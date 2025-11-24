package ru.zagrebin.model;


import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_status_created_at", columnList = "status, created_at"),
        @Index(name = "idx_posts_author", columnList = "author_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_type", length = 20, nullable = false)
    private String postType;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String excerpt;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User author;

    @ManyToMany
    @JoinTable(
            name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order ASC")
    @Builder.Default
    private List<RecipeStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<PostIngredient> ingredients = new HashSet<>();

    @Column(name = "likes_count")
    private Integer likesCount = 0;

    @Column(name = "comments_count")
    private Integer commentsCount = 0;

    @Column(name = "views_count")
    private Long viewsCount = 0L;

    @Column
    private Integer calories;

    @Column(name = "cooking_time_minutes")
    private Integer cookingTimeMinutes;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (likesCount == null) likesCount = 0;
        if (commentsCount == null) commentsCount = 0;
        if (viewsCount == null) viewsCount = 0L;
        if (status == null) status = "draft";
        if (postType == null) postType = "recipe";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
