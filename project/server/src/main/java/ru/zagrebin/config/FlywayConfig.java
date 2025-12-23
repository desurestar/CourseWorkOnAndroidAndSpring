package ru.zagrebin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {
    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);
    @Value("${app.flyway.auto-repair-enabled:false}")
    private boolean autoRepairEnabled;
    private final Environment environment;

    public FlywayConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayException ex) {
                if (!(ex instanceof FlywayValidateException)) {
                    throw ex;
                }
                boolean repairAllowed = autoRepairEnabled && !environment.acceptsProfiles(Profiles.of("prod"));
                if (!repairAllowed) {
                    throw ex;
                }
                log.warn("Flyway validation failed ({}). Auto-repair is enabled for non-production profiles; attempting repair before re-running migrations.",
                        ex.getClass().getSimpleName());
                flyway.repair();
                flyway.validate();
                try {
                    flyway.migrate();
                    log.info("Flyway repair completed successfully; migrations re-applied after validation error.");
                } catch (Exception migrateAfterRepairEx) {
                    log.error("Flyway migrate failed after repair attempt", migrateAfterRepairEx);
                    throw migrateAfterRepairEx;
                }
            }
        };
    }
}
