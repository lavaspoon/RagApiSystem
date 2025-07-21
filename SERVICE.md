# 문서 검색 및 답변 시스템 동작 가이드

## 개요
이 시스템은 벡터 임베딩을 활용하여 문서 내에서 사용자의 질문과 관련된 정보를 찾고, AI 모델을 통해 자연어로 답변을 생성하는 RAG(Retrieval-Augmented Generation) 시스템입니다.

## 시스템 구성 요소

### 핵심 컴포넌트
- **VectorStoreRepository**: 임베딩된 문서 청크들을 저장하고 검색
- **EmbeddingModel**: 텍스트를 벡터로 변환
- **OllamaChatModel**: 자연어 답변 생성
- **DocumentService**: 문서 메타데이터 관리

## 답변 생성 프로세스

### 1. 카테고리 기반 검색 (`answerQuestionInCategory`)

```
질문 입력 → 임베딩 변환 → 유사 청크 검색 → 컨텍스트 구성 → AI 답변 생성
```

#### 세부 단계:
1. **질문 임베딩**: 사용자 질문을 벡터로 변환
2. **유사도 검색**: 카테고리 내 문서에서 가장 유사한 topK개 청크 검색
3. **컨텍스트 구성**: 검색된 청크들을 하나의 텍스트로 결합
4. **프롬프트 생성**: 구조화된 프롬프트 템플릿 사용
5. **AI 답변**: LLM이 컨텍스트 기반으로 답변 생성

#### 프롬프트 템플릿:
```
다음 문서 내용을 바탕으로 질문에 답변해주세요.

질문: {사용자_질문}

관련 내용:
{검색된_컨텍스트}

답변 지침:
- 제공된 정보만을 기반으로 답변하세요
- 간결하고 명확하게 답변하세요
- 문서에 없는 정보는 추측하지 마세요

답변:
```

### 2. 문서 기반 검색 (`answerQuestionInDocument`)

특정 문서에 대해서만 검색하는 방식으로, 프로세스는 카테고리 검색과 유사하지만 검색 범위가 해당 문서로 제한됩니다.

#### 프롬프트 템플릿:
```
'{문서명}' 문서의 내용을 바탕으로 질문에 답변해주세요.

질문: {사용자_질문}

문서 내용:
{검색된_컨텍스트}

답변 지침:
- 해당 문서의 내용만을 기반으로 답변하세요
- 간결하고 명확하게 답변하세요
- 문서에 없는 정보는 추측하지 마세요

답변:
```

## 검색 알고리즘

### 유사도 기반 검색
1. **임베딩 변환**: 질문을 float 배열로 변환 후 Double 리스트로 변환
2. **벡터 검색**: PostgreSQL pgvector를 활용한 코사인 유사도 검색
3. **상위 결과**: 유사도 순으로 정렬된 topK개 청크 반환

### 검색 결과 구조
```java
Map<String, Object> {
    "content": "청크 내용",
    "metadata": "메타데이터",
    "document_id": "문서 ID",
    "file_name": "파일명",
    "chunk_index": "청크 인덱스"
}
```

## 답변 응답 구조

### SearchResponse 객체
- **query**: 사용자 질문
- **answer**: AI 생성 답변
- **documentName**: 주요 참조 문서명
- **confidence**: 신뢰도 점수 (0-100)
- **downloadUrl**: 문서 다운로드 URL

### 주요 참조 문서 선택 로직
1. **단일 문서**: 해당 문서명 반환
2. **다중 문서**: AI가 질문과 가장 관련성 높은 문서 선택
3. **AI 선택 실패**: 가장 높은 유사도를 가진 첫 번째 청크의 문서 사용

## 스트리밍 답변

### 실시간 응답 (`answerQuestionInCategoryStream`, `answerQuestionInDocumentStream`)
- **Reactive Streams**: Spring WebFlux를 활용한 비동기 스트리밍
- **실시간 전송**: 답변을 토큰 단위로 실시간 전송
- **에러 처리**: 스트림 중 오류 발생 시 에러 메시지 반환

```java
public Flux<String> answerQuestionInCategoryStream(String query, Long categoryId, int topK) {
    return Mono.fromCallable(() -> buildPrompt())
             .flatMapMany(prompt -> chatModel.stream(prompt))
             .onErrorResume(e -> Flux.just("오류 메시지"));
}
```

## 신뢰도 계산

### 단순 신뢰도 알고리즘
```java
private int calculateSimpleConfidence(List<Map<String, Object>> chunks, String answer) {
    // 청크 점수: 청크 개수 기반 (최대 1.0)
    double chunkScore = Math.min(chunks.size() / 3.0, 1.0);
    
    // 답변 점수: 답변 길이 기반 (최대 1.0)
    double answerScore = Math.min(answer.length() / 50.0, 1.0);
    
    // 0-100 점수로 변환
    return (int) Math.round((chunkScore + answerScore) / 2.0 * 100);
}
```

## 에러 처리

### 주요 에러 상황
1. **검색 결과 없음**: "관련된 정보를 찾을 수 없습니다" 메시지
2. **문서 없음**: DocumentService에서 예외 발생
3. **AI 모델 오류**: "답변 생성 중 오류가 발생했습니다" 메시지
4. **임베딩 오류**: 빈 검색 결과 반환

### 예외 처리 전략
- **로깅**: 모든 에러를 로그로 기록
- **Graceful Degradation**: 에러 발생 시 사용자 친화적 메시지 반환
- **스트림 에러**: `onErrorResume`을 통한 에러 스트림 처리

## 성능 최적화

### 검색 최적화
- **TopK 제한**: 검색 결과를 상위 K개로 제한하여 성능 향상
- **벡터 인덱싱**: PostgreSQL pgvector 인덱스 활용
- **청크 크기**: 적절한 청크 크기로 검색 정확도와 성능 균형

### 메모리 최적화
- **스트림 처리**: 대용량 응답을 스트림으로 처리
- **지연 로딩**: 필요한 시점에만 문서 로드
- **결과 제한**: TopK 파라미터로 메모리 사용량 제한

## 사용 예시

### REST API 호출
```http
POST /api/search/category/{categoryId}
{
    "query": "프로젝트 일정은 어떻게 되나요?",
    "topK": 5
}
```

### 응답 예시
```json
{
    "query": "프로젝트 일정은 어떻게 되나요?",
    "answer": "프로젝트는 3단계로 구성되며, 1단계는 2월까지, 2단계는 4월까지 완료 예정입니다.",
    "documentName": "프로젝트_계획서.pdf",
    "confidence": 85,
    "downloadUrl": "http://localhost:8050/api/documents/download/123"
}
```

## 결론

이 시스템은 RAG 아키텍처를 통해 문서 기반의 정확한 답변을 제공하며, 스트리밍을 통한 실시간 응답과 신뢰도 점수를 통해 사용자 경험을 향상시킵니다. 벡터 검색과 AI 생성을 결합하여 기존 문서의 정보를 효과적으로 활용할 수 있습니다.