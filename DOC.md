# 문서 저장 및 벡터화 시스템 가이드

## 개요
이 시스템은 다양한 형식의 문서를 업로드하고, Apache Tika를 통해 텍스트를 추출한 후, 벡터 임베딩으로 변환하여 검색 가능한 형태로 저장하는 완전한 문서 처리 파이프라인입니다.

## 전체 아키텍처

```
[파일 업로드] → [물리적 저장] → [메타데이터 저장] → [텍스트 추출] → [청킹] → [벡터화] → [벡터 저장]
```

## 문서 저장 프로세스

### 1. 파일 업로드 및 물리적 저장

#### 단일 파일 업로드 (`uploadDocument`)
```java
@Transactional
public Document uploadDocument(Long categoryId, MultipartFile file)
```

#### 다중 파일 업로드 (`uploadMultipleDocuments`)
```java
@Transactional
public List<Document> uploadMultipleDocuments(Long categoryId, MultipartFile[] files)
```

### 2. 물리적 파일 저장 과정

#### 파일명 생성 규칙
```java
String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
String uniqueFileName = timestamp + "_" + originalFileName;
```
- **형식**: `yyyyMMddHHmmss_원본파일명`
- **예시**: `20240115143025_프로젝트계획서.pdf`

#### 저장 위치
- **기본 경로**: `uploads/` 디렉토리
- **전체 경로**: `uploads/20240115143025_프로젝트계획서.pdf`

#### 파일 저장 옵션
```java
Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
```
- 동일한 파일명 존재 시 덮어쓰기
- 디렉토리 자동 생성

### 3. 메타데이터 저장

#### Document 엔티티 저장
```java
Document document = new Document();
document.setCategory(category);           // 카테고리 정보
document.setFileName(originalFileName);   // 원본 파일명
document.setFilePath(targetLocation.toString()); // 물리적 저장 경로
document.setContentType(file.getContentType()); // MIME 타입
document.setFileSize(file.getSize());    // 파일 크기
```

## 벡터화 프로세스

### 1. 텍스트 추출 (Apache Tika)

```java
Resource fileResource = loadFileAsResource(document.getFilePath());
DocumentReader documentReader = new TikaDocumentReader(fileResource);
List<org.springframework.ai.document.Document> aiDocuments = documentReader.get();
```

#### 지원 파일 형식
- **문서**: PDF, DOC, DOCX, TXT, RTF
- **스프레드시트**: XLS, XLSX, CSV
- **프레젠테이션**: PPT, PPTX
- **기타**: HTML, XML 등

### 2. 텍스트 청킹 (Text Chunking)

```java
TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 5, 10000, true);
List<org.springframework.ai.document.Document> chunks = splitter.apply(aiDocuments);
```

#### 청킹 파라미터
- **chunkSize**: 500 토큰 (한 청크의 최대 크기)
- **chunkOverlap**: 50 토큰 (청크 간 중복 영역)
- **minChunkSizeChars**: 5 문자 (최소 청크 크기)
- **maxNumChunks**: 10,000 개 (최대 청크 수)
- **keepSeparator**: true (구분자 유지)

#### 청킹 전략
- **토큰 기반**: 단어 경계를 고려한 분할
- **중복 처리**: 청크 간 연결성 유지
- **크기 제한**: 메모리 및 성능 최적화

### 3. 벡터 임베딩 생성

```java
float[] embeddingArray = embeddingModel.embed(chunk.getContent());
List<Double> embedding = new ArrayList<>();
for (float f : embeddingArray) {
    embedding.add((double) f);
}
```

#### 임베딩 과정
1. **텍스트 → 벡터**: 각 청크를 고차원 벡터로 변환
2. **타입 변환**: float[] → List<Double>
3. **문자열 변환**: PostgreSQL vector 타입용 `[1.2,3.4,5.6]` 형태

### 4. 벡터 메타데이터 구성

```java
Map<String, Object> metadata = new HashMap<>();
metadata.put("document_id", document.getId());
metadata.put("file_name", document.getFileName());
metadata.put("category_id", document.getCategory().getId());
metadata.put("category_name", document.getCategory().getName());
metadata.put("content_type", document.getContentType());
metadata.put("file_size", document.getFileSize());
metadata.put("upload_time", LocalDateTime.now().toString());
metadata.put("chunk_index", i);
```

## 데이터베이스 저장 구조

### 1. Document 테이블
```sql
documents {
    id: BIGINT (PK)
    category_id: BIGINT (FK)
    file_name: VARCHAR(255)     -- 원본 파일명
    file_path: VARCHAR(500)     -- 물리적 저장 경로
    content_type: VARCHAR(100)  -- MIME 타입
    file_size: BIGINT          -- 파일 크기 (bytes)
    created_at: TIMESTAMP
    updated_at: TIMESTAMP
}
```

### 2. VectorStore 테이블
```sql
vector_stores {
    id: BIGINT (PK)
    document_id: BIGINT (FK)
    chunk_index: INT           -- 청크 순서
    content: TEXT              -- 청크 내용
    embedding: VECTOR          -- 벡터 임베딩 (pgvector)
    metadata: JSONB            -- 메타데이터
    created_at: TIMESTAMP
    updated_at: TIMESTAMP
}
```

