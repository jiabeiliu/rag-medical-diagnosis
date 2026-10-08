package com.jiabeiliu.rag.web;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/ingest}. */
public record IngestRequest(
        @NotBlank String docId,
        @NotBlank String text) {
}
