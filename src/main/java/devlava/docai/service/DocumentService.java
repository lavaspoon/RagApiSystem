package devlava.docai.service;

import devlava.docai.entity.Category;
import devlava.docai.entity.Document;
import devlava.docai.repository.CategoryRepository;
import devlava.docai.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;
    private final Path fileStorageLocation;

    public DocumentService(DocumentRepository documentRepository, CategoryRepository categoryRepository) {
        this.documentRepository = documentRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageLocation = Paths.get("uploads");

        try {
            Files.createDirectories(fileStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public List<Document> getDocumentsByCategory(Long categoryId) {
        return documentRepository.findByCategoryId(categoryId);
    }

    public Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found with id: " + id));
    }

    // 단일 파일 업로드 (기존)
    public Document uploadDocument(Long categoryId, MultipartFile file) throws IOException {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + categoryId));

        return saveFile(category, file);
    }

    // 멀티 파일 업로드 (새로 추가)
    @Transactional
    public List<Document> uploadMultipleDocuments(Long categoryId, MultipartFile[] files) throws IOException {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + categoryId));

        List<Document> documents = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                if (!file.isEmpty()) {
                    Document document = saveFile(category, file);
                    documents.add(document);
                }
            } catch (IOException e) {
                failedFiles.add(file.getOriginalFilename());
                // 로그 남기고 계속 진행
                System.err.println("Failed to upload file: " + file.getOriginalFilename() + " - " + e.getMessage());
            }
        }

        if (!failedFiles.isEmpty()) {
            throw new RuntimeException("일부 파일 업로드 실패: " + String.join(", ", failedFiles));
        }

        return documents;
    }

    // 파일 저장 공통 메서드
    private Document saveFile(Category category, MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uniqueFileName = timestamp + "_" + originalFileName;
        Path targetLocation = fileStorageLocation.resolve(uniqueFileName);

        // 파일 저장 (덮어쓰기 옵션 사용)
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        Document document = new Document();
        document.setCategory(category);
        document.setTitle(originalFileName); // 원본 파일명을 title로 설정
        document.setFileName(originalFileName);
        document.setFilePath(targetLocation.toString());
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());

        return documentRepository.save(document);
    }

    public Resource loadFileAsResource(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Resource resource = new UrlResource(path.toUri());

            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filePath);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found: " + filePath, ex);
        }
    }

    public void deleteDocument(Long id) {
        Document document = getDocument(id);

        // 실제 파일 삭제
        try {
            Files.deleteIfExists(Paths.get(document.getFilePath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + document.getFilePath(), e);
        }

        documentRepository.deleteById(id);
    }
}