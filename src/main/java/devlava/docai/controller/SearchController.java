package devlava.docai.controller;

import devlava.docai.dto.SearchResponse;
import devlava.docai.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@CrossOrigin("*")
public class SearchController {

    private final SearchService searchService;

    /**
     * 특정 카테고리에서 질문에 대한 답변 생성 (기존 방식)
     */
    @PostMapping("/category/{categoryId}/answer")
    public ResponseEntity<SearchResponse> answerQuestionInCategory(
            @PathVariable Long categoryId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        SearchResponse response = searchService.answerQuestionInCategory(query, categoryId, topK);
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 문서에서 질문에 대한 답변 생성 (기존 방식)
     */
    @PostMapping("/document/{documentId}/answer")
    public ResponseEntity<SearchResponse> answerQuestionInDocument(
            @PathVariable Long documentId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        SearchResponse response = searchService.answerQuestionInDocument(query, documentId, topK);
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 카테고리에서 질문에 대한 답변 생성 (Stream 방식)
     */
    @PostMapping(value = "/category/{categoryId}/answer/stream",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> answerQuestionInCategoryStream(
            @PathVariable Long categoryId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        return searchService.answerQuestionInCategoryStream(query, categoryId, topK);
    }

    /**
     * 특정 문서에서 질문에 대한 답변 생성 (Stream 방식)
     */
    @PostMapping(value = "/document/{documentId}/answer/stream",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> answerQuestionInDocumentStream(
            @PathVariable Long documentId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        return searchService.answerQuestionInDocumentStream(query, documentId, topK);
    }
}