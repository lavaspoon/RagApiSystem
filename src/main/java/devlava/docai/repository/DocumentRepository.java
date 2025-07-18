package devlava.docai.repository;

import devlava.docai.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByCategoryId(Long categoryId);
}