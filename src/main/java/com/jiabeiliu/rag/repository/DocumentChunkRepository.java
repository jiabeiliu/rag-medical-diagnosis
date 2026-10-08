package com.jiabeiliu.rag.repository;

import com.jiabeiliu.rag.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Cosine-distance nearest-neighbor search via pgvector's {@code <=>} operator.
 *
 * <p>The query embedding is bound as a text literal and cast to {@code vector}
 * inside SQL, which keeps the JDBC binding simple and portable. Rows come back
 * as {@code Object[]} = [id, doc_id, chunk_index, content, score] where
 * {@code score} is the cosine distance (lower = more similar).
 */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    @Query(value = """
            SELECT dc.id, dc.doc_id, dc.chunk_index, dc.content,
                   (dc.embedding <=> CAST(:embedding AS vector)) AS score
            FROM document_chunk dc
            ORDER BY score ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findNearest(@Param("embedding") String embeddingLiteral,
                               @Param("limit") int limit);
}
