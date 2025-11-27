package ru.zagrebin.mapper;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import ru.zagrebin.dto.PostCardDto;
import ru.zagrebin.dto.PostFullDto;
import ru.zagrebin.model.Post;
import ru.zagrebin.model.RecipeStep;
import ru.zagrebin.model.Tag;

public final class PostMapper {
    private PostMapper() {}

    public static PostCardDto toCard(Post p) {
        if (p == null) return null;
        PostCardDto dto = new PostCardDto();
        dto.setId(p.getId());
        dto.setTitle(p.getTitle());
        dto.setExcerpt(p.getExcerpt());
        dto.setCoverUrl(p.getCoverUrl());
        dto.setAuthorId(p.getAuthor() != null ? p.getAuthor().getId() : null);
        dto.setLikesCount(p.getLikesCount() == null ? 0 : p.getLikesCount());
        dto.setCookingTimeMinutes(p.getCookingTimeMinutes());
        dto.setCalories(p.getCalories());
        dto.setAuthorName(p.getAuthor() != null ? p.getAuthor().getDisplayName() : "Unknown");
        dto.setPublishedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "Unknown");
        dto.setViewsCount(p.getViewsCount() == null ? 0 : p.getViewsCount());
        dto.setTags(p.getTags().stream().map(Tag::getName).collect(Collectors.toSet()));
        return dto;
    }

    public static PostFullDto toFull(Post p, boolean isLiked, boolean isSubscribed) {
        if (p == null) return null;
        PostFullDto dto = new PostFullDto();
        dto.setId(p.getId());
        dto.setPostType(p.getPostType());
        dto.setStatus(p.getStatus());
        dto.setTitle(p.getTitle());
        dto.setExcerpt(p.getExcerpt());
        dto.setContent(p.getContent());
        dto.setCoverUrl(p.getCoverUrl());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        // author
        dto.setAuthor(AuthorMapper.toDto(p.getAuthor()));
        if (dto.getAuthor() != null) dto.getAuthor().setSubscribed(isSubscribed);

        dto.setTags(p.getTags().stream().map(TagMapper::toDto).collect(Collectors.toList()));
        dto.setIngredients(p.getIngredients().stream().map(IngredientMapper::toDto).collect(Collectors.toList()));

        // Дедуплируем шаги по id и сортируем по order
        List<RecipeStep> uniqueSteps = p.getSteps().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        RecipeStep::getId,
                        s -> s,
                        (a, b) -> a,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparingInt(RecipeStep::getOrder))
                .collect(Collectors.toList());
        dto.setSteps(uniqueSteps.stream().map(StepMapper::toDto).collect(Collectors.toList()));

        dto.setLikesCount(p.getLikesCount() == null ? 0 : p.getLikesCount());
        dto.setLiked(isLiked);
        dto.setViewsCount(p.getViewsCount() == null ? 0L : p.getViewsCount());
        dto.setCalories(p.getCalories());
        dto.setCookingTimeMinutes(p.getCookingTimeMinutes());
        return dto;
    }
}
