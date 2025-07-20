package devlava.docai.controller;

import devlava.docai.dto.SearchResponse;
import devlava.docai.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 특정 카테고리에서 질문에 대한 답변 생성
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
     * 특정 문서에서 질문에 대한 답변 생성
     */
    @PostMapping("/document/{documentId}/answer")
    public ResponseEntity<SearchResponse> answerQuestionInDocument(
            @PathVariable Long documentId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        SearchResponse response = searchService.answerQuestionInDocument(query, documentId, topK);
        return ResponseEntity.ok(response);
    }
}