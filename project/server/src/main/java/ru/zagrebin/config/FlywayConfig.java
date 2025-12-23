package ru.zagrebin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {
    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);
    @Value("${app.flyway.auto-repair-enabled:true}")
    private boolean autoRepairEnabled;

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayValidateException ex) {
                if (!autoRepairEnabled) {
                    throw ex;
                }
                log.warn("Flyway validation failed ({}). Auto-repair is enabled; attempting repair before re-running migrations.", ex.getMessage());
                flyway.repair();
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
