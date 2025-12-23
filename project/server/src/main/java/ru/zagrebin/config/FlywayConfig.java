package ru.zagrebin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;

@Configuration
public class FlywayConfig {
    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);
    /**
     * Enables automatic Flyway repair on validation errors (e.g., checksum mismatches).
     * Use cautiously because it can hide migration problems if run in production.
     */
    @Value("${app.flyway.auto-repair-enabled:false}")
    private boolean autoRepairEnabled;
    /**
     * Comma-separated list of production profile names that must not attempt automatic repair.
     */
    @Value("${app.flyway.prod-profiles:prod}")
    private String prodProfiles;
    private final Environment environment;
    private Profiles productionProfiles;

    public FlywayConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void initProductionProfiles() {
        String[] profiles = Arrays.stream(prodProfiles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        productionProfiles = Profiles.of(profiles);
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                runMigrations(flyway);
            } catch (FlywayValidateException ex) {
                boolean repairAllowed = autoRepairEnabled && !environment.acceptsProfiles(productionProfiles);
                if (!repairAllowed) {
                    if (log.isInfoEnabled()) {
                        log.info("Flyway auto-repair skipped (enabled: {}, production profiles active: {}).", autoRepairEnabled,
                                environment.acceptsProfiles(productionProfiles));
                    }
                    throw ex;
                }
                log.warn("Flyway validation failed ({}). Auto-repair is enabled for non-production profiles; attempting repair before re-running migrations.",
                        ex.getMessage());
                flyway.repair();
                try {
                    runMigrations(flyway);
                    log.info("Flyway repair completed successfully; migrations re-applied after validation error.");
                } catch (FlywayValidateException validationAfterRepairEx) {
                    log.error("Flyway validation failed after repair attempt; underlying issue may persist", validationAfterRepairEx);
                    throw validationAfterRepairEx;
                }
            }
        };
    }

    private void runMigrations(Flyway flyway) {
        flyway.validate();
        flyway.migrate();
    }
}
