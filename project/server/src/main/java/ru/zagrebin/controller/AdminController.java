package ru.zagrebin.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import ru.zagrebin.dto.AdminPostDto;
import ru.zagrebin.dto.CreateIngredientRequest;
import ru.zagrebin.dto.CreateTagRequest;
import ru.zagrebin.dto.IngredientDto;
import ru.zagrebin.dto.PaginatedResponse;
import ru.zagrebin.dto.TagDto;
import ru.zagrebin.dto.UpdatePostStatusRequest;
import ru.zagrebin.dto.UpdateUserRoleRequest;
import ru.zagrebin.dto.UserDto;
import ru.zagrebin.mapper.IngredientMapper;
import ru.zagrebin.mapper.TagMapper;
import ru.zagrebin.model.Ingredient;
import ru.zagrebin.model.Post;
import ru.zagrebin.model.Tag;
import ru.zagrebin.model.User;
import ru.zagrebin.repository.IngredientRepository;
import ru.zagrebin.repository.PostRepository;
import ru.zagrebin.repository.TagRepository;
import ru.zagrebin.repository.UserRepository;
import ru.zagrebin.security.Roles;
import ru.zagrebin.security.UserPrincipal;
import ru.zagrebin.service.PostService;
import ru.zagrebin.util.UrlHelper;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Set<String> ALLOWED_STATUSES = Set.of("draft", "published", "archived");

    private final PostRepository postRepository;
    private final IngredientRepository ingredientRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final PostService postService;

    public AdminController(PostRepository postRepository,
                           IngredientRepository ingredientRepository,
                           TagRepository tagRepository,
                           UserRepository userRepository,
                           PostService postService) {
        this.postRepository = postRepository;
        this.ingredientRepository = ingredientRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.postService = postService;
    }

    @GetMapping("/posts")
    @Transactional(readOnly = true)
    public ResponseEntity<PaginatedResponse<AdminPostDto>> listPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search
    ) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> posts = (search != null && !search.isBlank())
                ? postRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(search.trim(), pageable)
                : postRepository.findAll(pageable);

        var results = posts.getContent().stream()
                .map(this::toAdminPostDto)
                .collect(Collectors.toList());

        String next = posts.hasNext()
                ? UriComponentsBuilder.fromPath("/api/admin/posts")
                .queryParam("page", page + 1)
                .queryParam("page_size", pageSize)
                .queryParamIfPresent("search", java.util.Optional.ofNullable(search).filter(s -> !s.isBlank()))
                .build().toString()
                : null;

        return ResponseEntity.ok(new PaginatedResponse<>(results, next));
    }

    @PutMapping("/posts/{id}/status")
    public ResponseEntity<AdminPostDto> updatePostStatus(@PathVariable Long id,
                                                         @Valid @RequestBody UpdatePostStatusRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + id));
        String normalized = normalizeStatus(request.status());
        post.setStatus(normalized);
        Post saved = postRepository.save(post);
        return ResponseEntity.ok(toAdminPostDto(saved));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        postService.delete(id, principal != null ? principal.getId() : null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ingredients")
    @Transactional(readOnly = true)
    public ResponseEntity<PaginatedResponse<IngredientDto>> listIngredients(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
            @RequestParam(required = false) String search
    ) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        var ingredients = (search != null && !search.isBlank())
                ? ingredientRepository.findByNameContainingIgnoreCase(search.trim(), pageable)
                : ingredientRepository.findAll(pageable);
        var results = ingredients.getContent().stream()
                .map(IngredientMapper::toDto)
                .collect(Collectors.toList());
        String next = ingredients.hasNext()
                ? UriComponentsBuilder.fromPath("/api/admin/ingredients")
                .queryParam("page", page + 1)
                .queryParam("page_size", pageSize)
                .queryParamIfPresent("search", java.util.Optional.ofNullable(search).filter(s -> !s.isBlank()))
                .build().toString()
                : null;
        return ResponseEntity.ok(new PaginatedResponse<>(results, next));
    }

    @PostMapping("/ingredients")
    public ResponseEntity<IngredientDto> createIngredient(@Valid @RequestBody CreateIngredientRequest request) {
        String name = request.name().trim();
        ingredientRepository.findByName(name).ifPresent(i -> {
            throw new IllegalArgumentException("Ingredient already exists: " + name);
        });
        Ingredient created = ingredientRepository.save(Ingredient.builder().name(name).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(IngredientMapper.toDto(created));
    }

    @DeleteMapping("/ingredients/{id}")
    public ResponseEntity<Void> deleteIngredient(@PathVariable Long id) {
        if (!ingredientRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        ingredientRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tags")
    @Transactional(readOnly = true)
    public ResponseEntity<PaginatedResponse<TagDto>> listTags(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
            @RequestParam(required = false) String search
    ) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        var tags = (search != null && !search.isBlank())
                ? tagRepository.findByNameContainingIgnoreCase(search.trim(), pageable)
                : tagRepository.findAll(pageable);
        var results = tags.getContent().stream()
                .map(TagMapper::toDto)
                .collect(Collectors.toList());
        String next = tags.hasNext()
                ? UriComponentsBuilder.fromPath("/api/admin/tags")
                .queryParam("page", page + 1)
                .queryParam("page_size", pageSize)
                .queryParamIfPresent("search", java.util.Optional.ofNullable(search).filter(s -> !s.isBlank()))
                .build().toString()
                : null;
        return ResponseEntity.ok(new PaginatedResponse<>(results, next));
    }

    @PostMapping("/tags")
    public ResponseEntity<TagDto> createTag(@Valid @RequestBody CreateTagRequest request) {
        String name = request.name().trim();
        tagRepository.findByName(name).ifPresent(t -> {
            throw new IllegalArgumentException("Tag already exists: " + name);
        });
        String slug = (request.slug() != null && !request.slug().isBlank())
                ? request.slug().trim()
                : generateSlug(name);
        Tag created = tagRepository.save(Tag.builder()
                .name(name)
                .slug(slug)
                .color(normalizeColor(request.color()))
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(TagMapper.toDto(created));
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        if (!tagRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        tagRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public ResponseEntity<PaginatedResponse<UserDto>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search
    ) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<User> users = (search != null && !search.isBlank())
                ? userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search.trim(), search.trim(), pageable)
                : userRepository.findAll(pageable);

        var results = users.getContent().stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());

        String next = users.hasNext()
                ? UriComponentsBuilder.fromPath("/api/admin/users")
                .queryParam("page", page + 1)
                .queryParam("page_size", pageSize)
                .queryParamIfPresent("search", java.util.Optional.ofNullable(search).filter(s -> !s.isBlank()))
                .build().toString()
                : null;
        return ResponseEntity.ok(new PaginatedResponse<>(results, next));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserDto> updateUserRole(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateUserRoleRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        String normalized = request.role().trim().toLowerCase(Locale.ROOT);
        if (!Roles.ADMIN.equalsIgnoreCase(normalized) && !Roles.USER.equalsIgnoreCase(normalized)) {
            return ResponseEntity.badRequest().build();
        }
        if (principal != null && principal.getId() != null && principal.getId().equals(user.getId())
                && !Roles.ADMIN.equalsIgnoreCase(normalized)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        user.setRole(normalized);
        User saved = userRepository.save(user);
        return ResponseEntity.ok(toUserDto(saved));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null && principal.getId() != null && principal.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private AdminPostDto toAdminPostDto(Post post) {
        return new AdminPostDto(
                post.getId(),
                post.getTitle(),
                post.getPostType(),
                post.getStatus(),
                post.getCreatedAt() != null ? post.getCreatedAt().toString() : null,
                UrlHelper.toAbsolute(post.getCoverUrl()),
                post.getAuthor() != null ? post.getAuthor().getId() : null,
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.getAuthor() != null ? post.getAuthor().getDisplayName() : null
        );
    }

    private UserDto toUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                UrlHelper.toAbsolute(user.getAvatarUrl()),
                user.getSubscribers().size(),
                user.getSubscriptions().size()
        );
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "draft";
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_STATUSES.contains(normalized) ? normalized : "draft";
    }

    private String generateSlug(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            return color;
        }
        String trimmed = color.trim();
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }
}
