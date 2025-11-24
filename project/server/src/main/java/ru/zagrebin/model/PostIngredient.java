package ru.zagrebin.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "post_ingredient", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_ingredient", columnNames = {"post_id", "ingredient_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "quantity_value")
    private Double quantityValue; // numeric, e.g. 300.0

    @Column(name = "quantity_unit", length = 50)
    private String unit; // "г", "шт", "мл"
}
