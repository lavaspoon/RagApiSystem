package devlava.docai.controller;

import devlava.docai.dto.DocumentDto;
import devlava.docai.entity.Document;
import devlava.docai.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin("*")
public class DocumentController {
    private final DocumentService documentService;

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<DocumentDto>> getDocumentsByCategory(@PathVariable Long categoryId) {
        List<DocumentDto> documents = documentService.getDocumentsByCategory(categoryId)
                .stream()
                .map(DocumentDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(documents);
    }

    // 단일 파일 업로드 (기존)
    @PostMapping("/upload")
    public ResponseEntity<DocumentDto> uploadDocument(
            @RequestParam("categoryId") Long categoryId,
            @RequestParam("file") MultipartFile file) throws IOException {
        Document document = documentService.uploadDocument(categoryId, file);
        return ResponseEntity.ok(DocumentDto.from(document));
    }

    // 멀티 파일 업로드 (새로 추가)
    @PostMapping("/upload-multiple")
    public ResponseEntity<List<DocumentDto>> uploadMultipleDocuments(
            @RequestParam("categoryId") Long categoryId,
            @RequestParam("files") MultipartFile[] files) throws IOException {
        List<Document> documents = documentService.uploadMultipleDocuments(categoryId, files);
        List<DocumentDto> documentDtos = documents.stream()
                .map(DocumentDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(documentDtos);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        Document document = documentService.getDocument(id);
        Resource resource = documentService.loadFileAsResource(document.getFilePath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok().build();
    }
}