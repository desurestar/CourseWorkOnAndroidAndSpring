package ru.zagrebin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostFilterRequest {
    private String postType;
    private Integer cookingTimeMin;
    private Integer cookingTimeMax;
    private Integer caloriesMin;
    private Integer caloriesMax;
    private List<String> tags;

    public PostFilterRequest normalize() {
        String normalizedType = postType != null ? postType.toLowerCase() : null;
        if ("article".equals(normalizedType)) {
            return PostFilterRequest.builder()
                    .postType(normalizedType)
                    .tags(tags)
                    .build();
        }
        return PostFilterRequest.builder()
                .postType(normalizedType)
                .cookingTimeMin(cookingTimeMin)
                .cookingTimeMax(cookingTimeMax)
                .caloriesMin(caloriesMin)
                .caloriesMax(caloriesMax)
                .tags(tags)
                .build();
    }

    public boolean hasTags() {
        return tags != null && !tags.isEmpty();
    }

    public List<String> effectiveTags() {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return tags;
    }

    public int tagsCount() {
        return tags == null ? 0 : tags.size();
    }

    public void appendQueryParams(UriComponentsBuilder builder) {
        if (postType != null) builder.queryParam("post_type", postType);
        if (cookingTimeMin != null) builder.queryParam("cooking_time_min", cookingTimeMin);
        if (cookingTimeMax != null) builder.queryParam("cooking_time_max", cookingTimeMax);
        if (caloriesMin != null) builder.queryParam("calories_min", caloriesMin);
        if (caloriesMax != null) builder.queryParam("calories_max", caloriesMax);
        if (tags != null) {
            tags.forEach(tag -> builder.queryParam("tags", tag));
        }
    }
}
