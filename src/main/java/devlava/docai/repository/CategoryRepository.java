package devlava.docai.repository;

import devlava.docai.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("SELECT DISTINCT root FROM Category root " +
            "LEFT JOIN FETCH root.children c1 " +
            "LEFT JOIN FETCH c1.children c2 " +
            "LEFT JOIN FETCH c2.children c3 " +
            "WHERE root.parent IS NULL")
    Set<Category> findRootCategoriesWithChildren();

    @Query("SELECT DISTINCT c FROM Category c " +
            "LEFT JOIN FETCH c.children child " +
            "LEFT JOIN FETCH child.children " +
            "WHERE c.id = :id")
    Optional<Category> findByIdWithChildrenHierarchy(@Param("id") Long id);

    @Query(value = "SELECT DISTINCT c FROM Category c " +
            "LEFT JOIN FETCH c.children " +
            "WHERE c.id = :id")
    Optional<Category> findByIdWithChildren(@Param("id") Long id);

    @Query(value = "SELECT DISTINCT c FROM Category c " +
            "LEFT JOIN FETCH c.documents " +
            "WHERE c.id = :id")
    Optional<Category> findByIdWithDocuments(@Param("id") Long id);
}