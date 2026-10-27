package com.skillup.application.performance;
import com.skillup.domain.promotion.PromotionRepository;
import com.skillup.infrastructure.redis.PromotionCache;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.ApplicationRunner;
@Configuration @Profile("step2") @EnableScheduling @RequiredArgsConstructor
public class Step2Bootstrap {
    private final PromotionRepository promotions;
    private final PromotionCache cache;
    @Bean ApplicationRunner warmPromotions() {
        return args->promotions.getByStatus(1).forEach(p->cache.preheat(p.getPromotionId()));
    }
}
