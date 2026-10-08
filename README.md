# RAG Medical Diagnosis — Java/Spring Boot RAG Microservice

A minimal, end-to-end **Retrieval-Augmented Generation** microservice in Java 17 +
Spring Boot 3.2, backed by **pgvector** on PostgreSQL 16. Ingest medical knowledge
documents, retrieve the most relevant chunks by cosine similarity, and generate
grounded answers with citations — all in a Java stack.

**Runs 100% free locally** via [Ollama](https://ollama.com): `nomic-embed-text`
for embeddings (768-dim) and `llama3.1:8b` for generation. An optional paid
OpenAI profile is one env var away.

This project is the **RAG counterpart** to a fine-tuning experiment
([finetune-medical-diagnosis](https://github.com/jiabeiliu/finetune-medical-diagnosis)):
the same medical-diagnosis QA task, solved without touching model weights.

## Architecture

```
POST /api/ingest ──▶ ChunkingService ──▶ EmbeddingService ──▶ PostgreSQL + pgvector
   {docId, text}      (1500 chars,        (nomic-embed-text,
                       200 overlap)        768-dim, batched)   document_chunk(embedding vector(768))

POST /api/query ──▶ RetrievalService ──▶ ChatService ──▶ {answer, sources[]}
   {question, topK}   (pgvector <=>        (llama3.1:8b, answers
                      cosine distance)     ONLY from context)
```

Both LLM calls go through an **OpenAI-compatible HTTP API**
(`POST /v1/embeddings`, `POST /v1/chat/completions`) at a configurable base URL
(`LLM_BASE_URL`, default `http://localhost:11434/v1`). Ollama mirrors these
endpoints, so switching providers changes config, not code.

## Fine-tune vs RAG — the comparison story

On the same 20-case medical-diagnosis task, the fine-tuned baseline
(`gpt-4o-mini` via OpenAI's fine-tuning API, two hyperparameter configs):

| | Correct / 20 | Accuracy |
|---|---|---|
| Before fine-tuning | 11 | **55%** |
| After fine-tuning | 1 | **5%** — mode collapse: the model repeated 3 canned diagnoses |

Fine-tuning on a narrow distribution destroyed the base model's calibration.
RAG takes the opposite approach: **freeze the model, fix the knowledge**.
This service will be evaluated on the same 20 cases via `eval/` (hit@k + MRR
for retrieval; answer-correctness hook ready to wire up).

## Free local setup

No API keys, no bills — everything runs on your machine.

```bash
# 1. Install Ollama: https://ollama.com/download (or: brew install ollama)

# 2. Pull the models (one-time download)
ollama pull nomic-embed-text
ollama pull llama3.1:8b

# 3. Start PostgreSQL with pgvector
docker compose up -d

# 4. Run (schema.sql creates the vector extension + table automatically)
mvn spring-boot:run
```

That's it — `llm.base-url` defaults to `http://localhost:11434/v1`,
`llm.embedding-model` to `nomic-embed-text`, `llm.chat-model` to `llama3.1:8b`.

## API examples

```bash
# Ingest a document
curl -s -X POST localhost:8080/api/ingest \
  -H 'Content-Type: application/json' \
  -d '{"docId":"diabetes-guide","text":"Metformin is the first-line treatment for type 2 diabetes..."}'
# → {"docId":"diabetes-guide","chunks":1}

# Ask a question
curl -s -X POST localhost:8080/api/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the first-line treatment for type 2 diabetes?","topK":3}'
# → {"answer":"...","sources":[{"chunkId":1,"docId":"diabetes-guide","score":0.21}, ...]}
```

## Evaluation

```bash
# Requires: docker compose up -d, Ollama running with both models pulled
mvn spring-boot:run -Dspring-boot.run.arguments="--eval.enabled=true --eval.top-k=5"
```

What happens: the runner ingests the two **SYNTHETIC** sample docs from
`eval/synthetic_docs.csv`, embeds each question in `eval/eval_cases.csv`,
retrieves top-K chunks, and prints **hit@k** and **MRR** per case plus
aggregates. Add your own rows (columns: `case_no,question,expected_diagnosis,
gold_chunk_doc_ids` with pipe-separated doc ids) to evaluate on real
(de-identified) data — e.g. the same 20 cases used for the fine-tuning
baseline. Answer-level scoring is a stub hook
(`EvalRunner#answerCorrectnessHook`) ready for an LLM judge.

## Optional: OpenAI profile (paid)

```bash
export LLM_PROVIDER=openai
export LLM_BASE_URL=https://api.openai.com/v1
export OPENAI_API_KEY=<redacted>
export LLM_EMBEDDING_MODEL=text-embedding-3-small
export LLM_CHAT_MODEL=gpt-4o-mini
# NOTE: text-embedding-3-small is 1536-dim -> also set
# llm.embedding-dimensions=1536 and use vector(1536) in schema.sql
mvn spring-boot:run
```

The API key is **never hardcoded** — it comes only from the `OPENAI_API_KEY`
environment variable, and the `Authorization` header is omitted entirely when
it's blank (Ollama needs no key).

## Tech notes

- **pgvector + Hibernate**: pgvector-java 0.1.1 ships no Hibernate type, so
  `config/PGvectorType.java` is a minimal Hibernate 6 `UserType` that binds the
  vector as its text literal (`Types.OTHER`); reads parse it back.
- **Schema**: `schema.sql` runs before JPA startup (`spring.sql.init.mode=always`)
  — `CREATE EXTENSION IF NOT EXISTS vector` + table DDL. No Flyway, no
  `ddl-auto` surprises.
- **Embeddings**: `nomic-embed-text` → 768-dim, batched 100/request; the service
  validates the returned dimension against `llm.embedding-dimensions` and fails
  fast on mismatch.
- **Chunking**: fixed 1500-char windows with 200-char overlap (documented as
  intentionally naive in `ChunkingService`).

## Limitations (honest)

- Fixed-size chunking can split sentences; a production version would use
  sentence/token-aware chunking.
- No hybrid search (BM25) or reranking — pure dense retrieval.
- Eval currently scores retrieval only; answer correctness is a stub.
- Local 8b generation is weaker than frontier APIs — the tradeoff for free.
