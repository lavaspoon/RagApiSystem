package devlava.docai.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class DocumentProcessor {
    private static final int MAX_CHUNK_SIZE = 1000; // 청크 최대 크기
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]\\s+");

    public List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 문장 단위로 분리
        String[] sentences = SENTENCE_PATTERN.split(text.trim());
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            String trimmedSentence = sentence.trim() + ". ";

            // 현재 청크에 문장을 추가했을 때 최대 크기를 초과하는 경우
            if (currentChunk.length() + trimmedSentence.length() > MAX_CHUNK_SIZE) {
                // 현재 청크가 비어있지 않으면 chunks 리스트에 추가
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }

                // 긴 문장을 여러 청크로 분할
                if (trimmedSentence.length() > MAX_CHUNK_SIZE) {
                    String[] words = trimmedSentence.split("\\s+");
                    StringBuilder wordChunk = new StringBuilder();

                    for (String word : words) {
                        if (wordChunk.length() + word.length() + 1 > MAX_CHUNK_SIZE) {
                            chunks.add(wordChunk.toString().trim());
                            wordChunk = new StringBuilder();
                        }
                        wordChunk.append(word).append(" ");
                    }

                    if (wordChunk.length() > 0) {
                        currentChunk = wordChunk;
                    }
                } else {
                    currentChunk.append(trimmedSentence);
                }
            } else {
                currentChunk.append(trimmedSentence);
            }
        }

        // 마지막 청크 처리
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}