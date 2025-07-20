package devlava.docai.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @JsonBackReference
    private Category category;

    private String fileName;
    private String filePath;
    private String contentType;
    private long fileSize;

    // VectorStore와의 관계 추가
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private Set<VectorStore> vectorStores = new LinkedHashSet<>();

    // 편의 메서드
    public void addVectorStore(VectorStore vectorStore) {
        this.vectorStores.add(vectorStore);
        vectorStore.setDocument(this);
    }

    public void removeVectorStore(VectorStore vectorStore) {
        this.vectorStores.remove(vectorStore);
        vectorStore.setDocument(null);
    }
}