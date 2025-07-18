package devlava.docai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        log.error("=== 파일 업로드 크기 초과 ===");
        log.error("Exception message: {}", exc.getMessage());
        log.error("Max upload size: {}", exc.getMaxUploadSize());
        log.error("Root cause: {}", exc.getRootCause() != null ? exc.getRootCause().getMessage() : "없음");

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("파일 크기 초과 - 최대 크기: " + exc.getMaxUploadSize() + " bytes");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<String> handleMultipartException(MultipartException exc) {
        log.error("Multipart exception: {}", exc.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("파일 업로드 중 오류가 발생했습니다: " + exc.getMessage());
    }
}