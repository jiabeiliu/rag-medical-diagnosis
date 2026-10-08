-- Runs before JPA startup (spring.sql.init.mode=always).
CREATE EXTENSION IF NOT EXISTS vector;

-- embedding is vector(768): nomic-embed-text dimension.
-- MUST match llm.embedding-dimensions in application.yml.
CREATE TABLE IF NOT EXISTS document_chunk (
    id          BIGSERIAL PRIMARY KEY,
    doc_id      VARCHAR(255) NOT NULL,
    chunk_index INT NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(768)
);

CREATE INDEX IF NOT EXISTS idx_document_chunk_doc_id ON document_chunk (doc_id);
