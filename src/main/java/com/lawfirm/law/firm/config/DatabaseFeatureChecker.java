package com.lawfirm.law.firm.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseFeatureChecker implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseFeatureChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.queryForObject("select unaccent('a'::text)", String.class);
            DatabaseFeatures.setUnaccentAvailable(true);
            System.out.println("Database feature: unaccent() available");
        } catch (Exception ex) {
            DatabaseFeatures.setUnaccentAvailable(false);
            System.out.println("Database feature: unaccent() NOT available - search will use fallback normalization (accent-insensitive on JVM side only)");
        }
    }
}
