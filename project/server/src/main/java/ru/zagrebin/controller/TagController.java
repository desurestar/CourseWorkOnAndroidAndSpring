package ru.zagrebin.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import ru.zagrebin.dto.PaginatedResponse;
import ru.zagrebin.dto.TagDto;
import ru.zagrebin.mapper.TagMapper;
import ru.zagrebin.model.Tag;
import ru.zagrebin.repository.TagRepository;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagRepository tagRepository;

    public TagController(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<TagDto>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "16") int pageSize,
            @RequestParam(required = false) String search
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, pageSize);
        Page<Tag> tags = (search != null && !search.isBlank())
                ? tagRepository.findByNameContainingIgnoreCase(search, pageable)
                : tagRepository.findAll(pageable);

        List<TagDto> results = tags.getContent().stream()
                .map(TagMapper::toDto)
                .collect(Collectors.toList());

        String next = null;
        if (tags.hasNext()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/tags")
                    .queryParam("page", page + 1)
                    .queryParam("page_size", pageSize);
            if (search != null && !search.isBlank()) {
                builder.queryParam("search", search);
            }
            next = builder.build().toString();
        }

        return ResponseEntity.ok(new PaginatedResponse<>(results, next));
    }
}
