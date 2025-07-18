package devlava.docai.dto;

import devlava.docai.entity.Document;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentDto {
    private Long id;
    private String title;
    private String fileName;
    private String contentType;
    private long fileSize;
    private Long categoryId;

    public static DocumentDto from(Document document) {
        DocumentDto dto = new DocumentDto();
        dto.setId(document.getId());
        dto.setTitle(document.getTitle());
        dto.setFileName(document.getFileName());
        dto.setContentType(document.getContentType());
        dto.setFileSize(document.getFileSize());
        dto.setCategoryId(document.getCategory().getId());
        return dto;
    }
}