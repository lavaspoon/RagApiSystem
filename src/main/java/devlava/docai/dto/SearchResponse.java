package devlava.docai.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SearchResponse {

    private String query;           // 사용자 질문
    private String answer;          // AI 답변
    private String documentName;    // 주요 참조 문서명
    private int confidence;         // 신뢰도 퍼센트 (0 ~ 100)
    private String downloadUrl;     // 다운로드 URL

}