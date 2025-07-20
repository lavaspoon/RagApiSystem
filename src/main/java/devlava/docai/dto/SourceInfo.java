package devlava.docai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SourceInfo {
    private Long documentId;    // 문서 ID
    private String fileName;    // 파일명
    private Integer chunkIndex; // 청크 인덱스
    private String content;     // 청크 내용 (일부)
}