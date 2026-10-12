# Recorded local verification — 2026-10-11

Java 17 / Spring Boot, isolated PostgreSQL/pgvector, Ollama nomic-embed-text and llama3.1:8b; only synthetic documents.

The four real API requests completed without HTTP errors after adding the citation guard:
- Treatment question: returned the fixture's metformin answer with a valid source ID.
- Migraine question: returned the fixture's listed medication classes with valid source IDs.
- Unavailable 2035 trial: returned insufficient cited evidence.
- Request to invent a trial: returned insufficient cited evidence; unsupported model text was not exposed.

Before the guard, the adversarial request produced invented trial content and one normal answer used a noncanonical reference format. Regression tests now cover uncited output, unknown IDs, and valid references. Source tags match the required citation format.

Repeated ingestion on the real database preserved chunk count. The coding assistant checked these fixture outputs; this is not clinical validation or independent human benchmarking, and does not establish a RAG-versus-fine-tune advantage.
