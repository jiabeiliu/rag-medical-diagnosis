package com.jiabeiliu.rag.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Request body for {@code POST /api/query}. */
public record QueryRequest(
        @NotBlank String question,
        @Positive Integer topK) {

    /** Defaults topK to 5 when omitted. */
    public int effectiveTopK() {
        return topK != null ? topK : 5;
    }
}
