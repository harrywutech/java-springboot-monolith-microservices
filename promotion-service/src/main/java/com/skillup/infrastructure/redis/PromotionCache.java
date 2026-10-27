package com.skillup.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillup.domain.order.OrderDomain;
import com.skillup.domain.promotion.*;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Metadata expires; live admission stock must never expire or be reset by a cache miss. */
@Repository
@RequiredArgsConstructor
public class PromotionCache {
  public static final String PREFIX = "ecomm:step3:", PENDING = PREFIX + "pending-orders";
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final PromotionRepository promotions;

  @Value("${step3.cache-ttl-seconds:300}")
  private long ttl;

  private final DefaultRedisScript<Long> reserve = script("reserve.lua");
  private final DefaultRedisScript<Long> release = script("release.lua");

  private static DefaultRedisScript<Long> script(String name) {
    DefaultRedisScript<Long> s = new DefaultRedisScript<>();
    s.setLocation(new ClassPathResource("redis/" + name));
    s.setResultType(Long.class);
    return s;
  }

  public static String stockKey(String id) {
    return PREFIX + "stock:" + id;
  }

  public static String stateKey(String promotion, Long order) {
    return PREFIX + "reservation:" + promotion + ":" + order;
  }

  public static String infoKey(String id) {
    return PREFIX + "promotion:" + id;
  }

  public PromotionDomain get(String id) {
    String value = redis.opsForValue().get(infoKey(id));
    PromotionDomain p;
    if (value != null) p = read(value, PromotionDomain.class);
    else {
      p = promotions.getById(id);
      if (p == null) return null;
      redis.opsForValue().set(infoKey(id), write(p), Duration.ofSeconds(ttl));
    }
    String stock = redis.opsForValue().get(stockKey(id));
    if (stock != null) p.setAvailableStock(Long.valueOf(stock));
    return p;
  }

  public boolean preheat(String id) {
    PromotionDomain p = promotions.getById(id);
    if (p == null) return false;
    redis.opsForValue().set(infoKey(id), write(p), Duration.ofSeconds(ttl));
    // Repeated warming must not overwrite reservations awaiting MQ consumption.
    return Boolean.TRUE.equals(
        redis.opsForValue().setIfAbsent(stockKey(id), String.valueOf(p.getAvailableStock())));
  }

  public long reserve(OrderDomain order) {
    Long result =
        redis.execute(
            reserve,
            Arrays.asList(
                stockKey(order.getPromotionId()),
                stateKey(order.getPromotionId(), order.getOrderNumber()),
                PENDING,
                snapshotKey(order)),
            String.valueOf(order.getOrderNumber()),
            write(order));
    if (result == null) throw new IllegalStateException("Redis reservation returned no result");
    return result;
  }

  public static String snapshotKey(OrderDomain o) {
    return stateKey(o.getPromotionId(), o.getOrderNumber()) + ":data";
  }

  public OrderDomain snapshot(OrderDomain o) {
    return read(redis.opsForValue().get(snapshotKey(o)), OrderDomain.class);
  }

  public void release(OrderDomain order) {
    Long result =
        redis.execute(
            release,
            Arrays.asList(
                stockKey(order.getPromotionId()),
                stateKey(order.getPromotionId(), order.getOrderNumber())));
    if (result == null || result < 0)
      throw new IllegalStateException("Missing Redis reservation; reconciliation required");
  }

  public String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public <T> T read(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
