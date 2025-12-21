package ru.zagrebin.controller;

import org.springframework.http.ResponseEntity;
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

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(
            @PathVariable("id") Long userId
    ) {
        return userRepository.findById(userId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/subscribe")
    public ResponseEntity<SubscriptionDto> subscribe(
            @PathVariable("id") Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        SubscriptionDto dto = subscriptionService.subscribe(principal.getId(), userId);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}/subscribe")
    public ResponseEntity<SubscriptionDto> unsubscribe(
            @PathVariable("id") Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        SubscriptionDto dto = subscriptionService.unsubscribe(principal.getId(), userId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}/subscription")
    public ResponseEntity<SubscriptionDto> getStatus(
            @PathVariable("id") Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        SubscriptionDto dto = subscriptionService.getStatus(principal.getId(), userId);
        return ResponseEntity.ok(dto);
    }

    private UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                UrlHelper.toAbsolute(user.getAvatarUrl())
        );
    }
}
