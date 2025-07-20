package devlava.docai.controller;

import devlava.docai.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final DocumentService documentService;

    /**
     * 특정 카테고리 하위의 모든 파일에서 검색
     *
     * @param categoryId 카테고리 ID
     * @param query 검색할 질문/쿼리
     * @param topK 반환할 최대 결과 수 (기본값: 5)
     * @return 유사한 문서 청크들의 리스트
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Map<String, Object>>> searchInCategory(
            @PathVariable Long categoryId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        List<Map<String, Object>> results = documentService.searchInCategory(query, categoryId, topK);
        return ResponseEntity.ok(results);
    }

    /**
     * 단일 파일에 대한 질문/검색
     *
     * @param documentId 문서 ID
     * @param query 검색할 질문/쿼리
     * @param topK 반환할 최대 결과 수 (기본값: 5)
     * @return 해당 문서 내에서 유사한 청크들의 리스트
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<Map<String, Object>>> searchInDocument(
            @PathVariable Long documentId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        List<Map<String, Object>> results = documentService.searchInDocument(query, documentId, topK);
        return ResponseEntity.ok(results);
    }
}