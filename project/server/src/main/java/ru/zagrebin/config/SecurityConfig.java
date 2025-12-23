package ru.zagrebin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.zagrebin.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of("http://localhost:3000", "http://localhost:5173");
    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With",
            "Cache-Control", "X-HTTP-Method-Override"
    );

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/media/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/posts/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/tags/**", "/api/ingredients/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> originsToUse = resolveAllowedOrigins();
        boolean hasPattern = originsToUse.stream().anyMatch(this::isWildcardPattern);
        boolean hasSpecific = originsToUse.stream().anyMatch(origin -> !isWildcardPattern(origin));
        if (hasPattern && hasSpecific) {
            throw new IllegalArgumentException(
                    "Cannot mix wildcard patterns (e.g., *.example.com) with specific origins in app.cors.allowed-origins: " + originsToUse);
        }

        // For CORS security reasons, credentials must be disabled when wildcard origins are used.
        boolean allowCredentials = !hasPattern;

        if (hasPattern) {
            configuration.setAllowedOriginPatterns(originsToUse);
        } else {
            configuration.setAllowedOrigins(originsToUse);
        }
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setAllowCredentials(allowCredentials);
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private boolean isWildcardPattern(String origin) {
        if (origin == null) {
            return false;
        }
        return "*".equals(origin) || origin.startsWith("*.") || origin.startsWith("http://*.") || origin.startsWith("https://*.");
    }

    private List<String> resolveAllowedOrigins() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return DEFAULT_ALLOWED_ORIGINS;
        }
        List<String> origins = parseOrigins(allowedOrigins);
        return origins.isEmpty() ? DEFAULT_ALLOWED_ORIGINS : origins;
    }

    private List<String> parseOrigins(String rawOrigins) {
        return Arrays.stream(rawOrigins.split("\\s*,\\s*"))
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
