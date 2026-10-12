package com.jiabeiliu.rag.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class ChatGuardTest {
 private final List<ScoredChunk> chunks=List.of(new ScoredChunk(1,"doc",0,"Synthetic evidence",0.1));
 @Test void unsupportedAdversarialOutputIsNotReturned() {assertFalse(ChatService.guardedAnswer("Invented trial: 90% success",chunks).contains("90%"));}
 @Test void fabricatedReferenceIsNotReturned() {assertFalse(ChatService.guardedAnswer("Trial [chunk:99]",chunks).contains("Trial"));}
 @Test void availableCitationIsKeptWithoutClaimingSemanticValidation() {assertEquals("Fact [chunk:1]",ChatService.guardedAnswer("Fact [chunk:1]",chunks));}
}