### 3. 벡터 저장 프로세스

#### 개별 청크 저장 (별도 트랜잭션)
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
protected void saveVectorChunk(Long documentId, int chunkIndex, String content, 
                              String embedding, String metadata)
```

#### 트랜잭션 분리 이유
- **독립적 처리**: 개별 청크 실패가 전체에 영향 안 줌
- **메모리 효율**: 대용량 문서 처리 시 메모리 사용량 최적화
- **에러 복구**: 부분적 실패 상황에서 복구 가능

## 에러 처리 및 예외 상황

### 1. 파일 업로드 실패
```java
List<String> failedFiles = new ArrayList<>();
// ... 파일별 처리
if (!failedFiles.isEmpty()) {
    throw new RuntimeException("일부 파일 업로드 실패: " + String.join(", ", failedFiles));
}
```

### 2. 벡터화 실패
```java
List<String> vectorFailedFiles = new ArrayList<>();
// ... 벡터 처리 시도
if (!vectorFailedFiles.isEmpty()) {
    log.warn("일부 파일 벡터화 실패 (파일은 저장됨): {}", String.join(", ", vectorFailedFiles));
}
```

### 3. 예외 처리 전략
- **파일 저장 우선**: 벡터화 실패해도 원본 파일은 보존
- **부분 성공 허용**: 일부 청크 실패 시에도 나머지는 처리 완료
- **상세 로깅**: 각 단계별 성공/실패 상태 기록

## 문서 삭제 프로세스

### 완전 삭제 (`deleteDocument`)
```java
@Transactional
public void deleteDocument(Long id)
```

#### 삭제 순서
1. **벡터 데이터 삭제**: `vectorStoreRepository.deleteByDocumentId(id)`
2. **물리적 파일 삭제**: `Files.deleteIfExists(Paths.get(document.getFilePath()))`
3. **메타데이터 삭제**: `documentRepository.deleteById(id)`

#### 안전장치
- **존재 확인**: 파일 존재 여부 확인 후 삭제
- **예외 처리**: 각 단계별 독립적 예외 처리
- **롤백 방지**: 부분 실패 시에도 가능한 부분까지는 정리

## 성능 최적화

### 1. 대용량 파일 처리
- **청킹**: 큰 문서를 작은 단위로 분할
- **스트리밍**: 메모리 효율적인 파일 처리
- **별도 트랜잭션**: 청크별 독립적 처리

### 2. 배치 처리
- **다중 파일**: 여러 파일 동시 처리
- **병렬 처리**: 각 파일별 독립적 벡터화
- **에러 격리**: 개별 파일 실패가 전체에 영향 없음

### 3. 메모리 관리
- **청크 크기 제한**: 500 토큰 단위로 분할
- **스트림 처리**: 대용량 파일의 순차적 처리
- **가비지 컬렉션**: 적절한 객체 생명주기 관리

## 모니터링 및 로깅

### 로그 레벨별 정보
- **INFO**: 주요 처리 단계 및 성공 건수
- **WARN**: 부분 실패 상황 및 복구 가능한 오류
- **ERROR**: 심각한 오류 및 처리 불가 상황
- **DEBUG**: 상세한 처리 과정 및 디버깅 정보

### 주요 모니터링 지표
- **파일 업로드 성공률**: 전체 대비 성공한 파일 비율
- **벡터화 성공률**: 업로드된 파일 중 벡터화 완료 비율
- **청크 처리 성능**: 문서당 평균 청크 수 및 처리 시간
- **저장 공간 사용량**: 물리적 파일 및 벡터 데이터 크기

## 사용 예시

### REST API 호출
```http
POST /api/documents/upload/{categoryId}
Content-Type: multipart/form-data

file: [파일 데이터]
```

### 다중 파일 업로드
```http
POST /api/documents/upload-multiple/{categoryId}
Content-Type: multipart/form-data

files[]: [파일1 데이터]
files[]: [파일2 데이터]
files[]: [파일3 데이터]
```

### 처리 결과 예시
```json
{
    "id": 123,
    "fileName": "프로젝트계획서.pdf",
    "filePath": "uploads/20240115143025_프로젝트계획서.pdf",
    "contentType": "application/pdf",
    "fileSize": 1048576,
    "category": {
        "id": 1,
        "name": "프로젝트 문서"
    },
    "vectorized": true,
    "chunkCount": 15
}
```

## 결론

이 문서 저장 시스템은 다음과 같은 특징을 가집니다:

1. **완전 자동화**: 파일 업로드부터 벡터화까지 완전 자동 처리
2. **확장성**: 대용량 파일 및 다중 파일 처리 지원
3. **안정성**: 부분 실패 상황에서도 안전한 데이터 보존
4. **검색 최적화**: 벡터 임베딩을 통한 의미론적 검색 지원
5. **모니터링**: 상세한 로깅 및 에러 추적 기능

이러한 특징들로 인해 기업 문서 관리 및 검색 시스템에 최적화된 솔루션을 제공합니다.