package devlava.docai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchResponse {
    private String query;           // 원본 질문
    private String answer;          // LLM이 생성한 답변
    private List<SourceInfo> sources; // 출처 정보
    private double confidence;      // 신뢰도 (0.0 ~ 1.0)
    private int totalChunks;       // 검색된 총 청크 수
    private String documentName;   // 단일 문서 검색시 문서명
}