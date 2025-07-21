package devlava.docai.service;

import devlava.docai.dto.SearchResponse;
import devlava.docai.dto.SourceInfo;
import devlava.docai.entity.Document;
import devlava.docai.entity.VectorStore;
import devlava.docai.repository.VectorStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final VectorStoreRepository vectorStoreRepository;
    private final EmbeddingModel embeddingModel;
    private final OllamaChatModel chatModel;
    private final DocumentService documentService;

    /**
     * 카테고리 내 문서들에서 질문에 대한 답변 생성 (Stream)
     */
    public Flux<String> answerQuestionInCategoryStream(String query, Long categoryId, int topK) {
        return Mono.fromCallable(() -> {
                    log.info("Answering question in category {}: {}", categoryId, query);

                    // 1. 유사한 청크들 검색
                    List<Map<String, Object>> similarChunks = searchSimilarChunksInCategory(query, categoryId, topK);

                    if (similarChunks.isEmpty()) {
                        return "죄송합니다. 해당 카테고리에서 관련된 정보를 찾을 수 없습니다.";
                    }

                    // 2. 컨텍스트 구성 (간단하게)
                    String context = buildSimpleContext(similarChunks);

                    // 3. 프롬프트 생성
                    return String.format("""
                        다음 문서 내용을 바탕으로 질문에 답변해주세요.
                        
                        질문: %s
                        
                        관련 내용:
                        %s
                        
                        답변 지침:
                        - 제공된 정보만을 기반으로 답변하세요
                        - 간결하고 명확하게 답변하세요
                        - 문서에 없는 정보는 추측하지 마세요
                        
                        답변:
                        """, query, context);
                })
                .flatMapMany(prompt -> generateAnswerStream(prompt))
                .onErrorResume(e -> {
                    log.error("Error answering question in category", e);
                    return Flux.just("답변 생성 중 오류가 발생했습니다.");
                });
    }

    /**
     * 특정 문서에서 질문에 대한 답변 생성 (Stream)
     */
    public Flux<String> answerQuestionInDocumentStream(String query, Long documentId, int topK) {
        return Mono.fromCallable(() -> {
                    log.info("Answering question in document {}: {}", documentId, query);

                    // 문서 존재 확인
                    Document document = documentService.getDocument(documentId);

                    // 1. 유사한 청크들 검색
                    List<Map<String, Object>> similarChunks = searchSimilarChunksInDocument(query, documentId, topK);

                    if (similarChunks.isEmpty()) {
                        return "죄송합니다. 해당 문서에서 관련된 정보를 찾을 수 없습니다.";
                    }

                    // 2. 컨텍스트 구성 (간단하게)
                    String context = buildSimpleContext(similarChunks);

                    // 3. 프롬프트 생성 (문서 특화)
                    return String.format("""
                        '%s' 문서의 내용을 바탕으로 질문에 답변해주세요.
                        
                        질문: %s
                        
                        문서 내용:
                        %s
                        
                        답변 지침:
                        - 해당 문서의 내용만을 기반으로 답변하세요
                        - 간결하고 명확하게 답변하세요
                        - 문서에 없는 정보는 추측하지 마세요
                        
                        답변:
                        """, document.getFileName(), query, context);
                })
                .flatMapMany(prompt -> generateAnswerStream(prompt))
                .onErrorResume(e -> {
                    log.error("Error answering question in document", e);
                    return Flux.just("답변 생성 중 오류가 발생했습니다.");
                });
    }

    /**
     * Stream 방식으로 답변 생성
     */
    private Flux<String> generateAnswerStream(String prompt) {
        try {
            return chatModel.stream(prompt);
        } catch (Exception e) {
            log.error("Error generating stream answer", e);
            return Flux.just("스트림 답변 생성 중 오류가 발생했습니다.");
        }
    }

    // 간단한 응답 형태로 수정된 메서드들
    public SearchResponse answerQuestionInCategory(String query, Long categoryId, int topK) {
        try {
            log.info("Answering question in category {}: {}", categoryId, query);

            List<Map<String, Object>> similarChunks = searchSimilarChunksInCategory(query, categoryId, topK);

            if (similarChunks.isEmpty()) {
                return SearchResponse.builder()
                        .query(query)
                        .answer("죄송합니다. 해당 카테고리에서 관련된 정보를 찾을 수 없습니다.")
                        .documentName("정보 없음")
                        .confidence(0)
                        .downloadUrl(null)
                        .build();
            }

            String context = buildSimpleContext(similarChunks);
            String answer = generateSimpleAnswer(query, context);
            String documentName = getBestMatchingDocumentName(similarChunks, query);
            int confidence = calculateSimpleConfidence(similarChunks, answer);

            // 🆕 주요 참조 문서 정보 추가
            Document mainDocument = getMainDocument(similarChunks, query);

            // 로그 추가 - 디버깅용
            log.info("Found {} chunks from documents: {}",
                    similarChunks.size(),
                    similarChunks.stream()
                            .map(chunk -> (String) chunk.get("file_name"))
                            .distinct()
                            .collect(Collectors.joining(", ")));
            log.info("Selected main document: {}", documentName);

            return SearchResponse.builder()
                    .query(query)
                    .answer(answer)
                    .documentName(documentName)
                    .confidence(confidence)
                    .downloadUrl(mainDocument != null ? "http://localhost:8050/api/documents/download/" + mainDocument.getId() : null)
                    .build();

        } catch (Exception e) {
            log.error("Error answering question in category", e);
            return SearchResponse.builder()
                    .query(query)
                    .answer("답변 생성 중 오류가 발생했습니다.")
                    .documentName("오류")
                    .confidence(0)
                    .downloadUrl(null)
                    .build();
        }
    }

    public SearchResponse answerQuestionInDocument(String query, Long documentId, int topK) {
        try {
            log.info("Answering question in document {}: {}", documentId, query);

            Document document = documentService.getDocument(documentId);
            List<Map<String, Object>> similarChunks = searchSimilarChunksInDocument(query, documentId, topK);

            if (similarChunks.isEmpty()) {
                return SearchResponse.builder()
                        .query(query)
                        .answer("죄송합니다. 해당 문서에서 관련된 정보를 찾을 수 없습니다.")
                        .documentName(document.getFileName())
                        .confidence(0)
                        .downloadUrl("http://localhost:8050/api/documents/download/" + document.getId())
                        .build();
            }

            String context = buildSimpleContext(similarChunks);
            String answer = generateSimpleAnswerForDocument(query, context, document.getFileName());
            int confidence = calculateSimpleConfidence(similarChunks, answer);

            return SearchResponse.builder()
                    .query(query)
                    .answer(answer)
                    .documentName(document.getFileName())
                    .confidence(confidence)
                    .downloadUrl("http://localhost:8050/api/documents/download/" + document.getId())
                    .build();

        } catch (Exception e) {
            log.error("Error answering question in document", e);
            return SearchResponse.builder()
                    .query(query)
                    .answer("답변 생성 중 오류가 발생했습니다.")
                    .documentName("오류")
                    .confidence(0)
                    .downloadUrl(null)
                    .build();
        }
    }

    // 유사도 기반 검색 메서드들 - 순서가 중요함!
    public List<Map<String, Object>> searchSimilarChunksInCategory(String query, Long categoryId, int topK) {
        try {
            float[] embeddingArray = embeddingModel.embed(query);
            List<Double> queryEmbedding = new ArrayList<>();
            for (float f : embeddingArray) {
                queryEmbedding.add((double) f);
            }
            String queryVector = convertEmbeddingToString(queryEmbedding);

            // 유사도 순으로 정렬된 결과를 반환 (가장 유사한 것이 첫 번째)
            List<VectorStore> results = vectorStoreRepository.findSimilarVectorsByCategory(queryVector, categoryId, topK);

            return results.stream().map(this::mapVectorStoreToResult).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error performing category search", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> searchSimilarChunksInDocument(String query, Long documentId, int topK) {
        try {
            float[] embeddingArray = embeddingModel.embed(query);
            List<Double> queryEmbedding = new ArrayList<>();
            for (float f : embeddingArray) {
                queryEmbedding.add((double) f);
            }
            String queryVector = convertEmbeddingToString(queryEmbedding);

            List<VectorStore> results = vectorStoreRepository.findSimilarVectorsByDocument(queryVector, documentId, topK);

            return results.stream().map(this::mapVectorStoreToResult).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error performing document search", e);
            return Collections.emptyList();
        }
    }

    // 새로운 헬퍼 메서드들 - 간단한 응답 생성용

    /**
     * 간단한 컨텍스트 구성 - chunk별 구분 없이 하나의 텍스트로 합침
     */
    private String buildSimpleContext(List<Map<String, Object>> chunks) {
        return chunks.stream()
                .map(chunk -> (String) chunk.get("content"))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 간단한 답변 생성
     */
    private String generateSimpleAnswer(String query, String context) {
        String prompt = String.format("""
            다음 내용을 바탕으로 질문에 간결하게 답변해주세요.
            
            질문: %s
            
            관련 내용:
            %s
            
            답변:
            """, query, context);

        return chatModel.call(prompt);
    }

    /**
     * 특정 문서용 간단한 답변 생성
     */
    private String generateSimpleAnswerForDocument(String query, String context, String fileName) {
        String prompt = String.format("""
            '%s' 문서의 다음 내용을 바탕으로 질문에 간결하게 답변해주세요.
            
            질문: %s
            
            문서 내용:
            %s
            
            답변:
            """, fileName, query, context);

        return chatModel.call(prompt);
    }

    /**
     * 🆕 주요 참조 문서 객체 반환 - 첨부파일 정보용
     */
    private Document getMainDocument(List<Map<String, Object>> chunks, String query) {
        if (chunks.isEmpty()) {
            return null;
        }

        try {
            // 가장 유사도가 높은 첫 번째 chunk의 문서 ID로 Document 객체 조회
            Long documentId = (Long) chunks.get(0).get("document_id");
            return documentService.getDocument(documentId);
        } catch (Exception e) {
            log.error("Error getting main document", e);
            return null;
        }
    }
    private String getBestMatchingDocumentName(List<Map<String, Object>> chunks, String query) {
        if (chunks.isEmpty()) {
            return "알 수 없음";
        }

        // 방법 1: 첫 번째 chunk의 문서 (가장 높은 유사도)
        String firstDocumentName = (String) chunks.get(0).get("file_name");

        // 방법 2: AI에게 어느 문서가 가장 관련성이 높은지 질문
        try {
            // 문서별로 그룹화하여 각 문서의 대표 내용 추출
            Map<String, List<Map<String, Object>>> documentGroups = chunks.stream()
                    .collect(Collectors.groupingBy(chunk -> (String) chunk.get("file_name")));

            if (documentGroups.size() == 1) {
                // 문서가 하나뿐이면 그것을 반환
                return firstDocumentName;
            }

            // 여러 문서가 있는 경우, AI에게 가장 관련성 높은 문서 선택 요청
            StringBuilder documentInfo = new StringBuilder();
            documentGroups.forEach((docName, docChunks) -> {
                documentInfo.append("문서: ").append(docName).append("\n");
                documentInfo.append("내용 미리보기: ")
                        .append(((String) docChunks.get(0).get("content"))
                                .substring(0, Math.min(200, docChunks.get(0).get("content").toString().length())))
                        .append("...\n\n");
            });

            String selectionPrompt = String.format("""
                다음 질문에 가장 적합한 문서를 선택해주세요. 문서명만 정확히 답변하세요.
                
                질문: %s
                
                문서들:
                %s
                
                가장 관련성이 높은 문서명:
                """, query, documentInfo.toString());

            String selectedDoc = chatModel.call(selectionPrompt).trim();

            // AI가 선택한 문서가 실제 목록에 있는지 확인
            if (documentGroups.containsKey(selectedDoc)) {
                log.info("AI selected document: {} for query: {}", selectedDoc, query);
                return selectedDoc;
            } else {
                log.warn("AI selected invalid document: {}, falling back to first document: {}",
                        selectedDoc, firstDocumentName);
                return firstDocumentName;
            }

        } catch (Exception e) {
            log.error("Error in AI document selection, using first document", e);
            return firstDocumentName;
        }
    }

    /**
     * 기존 방식 유지 (백업용) - 가장 많이 나타나는 문서명 반환
     */
    private String getMainDocumentName(List<Map<String, Object>> chunks) {
        Map<String, Long> documentCounts = chunks.stream()
                .collect(Collectors.groupingBy(
                        chunk -> (String) chunk.get("file_name"),
                        Collectors.counting()
                ));

        return documentCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("알 수 없음");
    }

    /**
     * 간단한 신뢰도 계산 - 퍼센트로 반환 (0~100)
     */
    private int calculateSimpleConfidence(List<Map<String, Object>> chunks, String answer) {
        if (chunks.isEmpty() || answer.contains("답변할 수 없습니다") || answer.contains("정보를 찾을 수 없습니다")) {
            return 0;
        }

        // chunks 개수와 답변 길이를 기반으로 간단한 신뢰도 계산
        double chunkScore = Math.min(chunks.size() / 3.0, 1.0);
        double answerScore = Math.min(answer.length() / 50.0, 1.0);

        // 0~100 사이의 정수로 변환
        double confidenceRatio = (chunkScore + answerScore) / 2.0;
        return (int) Math.round(confidenceRatio * 100);
    }

    // 기존 유틸리티 메서드들
    private Map<String, Object> mapVectorStoreToResult(VectorStore vs) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", vs.getContent());
        result.put("metadata", vs.getMetadata());
        result.put("document_id", vs.getDocument().getId());
        result.put("file_name", vs.getDocument().getFileName());
        result.put("chunk_index", vs.getChunkIndex());
        return result;
    }

    private String convertEmbeddingToString(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }
}