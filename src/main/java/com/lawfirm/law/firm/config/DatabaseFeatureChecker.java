package com.lawfirm.law.firm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseFeatureChecker implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseFeatureChecker.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseFeatureChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.queryForObject("select unaccent('a'::text)", String.class);
            DatabaseFeatures.setUnaccentAvailable(true);
            log.info("Database feature: unaccent() available");
        } catch (Exception ex) {
            DatabaseFeatures.setUnaccentAvailable(false);
            log.info("Database feature: unaccent() NOT available - search will use fallback normalization");
        }
    }
}
