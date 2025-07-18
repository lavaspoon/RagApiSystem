package devlava.docai.repository;

import devlava.docai.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.document.id = :documentId ORDER BY dc.order")
    List<DocumentChunk> findByDocumentIdOrderByOrder(@Param("documentId") Long documentId);
}