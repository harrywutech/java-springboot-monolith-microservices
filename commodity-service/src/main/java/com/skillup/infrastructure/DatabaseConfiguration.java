package com.skillup.infrastructure;

import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.*;

/** Each service account can access only its own schema; SQL uses its current connection schema. */
@Configuration
public class DatabaseConfiguration {
  @Bean
  public DefaultConfigurationCustomizer currentSchema() {
    return c -> c.settings().withRenderSchema(false);
  }
}
