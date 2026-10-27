package com.skillup;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
/** Generated jOOQ classes name the demo schema. Tests use their connection's isolated schema. */
@TestConfiguration
public class TestDatabaseConfiguration {
    @Bean DefaultConfigurationCustomizer testSchema() {
        return configuration -> configuration.settings().withRenderSchema(false);
    }
}
