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

                    // 2. 컨텍스트 구성
                    String context = buildContext(similarChunks);

                    // 3. 프롬프트 생성
                    return String.format("""
                        다음 문서들의 정보를 바탕으로 사용자의 질문에 답변해주세요.
                        
                        질문: %s
                        
                        관련 문서 정보:
                        %s
                        
                        답변 지침:
                        1. 제공된 문서 정보만을 기반으로 답변하세요
                        2. 문서에 없는 정보는 추측하지 마세요
                        3. 답변할 수 없는 경우 명확히 표현하세요
                        4. 가능한 한 구체적이고 정확한 답변을 제공하세요
                        5. 여러 문서에서 정보를 찾은 경우, 이를 종합해서 답변하세요
                        
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

                    // 2. 컨텍스트 구성
                    String context = buildContext(similarChunks);

                    // 3. 프롬프트 생성 (문서 특화)
                    return String.format("""
                        '%s' 문서의 내용을 바탕으로 사용자의 질문에 답변해주세요.
                        
                        질문: %s
                        
                        문서 내용:
                        %s
                        
                        답변 지침:
                        1. 해당 문서의 내용만을 기반으로 답변하세요
                        2. 문서에 없는 정보는 추측하지 마세요
                        3. 답변할 수 없는 경우 명확히 표현하세요
                        4. 가능한 한 구체적이고 정확한 답변을 제공하세요
                        
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
            // OllamaChatModel의 stream 메서드 사용
            return chatModel.stream(prompt);
        } catch (Exception e) {
            log.error("Error generating stream answer", e);
            return Flux.just("스트림 답변 생성 중 오류가 발생했습니다.");
        }
    }

    // 기존 메서드들 유지 (비 스트림 방식)
    public SearchResponse answerQuestionInCategory(String query, Long categoryId, int topK) {
        try {
            log.info("Answering question in category {}: {}", categoryId, query);

            List<Map<String, Object>> similarChunks = searchSimilarChunksInCategory(query, categoryId, topK);

            if (similarChunks.isEmpty()) {
                return SearchResponse.builder()
                        .query(query)
                        .answer("죄송합니다. 해당 카테고리에서 관련된 정보를 찾을 수 없습니다.")
                        .sources(Collections.emptyList())
                        .confidence(0.0)
                        .build();
            }

            String context = buildContext(similarChunks);
            String answer = generateAnswer(query, context);
            List<SourceInfo> sources = buildSourceInfo(similarChunks);
            double confidence = calculateConfidence(similarChunks, answer);

            return SearchResponse.builder()
                    .query(query)
                    .answer(answer)
                    .sources(sources)
                    .confidence(confidence)
                    .totalChunks(similarChunks.size())
                    .build();

        } catch (Exception e) {
            log.error("Error answering question in category", e);
            return SearchResponse.builder()
                    .query(query)
                    .answer("답변 생성 중 오류가 발생했습니다.")
                    .sources(Collections.emptyList())
                    .confidence(0.0)
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
                        .sources(Collections.emptyList())
                        .confidence(0.0)
                        .build();
            }

            String context = buildContext(similarChunks);
            String answer = generateAnswerForDocument(query, context, document.getFileName());
            List<SourceInfo> sources = buildSourceInfo(similarChunks);
            double confidence = calculateConfidence(similarChunks, answer);

            return SearchResponse.builder()
                    .query(query)
                    .answer(answer)
                    .sources(sources)
                    .confidence(confidence)
                    .totalChunks(similarChunks.size())
                    .documentName(document.getFileName())
                    .build();

        } catch (Exception e) {
            log.error("Error answering question in document", e);
            return SearchResponse.builder()
                    .query(query)
                    .answer("답변 생성 중 오류가 발생했습니다.")
                    .sources(Collections.emptyList())
                    .confidence(0.0)
                    .build();
        }
    }

    // 나머지 기존 메서드들은 동일하게 유지
    public List<Map<String, Object>> searchSimilarChunksInCategory(String query, Long categoryId, int topK) {
        try {
            float[] embeddingArray = embeddingModel.embed(query);
            List<Double> queryEmbedding = new ArrayList<>();
            for (float f : embeddingArray) {
                queryEmbedding.add((double) f);
            }
            String queryVector = convertEmbeddingToString(queryEmbedding);

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

    private String buildContext(List<Map<String, Object>> chunks) {
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> chunk = chunks.get(i);
            context.append("문서 ").append(i + 1).append(":\n");
            context.append("파일명: ").append(chunk.get("file_name")).append("\n");
            context.append("내용: ").append(chunk.get("content")).append("\n\n");
        }

        return context.toString();
    }

    private String generateAnswer(String query, String context) {
        String prompt = String.format("""
            다음 문서들의 정보를 바탕으로 사용자의 질문에 답변해주세요.
            
            질문: %s
            
            관련 문서 정보:
            %s
            
            답변 지침:
            1. 제공된 문서 정보만을 기반으로 답변하세요
            2. 문서에 없는 정보는 추측하지 마세요
            3. 답변할 수 없는 경우 명확히 표현하세요
            4. 가능한 한 구체적이고 정확한 답변을 제공하세요
            5. 여러 문서에서 정보를 찾은 경우, 이를 종합해서 답변하세요
            
            답변:
            """, query, context);

        return chatModel.call(prompt);
    }

    private String generateAnswerForDocument(String query, String context, String fileName) {
        String prompt = String.format("""
            '%s' 문서의 내용을 바탕으로 사용자의 질문에 답변해주세요.
            
            질문: %s
            
            문서 내용:
            %s
            
            답변 지침:
            1. 해당 문서의 내용만을 기반으로 답변하세요
            2. 문서에 없는 정보는 추측하지 마세요
            3. 답변할 수 없는 경우 명확히 표현하세요
            4. 가능한 한 구체적이고 정확한 답변을 제공하세요
            
            답변:
            """, fileName, query, context);

        return chatModel.call(prompt);
    }

    private List<SourceInfo> buildSourceInfo(List<Map<String, Object>> chunks) {
        return chunks.stream()
                .map(chunk -> SourceInfo.builder()
                        .documentId((Long) chunk.get("document_id"))
                        .fileName((String) chunk.get("file_name"))
                        .chunkIndex((Integer) chunk.get("chunk_index"))
                        .content(truncateContent((String) chunk.get("content"), 200))
                        .build())
                .collect(Collectors.toList());
    }

    private double calculateConfidence(List<Map<String, Object>> chunks, String answer) {
        if (chunks.isEmpty() || answer.contains("답변할 수 없습니다") || answer.contains("정보를 찾을 수 없습니다")) {
            return 0.0;
        }

        double chunkScore = Math.min(chunks.size() / 5.0, 1.0);
        double answerScore = Math.min(answer.length() / 100.0, 1.0);

        return (chunkScore + answerScore) / 2.0;
    }

    private Map<String, Object> mapVectorStoreToResult(VectorStore vs) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", vs.getContent());
        result.put("metadata", vs.getMetadata());
        result.put("document_id", vs.getDocument().getId());
        result.put("file_name", vs.getDocument().getFileName());
        result.put("chunk_index", vs.getChunkIndex());
        return result;
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private String convertEmbeddingToString(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }
}