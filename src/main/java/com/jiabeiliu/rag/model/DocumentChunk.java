package com.jiabeiliu.rag.model;

import com.jiabeiliu.rag.config.PGvectorType;
import com.pgvector.PGvector;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;

/**
 * One chunk of an ingested document, with its embedding stored in a
 * pgvector {@code vector(768)} column (matches nomic-embed-text).
 */
@Entity
@Table(name = "document_chunk")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false)
    private String docId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Type(PGvectorType.class)
    @Column(columnDefinition = "vector(768)")
    private PGvector embedding;

    protected DocumentChunk() {
    }

    public DocumentChunk(String docId, int chunkIndex, String content, PGvector embedding) {
        this.docId = docId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
    }

    public Long getId() {
        return id;
    }

    public String getDocId() {
        return docId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public PGvector getEmbedding() {
        return embedding;
    }
}
