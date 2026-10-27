package com.skillup.application.performance;

import com.skillup.domain.promotion.PromotionRepository;
import com.skillup.infrastructure.redis.PromotionCache;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class PromotionBootstrap {
  private final PromotionRepository promotions;
  private final PromotionCache cache;

  @Bean
  ApplicationRunner warmPromotions() {
    return args -> promotions.getByStatus(1).forEach(p -> cache.preheat(p.getPromotionId()));
  }
}
