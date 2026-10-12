package com.jiabeiliu.rag.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import com.jiabeiliu.rag.repository.DocumentChunkRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
class IngestServiceTest {
 @Test void failedEmbeddingNeverDeletesOldDocument() {
  var chunks=mock(ChunkingService.class); var embeddings=mock(EmbeddingService.class);
  var repo=mock(DocumentChunkRepository.class);
  when(chunks.chunk("new")).thenReturn(List.of("new"));
  when(embeddings.embedBatch(anyList())).thenThrow(new IllegalStateException("model down"));
  var service=new IngestService(chunks,embeddings,repo,mock(JdbcTemplate.class),mock(TransactionTemplate.class));
  assertThrows(IllegalStateException.class,()->service.ingest("doc","new")); verifyNoInteractions(repo);
 }
 @Test void replacementDeletesPriorChunksBeforePersisting() {
  var chunks=mock(ChunkingService.class); var embeddings=mock(EmbeddingService.class);
  var repo=mock(DocumentChunkRepository.class); var transaction=mock(TransactionTemplate.class);
  when(chunks.chunk("new")).thenReturn(List.of("new")); when(embeddings.embedBatch(anyList())).thenReturn(List.of(new float[]{1,2}));
  when(transaction.execute(any())).thenAnswer(call->((TransactionCallback<?>)call.getArgument(0)).doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
  var service=new IngestService(chunks,embeddings,repo,mock(JdbcTemplate.class),transaction);
  assertEquals(1,service.ingest("doc","new"));
  var order=inOrder(repo); order.verify(repo).deleteByDocId("doc"); order.verify(repo).flush(); order.verify(repo).save(any());
 }
}
