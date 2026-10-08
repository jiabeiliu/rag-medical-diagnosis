package com.jiabeiliu.rag.web;

import java.util.List;

/** Response body for {@code POST /api/query}. */
public record QueryResponse(
        String answer,
        List<SourceDto> sources) {
}
