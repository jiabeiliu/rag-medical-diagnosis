package com.jiabeiliu.rag.service;
import com.jiabeiliu.rag.model.DocumentChunk;
import com.jiabeiliu.rag.repository.DocumentChunkRepository;
import com.pgvector.PGvector;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
@Service
public class IngestService {
    private final ChunkingService chunking;
    private final EmbeddingService embeddings;
    private final DocumentChunkRepository repository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public IngestService(ChunkingService c, EmbeddingService e, DocumentChunkRepository r,
                         JdbcTemplate j, TransactionTemplate t) {
        chunking=c; embeddings=e; repository=r; jdbc=j; transaction=t;
    }
    public int ingest(String docId, String text) {
        List<String> chunks=chunking.chunk(text);
        if (chunks.isEmpty()) throw new IllegalArgumentException("Document must not be empty");
        // Network calls happen before the short replacement transaction. Failure preserves old data.
        List<float[]> vectors=embeddings.embedBatch(chunks);
        if (vectors.size()!=chunks.size()) throw new IllegalStateException("Embedding count mismatch");
        return transaction.execute(status -> {
            lock(docId);
            repository.deleteByDocId(docId);
            repository.flush();
            for(int i=0;i<chunks.size();i++)
                repository.save(new DocumentChunk(docId,i,chunks.get(i),new PGvector(vectors.get(i))));
            return chunks.size();
        });
    }
    public long delete(String docId) {
        return transaction.execute(status -> { lock(docId); return repository.deleteByDocId(docId); });
    }
    private void lock(String docId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, docId);
    }
}
