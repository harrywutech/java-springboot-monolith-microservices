package com.skillup.application.performance;

import com.skillup.application.order.MqSendRepo;
import com.skillup.common.*;
import com.skillup.domain.order.OrderDomain;
import com.skillup.infrastructure.redis.PromotionCache;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationRelay {
  private final StringRedisTemplate redis;
  private final JsonCodec json;
  private final MqSendRepo mq;

  @Scheduled(fixedDelay = 500, initialDelay = 5000)
  public void drain() {
    try {
      for (Map.Entry<Object, Object> e :
          redis.opsForHash().entries(PromotionCache.PENDING).entrySet()) {
        OrderDomain o = json.read(e.getValue().toString(), OrderDomain.class);
        mq.send(Topics.create(Shard.of(o.getUserId())), e.getValue().toString(), 0);
        redis.opsForHash().delete(PromotionCache.PENDING, e.getKey());
      }
    } catch (Exception e) {
      log.warn("Reservation remains pending: {}", e.toString());
    }
  }
}
