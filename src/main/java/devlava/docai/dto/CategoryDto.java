package devlava.docai.dto;

import devlava.docai.entity.Category;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
public class CategoryDto {
    private Long id;
    private String name;
    private Set<CategoryDto> children;

    public static CategoryDto from(Category category) {
        return from(category, 0);
    }

    private static CategoryDto from(Category category, int depth) {
        if (category == null || depth > 3) { // 최대 3단계까지만 변환
            return null;
        }

        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());

        if (category.getChildren() != null) {
            dto.setChildren(category.getChildren().stream()
                    .map(child -> from(child, depth + 1))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        return dto;
    }
}