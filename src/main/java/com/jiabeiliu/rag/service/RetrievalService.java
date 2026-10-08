package com.jiabeiliu.rag.service;

import com.jiabeiliu.rag.repository.DocumentChunkRepository;
import com.pgvector.PGvector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Vector search over ingested chunks using pgvector cosine distance.
 */
@Service
public class RetrievalService {

    private final DocumentChunkRepository repository;

    public RetrievalService(DocumentChunkRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the top-K chunks most similar to the query embedding,
     * ordered by ascending cosine distance.
     */
    @Transactional(readOnly = true)
    public List<ScoredChunk> retrieve(float[] queryEmbedding, int topK) {
        String literal = new PGvector(queryEmbedding).getValue();
        List<Object[]> rows = repository.findNearest(literal, topK);
        List<ScoredChunk> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new ScoredChunk(
                    ((Number) r[0]).longValue(),
                    (String) r[1],
                    ((Number) r[2]).intValue(),
                    (String) r[3],
                    ((Number) r[4]).doubleValue()));
        }
        return out;
    }
}
