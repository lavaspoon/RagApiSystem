package devlava.docai.repository;

import devlava.docai.entity.VectorStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VectorStoreRepository extends JpaRepository<VectorStore, Long> {

    // 문서별 벡터 삭제
    @Modifying
    @Query("DELETE FROM VectorStore v WHERE v.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    // 벡터 직접 삽입 (Native Query)
    @Modifying
    @Query(value = """
        INSERT INTO vector_stores (document_id, chunk_index, content, embedding, metadata, created_at, updated_at)
        VALUES (:documentId, :chunkIndex, :content, CAST(:embedding AS vector), CAST(:metadata AS jsonb), :createdAt, :updatedAt)
        """, nativeQuery = true)
    void insertVectorStore(
            @Param("documentId") Long documentId,
            @Param("chunkIndex") Integer chunkIndex,
            @Param("content") String content,
            @Param("embedding") String embedding,
            @Param("metadata") String metadata,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    // 카테고리별 벡터 유사도 검색
    @Query(value = """
        SELECT v.* FROM vector_stores v
        JOIN documents d ON v.document_id = d.id
        WHERE d.category_id = :categoryId
        ORDER BY v.embedding <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<VectorStore> findSimilarVectorsByCategory(
            @Param("queryVector") String queryVector,
            @Param("categoryId") Long categoryId,
            @Param("limit") int limit);

    /**
     * 특정 문서 내에서 유사도 검색
     */
    @Query(value = "SELECT v.* FROM vector_stores v " +
            "WHERE v.document_id = :documentId " +
            "ORDER BY v.embedding <-> CAST(:queryVector AS vector) " +
            "LIMIT :topK", nativeQuery = true)
    List<VectorStore> findSimilarVectorsByDocument(@Param("queryVector") String queryVector,
                                                   @Param("documentId") Long documentId,
                                                   @Param("topK") int topK);
}