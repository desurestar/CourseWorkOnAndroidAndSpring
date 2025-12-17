package ru.zagrebin.service.impl;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.zagrebin.dto.AuthRequest;
import ru.zagrebin.dto.AuthResponse;
import ru.zagrebin.dto.RegisterRequest;
import ru.zagrebin.dto.UpdateProfileRequest;
import ru.zagrebin.dto.UserDto;
import ru.zagrebin.model.User;
import ru.zagrebin.repository.UserRepository;
import ru.zagrebin.security.Roles;
import ru.zagrebin.security.JwtService;
import ru.zagrebin.service.AuthService;

import java.time.OffsetDateTime;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Пользователь с таким именем уже существует");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Roles.USER)
                .dateJoined(OffsetDateTime.now())
                .build();
        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);
        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(null)
                .expiresIn(jwtService.getExpirationMs())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> new BadCredentialsException("Неверный логин или пароль"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Неверный логин или пароль");
        }
        user.setLastLogin(OffsetDateTime.now());
        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(null)
                .expiresIn(jwtService.getExpirationMs())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return toDto(user);
    }

    @Override
    @Transactional
    public UserDto updateCurrentUser(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (request.getUsername() != null && !request.getUsername().isBlank()
                && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Пользователь с таким именем уже существует");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Пользователь с таким email уже существует");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getDisplayName() != null) {
            String display = request.getDisplayName().trim();
            user.setDisplayName(display.isEmpty() ? null : display);
        }

        if (request.getAvatarUrl() != null) {
            String avatar = request.getAvatarUrl().trim();
            user.setAvatarUrl(avatar.isEmpty() ? null : avatar);
        }

        User saved = userRepository.save(user);
        return toDto(saved);
    }

    private UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getAvatarUrl()
        );
    }
}
