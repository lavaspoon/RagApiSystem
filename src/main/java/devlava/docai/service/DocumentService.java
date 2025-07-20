package devlava.docai.service;

import devlava.docai.entity.Category;
import devlava.docai.entity.Document;
import devlava.docai.repository.CategoryRepository;
import devlava.docai.repository.DocumentRepository;
import devlava.docai.repository.VectorStoreRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;
    private final VectorStoreRepository vectorStoreRepository;
    private final EmbeddingModel embeddingModel; // 직접 임베딩 모델 사용
    private final ObjectMapper objectMapper;
    private final Path fileStorageLocation;

    public DocumentService(DocumentRepository documentRepository,
                           CategoryRepository categoryRepository,
                           VectorStoreRepository vectorStoreRepository,
                           EmbeddingModel embeddingModel) {
        this.documentRepository = documentRepository;
        this.categoryRepository = categoryRepository;
        this.vectorStoreRepository = vectorStoreRepository;
        this.embeddingModel = embeddingModel;
        this.objectMapper = new ObjectMapper();
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

    // 단일 파일 업로드 (벡터 처리 포함)
    @Transactional
    public Document uploadDocument(Long categoryId, MultipartFile file) throws IOException {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + categoryId));

        Document document = saveFile(category, file);

        // 벡터 처리
        try {
            processDocumentToVector(document);
            log.info("Document vectorized successfully: {}", document.getFileName());
        } catch (Exception e) {
            log.error("Failed to vectorize document: {}", document.getFileName(), e);
            // 벡터 처리 실패해도 문서는 저장된 상태로 유지
        }

        return document;
    }

    // 멀티 파일 업로드 (벡터 처리 포함)
    @Transactional
    public List<Document> uploadMultipleDocuments(Long categoryId, MultipartFile[] files) throws IOException {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + categoryId));

        List<Document> documents = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        List<String> vectorFailedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                if (!file.isEmpty()) {
                    Document document = saveFile(category, file);
                    documents.add(document);

                    // 각 파일별로 벡터 처리
                    try {
                        processDocumentToVector(document);
                        log.info("Document vectorized successfully: {}", document.getFileName());
                    } catch (Exception e) {
                        vectorFailedFiles.add(file.getOriginalFilename());
                        log.error("Failed to vectorize document: {}", document.getFileName(), e);
                    }
                }
            } catch (IOException e) {
                failedFiles.add(file.getOriginalFilename());
                log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
            }
        }

        if (!failedFiles.isEmpty()) {
            throw new RuntimeException("일부 파일 업로드 실패: " + String.join(", ", failedFiles));
        }

        if (!vectorFailedFiles.isEmpty()) {
            log.warn("일부 파일 벡터화 실패 (파일은 저장됨): {}", String.join(", ", vectorFailedFiles));
        }

        return documents;
    }

    // 벡터 처리 메서드 - 직접 우리 DB에 저장
    private void processDocumentToVector(Document document) {
        try {
            log.info("Starting vector processing for document: {}", document.getFileName());

            // 1. 파일을 Resource로 변환
            Resource fileResource = loadFileAsResource(document.getFilePath());
            log.info("File resource loaded: {}", fileResource.exists());

            // 2. Tika로 문서 읽기
            DocumentReader documentReader = new TikaDocumentReader(fileResource);
            List<org.springframework.ai.document.Document> aiDocuments = documentReader.get();
            log.info("Tika extracted {} documents", aiDocuments.size());

            if (aiDocuments.isEmpty()) {
                log.warn("No content extracted from file: {}", document.getFileName());
                return;
            }

            // 첫 번째 문서의 내용 일부 로깅
            if (!aiDocuments.isEmpty()) {
                String content = aiDocuments.get(0).getContent();
                log.info("First document content preview ({}chars): {}",
                        content.length(),
                        content.substring(0, Math.min(200, content.length())));
            }

            // 3. 텍스트 분할 (청킹)
            TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 5, 10000, true);
            List<org.springframework.ai.document.Document> chunks = splitter.apply(aiDocuments);
            log.info("Text splitter created {} chunks", chunks.size());

            if (chunks.isEmpty()) {
                log.warn("No chunks created from documents");
                return;
            }

            // 4. 각 청크를 임베딩하고 DB에 저장
            int successCount = 0;
            for (int i = 0; i < chunks.size(); i++) {
                org.springframework.ai.document.Document chunk = chunks.get(i);

                try {
                    // 임베딩 생성
                    float[] embeddingArray = embeddingModel.embed(chunk.getContent());
                    List<Double> embedding = new ArrayList<>();
                    for (float f : embeddingArray) {
                        embedding.add((double) f);
                    }
                    log.debug("Generated embedding for chunk {}: {} dimensions", i, embedding.size());

                    // 메타데이터 준비
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("document_id", document.getId());
                    metadata.put("file_name", document.getFileName());
                    metadata.put("category_id", document.getCategory().getId());
                    metadata.put("category_name", document.getCategory().getName());
                    metadata.put("content_type", document.getContentType());
                    metadata.put("file_size", document.getFileSize());
                    metadata.put("upload_time", LocalDateTime.now().toString());
                    metadata.put("chunk_index", i);

                    // 개별 청크 저장
                    saveVectorChunk(
                            document.getId(),
                            i,
                            chunk.getContent(),
                            convertEmbeddingToString(embedding),
                            objectMapper.writeValueAsString(metadata)
                    );

                    successCount++;
                    log.debug("Saved chunk {} to database", i);

                } catch (Exception e) {
                    log.error("Failed to process chunk {}: {}", i, e.getMessage());
                }
            }

            log.info("Successfully processed {}/{} chunks for document: {}",
                    successCount, chunks.size(), document.getFileName());

        } catch (Exception e) {
            log.error("Error processing document to vector: {}", document.getFileName(), e);
            throw new RuntimeException("Failed to process document to vector store", e);
        }
    }

    // 개별 벡터 청크 저장 (별도 트랜잭션)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveVectorChunk(Long documentId, int chunkIndex, String content, String embedding, String metadata) {
        try {
            vectorStoreRepository.insertVectorStore(
                    documentId,
                    chunkIndex,
                    content,
                    embedding,
                    metadata,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Failed to save vector chunk {}: {}", chunkIndex, e.getMessage());
            throw e;
        }
    }

    // 임베딩을 PostgreSQL vector 타입 문자열로 변환
    private String convertEmbeddingToString(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
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

    @Transactional
    public void deleteDocument(Long id) {
        Document document = getDocument(id);

        // 1. 벡터 스토어에서 관련 데이터 삭제
        try {
            vectorStoreRepository.deleteByDocumentId(id);
            log.info("Deleted vectors for document: {}", document.getFileName());
        } catch (Exception e) {
            log.error("Failed to delete vectors for document: {}", document.getFileName(), e);
        }

        // 2. 실제 파일 삭제
        try {
            Files.deleteIfExists(Paths.get(document.getFilePath()));
        } catch (IOException e) {
            log.error("Failed to delete file: {}", document.getFilePath(), e);
        }

        // 3. DB에서 문서 정보 삭제
        documentRepository.deleteById(id);
    }

}