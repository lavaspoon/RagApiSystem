package devlava.docai.controller;

import devlava.docai.dto.CategoryDto;
import devlava.docai.entity.Category;
import devlava.docai.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin("*")
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<Set<CategoryDto>> getRootCategories() {
        Set<Category> categories = categoryService.getRootCategories();
        Set<CategoryDto> dtos = categories.stream()
                .map(CategoryDto::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getCategory(@PathVariable Long id) {
        Category category = categoryService.getCategoryWithChildren(id);
        return ResponseEntity.ok(CategoryDto.from(category));
    }

    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(
            @RequestParam String name,
            @RequestParam(required = false) Long parentId) {
        Category category = categoryService.createCategory(name, parentId);
        return ResponseEntity.ok(CategoryDto.from(category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable Long id,
            @RequestParam String name) {
        Category category = categoryService.updateCategory(id, name);
        return ResponseEntity.ok(CategoryDto.from(category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok().build();
    }
}