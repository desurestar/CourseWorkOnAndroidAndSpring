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
import ru.zagrebin.dto.AdminUserDto;
import ru.zagrebin.dto.CreateIngredientRequest;
import ru.zagrebin.dto.CreateTagRequest;
import ru.zagrebin.dto.IngredientDto;
import ru.zagrebin.dto.PaginatedResponse;
import ru.zagrebin.dto.TagDto;
import ru.zagrebin.dto.UpdatePostStatusRequest;
import ru.zagrebin.dto.UpdateUserRoleRequest;
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private static final Set<String> ALLOWED_POST_STATUSES = Set.of("draft", "published", "archived");
    private static final Set<String> ALLOWED_ROLES = Set.of(Roles.ADMIN, Roles.USER);
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
    public ResponseEntity<PaginatedResponse<AdminPostDto>> listPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search
    ) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> posts = (search != null && !search.isBlank())
                ? postRepository.findByTitleContainingIgnoreCaseOrAuthor_UsernameContainingIgnoreCase(search, search, pageable)
                : postRepository.findAll(pageable);

        List<AdminPostDto> results = posts.stream()
                .map(this::toAdminPostDto)
                .collect(Collectors.toList());

        String next = null;
        if (posts.hasNext()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/admin/posts")
                    .queryParam("page", page + 1)
                    .queryParam("page_size", pageSize);
            if (search != null && !search.isBlank()) {
                builder.queryParam("search", search);
            }
            next = builder.build().toString();
        }

        return ResponseEntity.ok(new PaginatedResponse<>(results, next));
    }

    @PutMapping("/posts/{id}/status")
    public ResponseEntity<AdminPostDto> updatePostStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostStatusRequest request
    ) {
        String status = request.getStatus() == null ? null : request.getStatus().trim().toLowerCase();
        if (status == null || !ALLOWED_POST_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + id));
        post.setStatus(status);
        Post saved = postRepository.save(post);
        return ResponseEntity.ok(toAdminPostDto(saved));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        postService.delete(id, principal != null ? principal.getId() : null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ingredients")
    public ResponseEntity<PaginatedResponse<IngredientDto>> listIngredients(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
            @RequestParam(required = false) String search
    ) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        var ingredients = (search != null && !search.isBlank())
                ? ingredientRepository.findByNameContainingIgnoreCase(search, pageable)
                : ingredientRepository.findAll(pageable);
        List<IngredientDto> results = ingredients.getContent().stream()
                .map(IngredientMapper::toDto)
                .collect(Collectors.toList());

        String next = null;
        if (ingredients.hasNext()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/admin/ingredients")
                    .queryParam("page", page + 1)
                    .queryParam("page_size", pageSize);
            if (search != null && !search.isBlank()) {
                builder.queryParam("search", search);
            }
            next = builder.build().toString();
        }
        return ResponseEntity.ok(new PaginatedResponse<>(results, next));
    }

    @PostMapping("/ingredients")
    public ResponseEntity<IngredientDto> createIngredient(@Valid @RequestBody CreateIngredientRequest request) {
        String name = request.getName().trim();
        if (ingredientRepository.findByName(name).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        Ingredient ingredient = Ingredient.builder().name(name).build();
        Ingredient saved = ingredientRepository.save(ingredient);
        return ResponseEntity.status(HttpStatus.CREATED).body(IngredientMapper.toDto(saved));
    }

    @DeleteMapping("/ingredients/{id}")
    public ResponseEntity<Void> deleteIngredient(@PathVariable Long id) {
        if (!ingredientRepository.existsById(id)) {
            throw new EntityNotFoundException("Ingredient not found: " + id);
        }
        ingredientRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tags")
    public ResponseEntity<PaginatedResponse<TagDto>> listTags(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "16") int pageSize,
            @RequestParam(required = false) String search
    ) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        var tags = (search != null && !search.isBlank())
                ? tagRepository.findByNameContainingIgnoreCase(search, pageable)
                : tagRepository.findAll(pageable);
        List<TagDto> results = tags.getContent().stream()
                .map(TagMapper::toDto)
                .collect(Collectors.toList());

        String next = null;
        if (tags.hasNext()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/admin/tags")
                    .queryParam("page", page + 1)
                    .queryParam("page_size", pageSize);
            if (search != null && !search.isBlank()) {
                builder.queryParam("search", search);
            }
            next = builder.build().toString();
        }
        return ResponseEntity.ok(new PaginatedResponse<>(results, next));
    }

    @PostMapping("/tags")
    public ResponseEntity<TagDto> createTag(@Valid @RequestBody CreateTagRequest request) {
        String name = request.getName().trim();
        if (tagRepository.findByName(name).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        String slug = slugify(name, request.getSlug());
        String color = request.getColor();
        Tag tag = Tag.builder()
                .name(name)
                .slug(slug)
                .color(color)
                .build();
        Tag saved = tagRepository.save(tag);
        return ResponseEntity.status(HttpStatus.CREATED).body(TagMapper.toDto(saved));
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        if (!tagRepository.existsById(id)) {
            throw new EntityNotFoundException("Tag not found: " + id);
        }
        tagRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public ResponseEntity<PaginatedResponse<AdminUserDto>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search
    ) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<User> users = (search != null && !search.isBlank())
                ? userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable)
                : userRepository.findAll(pageable);

        List<AdminUserDto> results = users.getContent().stream()
                .map(this::toAdminUserDto)
                .collect(Collectors.toList());

        String next = null;
        if (users.hasNext()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/admin/users")
                    .queryParam("page", page + 1)
                    .queryParam("page_size", pageSize);
            if (search != null && !search.isBlank()) {
                builder.queryParam("search", search);
            }
            next = builder.build().toString();
        }
        return ResponseEntity.ok(new PaginatedResponse<>(results, next));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<AdminUserDto> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        String role = request.getRole() == null ? null : request.getRole().trim().toLowerCase();
        if (role == null || !ALLOWED_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        user.setRole(role);
        User saved = userRepository.save(user);
        return ResponseEntity.ok(toAdminUserDto(saved));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private AdminPostDto toAdminPostDto(Post post) {
        AdminPostDto dto = new AdminPostDto();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setStatus(post.getStatus());
        dto.setPostType(post.getPostType());
        dto.setAuthorId(post.getAuthor() != null ? post.getAuthor().getId() : null);
        dto.setAuthorUsername(post.getAuthor() != null ? post.getAuthor().getUsername() : null);
        dto.setCreatedAt(post.getCreatedAt());
        dto.setUpdatedAt(post.getUpdatedAt());
        return dto;
    }

    private String slugify(String name, String explicit) {
        String source = explicit != null && !explicit.isBlank() ? explicit : name;
        if (source == null) return null;
        String normalized = source.toLowerCase()
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-{2,}", "-");
        return normalized.isBlank() ? source.toLowerCase().trim() : normalized;
    }

    private AdminUserDto toAdminUserDto(User user) {
        return new AdminUserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                UrlHelper.toAbsolute(user.getAvatarUrl())
        );
    }
}
