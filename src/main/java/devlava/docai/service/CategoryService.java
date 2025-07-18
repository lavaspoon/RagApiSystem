package devlava.docai.service;

import devlava.docai.entity.Category;
import devlava.docai.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public Set<Category> getRootCategories() {
        return categoryRepository.findRootCategoriesWithChildren();
    }

    public Category getCategoryWithChildren(Long id) {
        return categoryRepository.findByIdWithChildrenHierarchy(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + id));
    }

    @Transactional
    public Category createCategory(String name, Long parentId) {
        Category category = new Category();
        category.setName(name);

        if (parentId != null) {
            Category parent = categoryRepository.findByIdWithChildren(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found with id: " + parentId));
            parent.addChild(category);
        }

        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(Long id, String name) {
        Category category = categoryRepository.findByIdWithChildren(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + id));
        category.setName(name);
        return category;
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new IllegalArgumentException("Category not found with id: " + id);
        }
        categoryRepository.deleteById(id);
    }
}