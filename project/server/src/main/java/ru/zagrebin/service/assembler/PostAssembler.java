package ru.zagrebin.service.assembler;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.zagrebin.dto.PostCreateDto;
import ru.zagrebin.dto.PostIngredientCreateDto;
import ru.zagrebin.dto.PostUpdateDto;
import ru.zagrebin.dto.RecipeStepCreateDto;
import ru.zagrebin.model.*;
import ru.zagrebin.repository.IngredientRepository;
import ru.zagrebin.repository.TagRepository;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class PostAssembler {

    private final TagRepository tagRepository;
    private final IngredientRepository ingredientRepository;

    public PostAssembler(TagRepository tagRepository,
                         IngredientRepository ingredientRepository) {
        this.tagRepository = tagRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Transactional
    public Post createFromDto(PostCreateDto dto, User author) {
        Post post = new Post();
        post.setPostType(dto.getPostType());
        post.setStatus(PostStatus.from(dto.getStatus()));
        post.setTitle(dto.getTitle());
        post.setExcerpt(dto.getExcerpt());
        post.setContent(dto.getContent());
        post.setCoverUrl(dto.getCoverUrl());
        post.setCookingTimeMinutes(dto.getCookingTimeMinutes());
        post.setCalories(dto.getCalories());
        post.setClientId(dto.getClientId());

        post.setAuthor(author);

        // tags
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            Set<Tag> tags = new HashSet<>(tagRepository.findAllById(dto.getTagIds()));
            post.setTags(tags);
        }

        // ingredients
        if (dto.getIngredients() != null && !dto.getIngredients().isEmpty()) {
            Set<PostIngredient> pis = new HashSet<>();
            for (PostIngredientCreateDto pigi : dto.getIngredients()) {
                Ingredient ing = ingredientRepository.findById(pigi.getIngredientId())
                        .orElseThrow(() -> new EntityNotFoundException("Ingredient not found: " + pigi.getIngredientId()));
                PostIngredient pi = PostIngredient.builder()
                        .post(post) // will be set when saving relation
                        .ingredient(ing)
                        .quantityValue(pigi.getQuantityValue())
                        .unit(pigi.getUnit())
                        .build();
                pis.add(pi);
            }
            post.setIngredients(pis);
        }

        // steps — create recipe steps preserving given order
        if (dto.getSteps() != null && !dto.getSteps().isEmpty()) {
            List<RecipeStep> steps = new ArrayList<>();
            for (RecipeStepCreateDto s : dto.getSteps()) {
                RecipeStep step = RecipeStep.builder()
                        .post(post)
                        .order(s.getOrder())
                        .description(s.getDescription())
                        .imageUrl(s.getImageUrl())
                        .build();
                steps.add(step);
            }
            // ensure ordered
            steps.sort(Comparator.comparingInt(RecipeStep::getOrder));
            post.setSteps(steps);
        }

        // likes/comments/views default already via entity @PrePersist
        return post; // will be persisted by service
    }

    /**
     * Update existing post from DTO.
     * We load post with all relations (use repository.findByIdWithAllRelations)
     * and then synchronize collections.
     */
    @Transactional
    public Post updateFromDto(Post post, PostUpdateDto dto) {
        post.setPostType(dto.getPostType());
        post.setStatus(PostStatus.from(dto.getStatus()));
        post.setTitle(dto.getTitle());
        post.setExcerpt(dto.getExcerpt());
        post.setContent(dto.getContent());
        post.setCoverUrl(dto.getCoverUrl());
        post.setCookingTimeMinutes(dto.getCookingTimeMinutes());
        post.setCalories(dto.getCalories());

        // tags synchronization: replace with requested set
        Set<Tag> newTags = new HashSet<>();
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            newTags.addAll(tagRepository.findAllById(dto.getTagIds()));
        }
        post.getTags().clear();
        post.getTags().addAll(newTags);

        // ingredients synchronization:
        // simple strategy: clear and recreate (because quantity/unit may change)
        post.getIngredients().clear();
        if (dto.getIngredients() != null) {
            for (PostIngredientCreateDto pic : dto.getIngredients()) {
                Ingredient ing = ingredientRepository.findById(pic.getIngredientId())
                        .orElseThrow(() -> new EntityNotFoundException("Ingredient not found: " + pic.getIngredientId()));
                PostIngredient pi = PostIngredient.builder()
                        .post(post)
                        .ingredient(ing)
                        .quantityValue(pic.getQuantityValue())
                        .unit(pic.getUnit())
                        .build();
                post.getIngredients().add(pi);
            }
        }

        // steps synchronization: we will clear and recreate in correct order
        post.getSteps().clear();
        if (dto.getSteps() != null) {
            List<RecipeStep> steps = dto.getSteps().stream()
                    .map(s -> {
                        RecipeStep rs = new RecipeStep();
                        rs.setPost(post);
                        rs.setOrder(s.getOrder());
                        rs.setDescription(s.getDescription());
                        rs.setImageUrl(s.getImageUrl());
                        return rs;
                    })
                    .sorted(Comparator.comparingInt(RecipeStep::getOrder))
                    .collect(Collectors.toList());
            post.getSteps().addAll(steps);
        }

        return post;
    }

    /**
     * Update collections (tags, ingredients, steps) from DTO without recreating the post entity.
     * Used for idempotent upsert when clientId matches existing post.
     */
    @Transactional
    public void updateCollections(Post post, PostCreateDto dto) {
        // tags synchronization
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            Set<Tag> newTags = new HashSet<>(tagRepository.findAllById(dto.getTagIds()));
            post.getTags().clear();
            post.getTags().addAll(newTags);
        } else {
            post.getTags().clear();
        }

        // ingredients synchronization
        post.getIngredients().clear();
        if (dto.getIngredients() != null && !dto.getIngredients().isEmpty()) {
            for (PostIngredientCreateDto pigi : dto.getIngredients()) {
                Ingredient ing = ingredientRepository.findById(pigi.getIngredientId())
                        .orElseThrow(() -> new EntityNotFoundException("Ingredient not found: " + pigi.getIngredientId()));
                PostIngredient pi = PostIngredient.builder()
                        .post(post)
                        .ingredient(ing)
                        .quantityValue(pigi.getQuantityValue())
                        .unit(pigi.getUnit())
                        .build();
                post.getIngredients().add(pi);
            }
        }

        // steps synchronization
        post.getSteps().clear();
        if (dto.getSteps() != null && !dto.getSteps().isEmpty()) {
            List<RecipeStep> steps = new ArrayList<>();
            for (RecipeStepCreateDto s : dto.getSteps()) {
                RecipeStep step = RecipeStep.builder()
                        .post(post)
                        .order(s.getOrder())
                        .description(s.getDescription())
                        .imageUrl(s.getImageUrl())
                        .build();
                steps.add(step);
            }
            steps.sort(Comparator.comparingInt(RecipeStep::getOrder));
            post.getSteps().addAll(steps);
        }
    }
}
