package com.jiabeiliu.rag.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-size character chunking with overlap.
 *
 * <p>Strategy: slice the text into ~1500-char windows advancing by
 * (1500 - 200) chars each step, so consecutive chunks share a 200-char
 * overlap. This is deliberately naive — it can split mid-word or
 * mid-sentence — but it is deterministic, language-agnostic, and easy to
 * reason about. A production upgrade would be sentence-aware or
 * token-aware chunking (e.g. tiktoken-based with the embedding model's
 * tokenizer).
 */
@Service
public class ChunkingService {

    /** Target chunk size in characters. */
    public static final int CHUNK_SIZE = 1500;
    /** Overlap between consecutive chunks in characters. */
    public static final int OVERLAP = 200;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.strip();
        int step = CHUNK_SIZE - OVERLAP;
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
        }
        return chunks;
    }
}
