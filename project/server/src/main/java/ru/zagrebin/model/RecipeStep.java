package ru.zagrebin.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "recipe_step", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_step_order", columnNames = {"post_id", "step_order"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Post post;

    @Column(name = "step_order", nullable = false)
    private Integer order;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;
}
