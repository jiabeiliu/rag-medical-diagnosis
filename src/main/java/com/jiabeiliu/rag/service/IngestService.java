package com.jiabeiliu.rag.service;

import com.jiabeiliu.rag.model.DocumentChunk;
import com.jiabeiliu.rag.repository.DocumentChunkRepository;
import com.pgvector.PGvector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ingest pipeline: chunk -&gt; embed (batched) -&gt; persist.
 */
@Service
public class IngestService {

    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository repository;

    public IngestService(ChunkingService chunkingService,
                         EmbeddingService embeddingService,
                         DocumentChunkRepository repository) {
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.repository = repository;
    }

    /**
     * Ingests one document. Returns the number of chunks stored.
     */
    @Transactional
    public int ingest(String docId, String text) {
        List<String> chunks = chunkingService.chunk(text);
        if (chunks.isEmpty()) {
            return 0;
        }
        List<float[]> embeddings = embeddingService.embedBatch(chunks);
        for (int i = 0; i < chunks.size(); i++) {
            repository.save(new DocumentChunk(docId, i, chunks.get(i),
                    new PGvector(embeddings.get(i))));
        }
        return chunks.size();
    }
}
