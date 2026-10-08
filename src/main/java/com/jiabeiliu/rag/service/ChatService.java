package com.jiabeiliu.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jiabeiliu.rag.web.QueryResponse;
import com.jiabeiliu.rag.web.SourceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retrieve-then-generate against an OpenAI-compatible
 * {@code POST /v1/chat/completions} endpoint.
 *
 * <p><b>Ollama-first (default, free, local):</b> {@code llm.base-url} defaults
 * to {@code http://localhost:11434/v1} with model {@code llama3.1:8b} — no API
 * key needed. <b>OpenAI profile (optional, paid):</b> set
 * {@code LLM_PROVIDER=openai}, {@code LLM_BASE_URL=https://api.openai.com/v1},
 * {@code OPENAI_API_KEY}, and {@code LLM_CHAT_MODEL=gpt-4o-mini}.
 *
 * <p>The model is instructed to answer <b>using only the retrieved context</b>
 * and to cite chunk ids, so answers stay grounded in the ingested documents.
 */
@Service
public class ChatService {

    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;
    private final RestClient restClient;
    private final String chatModel;

    public ChatService(EmbeddingService embeddingService,
                       RetrievalService retrievalService,
                       RestClient.Builder builder,
                       @Value("${llm.base-url:http://localhost:11434/v1}") String baseUrl,
                       @Value("${llm.api-key:}") String apiKey,
                       @Value("${llm.chat-model:llama3.1:8b}") String chatModel) {
        this.embeddingService = embeddingService;
        this.retrievalService = retrievalService;
        this.chatModel = chatModel;
        RestClient.Builder b = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        this.restClient = b.build();
    }

    public QueryResponse answer(String question, int topK) {
        float[] queryEmbedding = embeddingService.embed(question);
        List<ScoredChunk> chunks = retrievalService.retrieve(queryEmbedding, topK);

        StringBuilder context = new StringBuilder();
        List<SourceDto> sources = new ArrayList<>();
        for (ScoredChunk c : chunks) {
            context.append("[chunk ").append(c.chunkId())
                    .append(" | doc ").append(c.docId()).append("]\n")
                    .append(c.content()).append("\n\n");
            sources.add(new SourceDto(c.chunkId(), c.docId(), c.score()));
        }

        String system = "You are a medical-knowledge assistant. Answer the user's question "
                + "using ONLY the context chunks below. If the context does not contain the "
                + "answer, say so explicitly. Cite the chunks you used like [chunk:12].";
        String user = "Context:\n" + context + "\nQuestion: " + question;

        Map<String, Object> body = Map.of(
                "model", chatModel,
                "temperature", 0.0,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));

        JsonNode root = restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String answer = root.path("choices").path(0).path("message").path("content").asText("");
        return new QueryResponse(answer.strip(), sources);
    }
}
