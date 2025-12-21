package ru.zagrebin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.zagrebin.dto.SubscriptionDto;
import ru.zagrebin.dto.UserDto;
import ru.zagrebin.model.User;
import ru.zagrebin.repository.UserRepository;
import ru.zagrebin.security.UserPrincipal;
import ru.zagrebin.service.SubscriptionService;
import ru.zagrebin.util.UrlHelper;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public UserController(SubscriptionService subscriptionService,
                          UserRepository userRepository) {
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(
            @PathVariable("id") Long userId
    ) {
        return userRepository.findById(userId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/subscribe")
    public ResponseEntity<SubscriptionDto> subscribe(
            @PathVariable("id") Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SubscriptionDto dto = subscriptionService.subscribe(requirePrincipal(principal), userId);
        return ResponseEntity.ok(dto);
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/subscribe")
    public ResponseEntity<SubscriptionDto> unsubscribe(
            @PathVariable("id") Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SubscriptionDto dto = subscriptionService.unsubscribe(requirePrincipal(principal), userId);
        return ResponseEntity.ok(dto);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/subscription")
    public ResponseEntity<SubscriptionDto> getStatus(
            @PathVariable("id") Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SubscriptionDto dto = subscriptionService.getStatus(requirePrincipal(principal), userId);
        return ResponseEntity.ok(dto);
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}/followers")
    public ResponseEntity<List<UserDto>> getFollowers(
            @PathVariable("id") Long userId
    ) {
        return userRepository.findById(userId)
                .map(user -> user.getSubscribers().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}/following")
    public ResponseEntity<List<UserDto>> getFollowing(
            @PathVariable("id") Long userId
    ) {
        return userRepository.findById(userId)
                .map(user -> user.getSubscriptions().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private UserDto toDto(User user) {
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

    private Long requirePrincipal(UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Unauthorized");
        }
        return principal.getId();
    }
}
