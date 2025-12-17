package ru.zagrebin.service;

import ru.zagrebin.dto.AuthRequest;
import ru.zagrebin.dto.AuthResponse;
import ru.zagrebin.dto.RegisterRequest;
import ru.zagrebin.dto.UserDto;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(AuthRequest request);
    UserDto getCurrentUser(Long userId);
}
