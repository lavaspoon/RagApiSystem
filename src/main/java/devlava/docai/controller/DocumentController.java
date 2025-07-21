package devlava.docai.controller;

import devlava.docai.dto.DocumentDto;
import devlava.docai.entity.Document;
import devlava.docai.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin("*")
@Slf4j
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

    // 멀티 파일 업로드 (기존)
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

    /**
     * 🔥 개선된 문서 다운로드 (한글 파일명 지원, 에러 처리 강화)
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        try {
            log.info("Download request for document ID: {}", id);

            // 1. 문서 정보 조회
            Document document = documentService.getDocument(id);

            // 2. 파일 리소스 로드
            Resource resource = documentService.loadFileAsResource(document.getFilePath());

            if (!resource.exists() || !resource.isReadable()) {
                log.error("File not found or not readable: {}", document.getFilePath());
                return ResponseEntity.notFound().build();
            }

            // 3. 한글 파일명 인코딩 처리
            String encodedFileName = encodeFileName(document.getFileName());

            // 4. Content-Type 결정
            String contentType = determineContentType(document.getContentType(), document.getFileName());

            log.info("Serving file: {} ({})", document.getFileName(), contentType);

            // 5. 응답 헤더 설정 (한글 파일명 완벽 지원)
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(document.getFileSize()))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading document ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 🆕 문서 정보 조회 (다운로드 전 정보 확인용)
     */
    @GetMapping("/info/{id}")
    public ResponseEntity<DocumentInfo> getDocumentInfo(@PathVariable Long id) {
        try {
            Document document = documentService.getDocument(id);

            DocumentInfo info = DocumentInfo.builder()
                    .id(document.getId())
                    .fileName(document.getFileName())
                    .contentType(document.getContentType())
                    .fileSize(document.getFileSize())
                    .downloadUrl("/api/documents/download/" + document.getId())
                    .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                    .build();

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            log.error("Error getting document info for ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 파일명 인코딩 (한글 지원)
     */
    private String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20"); // 공백을 %20으로 변환
        } catch (UnsupportedEncodingException e) {
            log.warn("Failed to encode filename: {}", fileName);
            return fileName;
        }
    }

    /**
     * Content-Type 결정
     */
    private String determineContentType(String storedContentType, String fileName) {
        // 저장된 Content-Type이 있으면 우선 사용
        if (storedContentType != null && !storedContentType.isEmpty() &&
                !storedContentType.equals("application/octet-stream")) {
            return storedContentType;
        }

        // 파일 확장자로 Content-Type 추정
        String lowerFileName = fileName.toLowerCase();

        if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFileName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerFileName.endsWith(".doc")) {
            return "application/msword";
        } else if (lowerFileName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lowerFileName.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (lowerFileName.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (lowerFileName.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (lowerFileName.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        } else if (lowerFileName.endsWith(".hwp")) {
            return "application/haansofthwp";
        } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerFileName.endsWith(".gif")) {
            return "image/gif";
        } else {
            // 기본값
            return "application/octet-stream";
        }
    }

    /**
     * 문서 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class DocumentInfo {
        private Long id;
        private String fileName;
        private String contentType;
        private Long fileSize;
        private String downloadUrl;
        private String categoryName;

        // 🆕 파일 크기를 사람이 읽기 쉬운 형태로 변환
        public String getFormattedFileSize() {
            if (fileSize == null) return "알 수 없음";

            if (fileSize < 1024) {
                return fileSize + " B";
            } else if (fileSize < 1024 * 1024) {
                return String.format("%.1f KB", fileSize / 1024.0);
            } else if (fileSize < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
            } else {
                return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
}