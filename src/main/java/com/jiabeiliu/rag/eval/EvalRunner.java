package com.jiabeiliu.rag.eval;

import com.jiabeiliu.rag.service.EmbeddingService;
import com.jiabeiliu.rag.service.IngestService;
import com.jiabeiliu.rag.service.RetrievalService;
import com.jiabeiliu.rag.service.ScoredChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Retrieval evaluation harness. Enabled with {@code --eval.enabled=true}.
 *
 * <p>Reads {@code eval/eval_cases.csv} (columns: case_no, question,
 * expected_diagnosis, gold_chunk_doc_ids — pipe-separated) and, for each case,
 * embeds the question, retrieves top-K chunks, and reports:
 * <ul>
 *   <li><b>hit@k</b>: fraction of cases where at least one retrieved chunk
 *       comes from a gold document.</li>
 *   <li><b>MRR</b> (mean reciprocal rank): mean of 1/rank of the first
 *       gold-document hit (0 when there is no hit).</li>
 * </ul>
 * With {@code eval.ingest-synthetic=true} (default) the two SYNTHETIC sample
 * documents from {@code eval/synthetic_docs.csv} are ingested first, so the
 * eval runs end-to-end without any real data.
 *
 * <p>Answer correctness is intentionally a stub: wire an LLM judge or a
 * keyword check into {@link #answerCorrectnessHook} when you want
 * end-to-end answer scoring on top of retrieval metrics.
 */
@Component
@ConditionalOnProperty(name = "eval.enabled", havingValue = "true")
public class EvalRunner implements CommandLineRunner {

    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;
    private final IngestService ingestService;
    private final int topK;
    private final boolean ingestSynthetic;

    public EvalRunner(EmbeddingService embeddingService,
                      RetrievalService retrievalService,
                      IngestService ingestService,
                      @Value("${eval.top-k:5}") int topK,
                      @Value("${eval.ingest-synthetic:true}") boolean ingestSynthetic) {
        this.embeddingService = embeddingService;
        this.retrievalService = retrievalService;
        this.ingestService = ingestService;
        this.topK = topK;
        this.ingestSynthetic = ingestSynthetic;
    }

    @Override
    public void run(String... args) throws Exception {
        if (ingestSynthetic) {
            ingestSyntheticDocs();
        }
        List<EvalCase> cases = loadCases();
        if (cases.isEmpty()) {
            System.out.println("[eval] no cases found in eval/eval_cases.csv");
            return;
        }

        int hits = 0;
        double mrrSum = 0.0;
        System.out.printf("[eval] running %d cases, topK=%d%n", cases.size(), topK);
        for (EvalCase c : cases) {
            float[] q = embeddingService.embed(c.question());
            List<ScoredChunk> retrieved = retrievalService.retrieve(q, topK);
            int rank = firstHitRank(retrieved, c.goldDocIds());
            boolean hit = rank > 0;
            if (hit) {
                hits++;
                mrrSum += 1.0 / rank;
            }
            System.out.printf("[eval] case=%s hit@%d=%s mrr=%.3f expected=%s%n",
                    c.caseNo(), topK, hit, hit ? 1.0 / rank : 0.0, c.expectedDiagnosis());
        }
        System.out.printf("[eval] RESULT hit@%d=%.3f (%d/%d)  MRR=%.3f%n",
                topK, (double) hits / cases.size(), hits, cases.size(), mrrSum / cases.size());
        System.out.println("[eval] note: answer-correctness scoring is a stub — "
                + "see EvalRunner#answerCorrectnessHook");
    }

    private int firstHitRank(List<ScoredChunk> retrieved, Set<String> goldDocIds) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (goldDocIds.contains(retrieved.get(i).docId())) {
                return i + 1; // 1-based rank
            }
        }
        return 0;
    }

    /**
     * Stub hook for answer-level correctness (e.g. LLM-as-judge comparing the
     * generated answer against {@code expectedDiagnosis}). Returns empty until
     * implemented.
     */
    protected OptionalDouble answerCorrectnessHook(String question, String expectedDiagnosis,
                                                   String generatedAnswer) {
        // TODO: implement, e.g. call the chat model with a grading prompt.
        return OptionalDouble.empty();
    }

    private void ingestSyntheticDocs() throws IOException {
        String text = readEvalFile("eval/synthetic_docs.csv");
        List<String[]> rows = parseCsv(text);
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (r.length >= 2) {
                int n = ingestService.ingest(r[0].strip(), r[1].strip());
                System.out.printf("[eval] ingested synthetic doc %s (%d chunks)%n", r[0].strip(), n);
            }
        }
    }

    private List<EvalCase> loadCases() throws IOException {
        List<EvalCase> out = new ArrayList<>();
        List<String[]> rows = parseCsv(readEvalFile("eval/eval_cases.csv"));
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (r.length < 4 || r[0].isBlank()) {
                continue;
            }
            Set<String> gold = new HashSet<>(Arrays.asList(r[3].split("\\|")));
            gold.removeIf(String::isBlank);
            out.add(new EvalCase(r[0].strip(), r[1].strip(), r[2].strip(), gold));
        }
        return out;
    }

    /** Reads from the working dir first, then falls back to the classpath. */
    private String readEvalFile(String relativePath) throws IOException {
        Path p = Path.of(relativePath);
        if (Files.exists(p)) {
            return Files.readString(p, StandardCharsets.UTF_8);
        }
        ClassPathResource cpr = new ClassPathResource(relativePath);
        if (cpr.exists()) {
            return new String(cpr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        throw new IOException("eval file not found: " + relativePath
                + " (run from the project root, or put it on the classpath)");
    }

    /** Minimal CSV parser supporting quoted fields that may contain commas/newlines. */
    static List<String[]> parseCsv(String text) {
        List<String[]> rows = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                cur.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                cur.add(field.toString());
                field.setLength(0);
                rows.add(cur.toArray(new String[0]));
                cur = new ArrayList<>();
            } else if (ch != '\r') {
                field.append(ch);
            }
        }
        if (!field.isEmpty() || !cur.isEmpty()) {
            cur.add(field.toString());
            rows.add(cur.toArray(new String[0]));
        }
        return rows;
    }

    private record EvalCase(String caseNo, String question, String expectedDiagnosis,
                            Set<String> goldDocIds) {
    }
}
