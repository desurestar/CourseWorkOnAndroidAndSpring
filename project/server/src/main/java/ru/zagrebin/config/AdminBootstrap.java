package ru.zagrebin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.zagrebin.repository.UserRepository;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(adminPassword)) {
            return;
        }
        userRepository.findByUsername("admin").ifPresent(user -> {
            if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
                user.setPasswordHash(passwordEncoder.encode(adminPassword));
                userRepository.save(user);
            }
        });
    }
}
