package com.jiabeiliu.rag.web;

import com.jiabeiliu.rag.service.ChatService;
import com.jiabeiliu.rag.service.IngestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RagController {

    private final IngestService ingestService;
    private final ChatService chatService;

    public RagController(IngestService ingestService, ChatService chatService) {
        this.ingestService = ingestService;
        this.chatService = chatService;
    }

    /** Ingest a document: chunk it, embed the chunks, store them with vectors. */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@Valid @RequestBody IngestRequest request) {
        int chunks = ingestService.ingest(request.docId(), request.text());
        return Map.of("docId", request.docId(), "chunks", chunks);
    }

    /** Ask a question: retrieve top-K chunks, then generate a grounded answer. */
    @PostMapping("/query")
    public QueryResponse query(@Valid @RequestBody QueryRequest request) {
        return chatService.answer(request.question(), request.effectiveTopK());
    }
}
