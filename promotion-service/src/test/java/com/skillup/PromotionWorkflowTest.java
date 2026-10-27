package com.skillup;

import static org.junit.jupiter.api.Assertions.*;

import com.skillup.application.order.MqSendRepo;
import com.skillup.application.performance.*;
import com.skillup.domain.order.*;
import com.skillup.domain.order.util.OrderStatus;
import com.skillup.domain.promotion.*;
import com.skillup.infrastructure.redis.PromotionCache;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.cloud.consul.enabled=false",
      "spring.datasource.url=jdbc:mysql://127.0.0.1:13308/step3_promotion_test?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC",
      "spring.datasource.username=step3_promotion_test",
      "spring.redis.database=15",
      "step3.mq.enabled=false"
    })
class PromotionWorkflowTest {
  @MockBean MqSendRepo mq;
  @MockBean ReservationRelay relay;
  @MockBean PromotionBootstrap bootstrap;
  @Autowired PromotionRepository repository;
  @Autowired PromotionCache cache;
  @Autowired ReservationApplication reservations;
  @Autowired StockMessageHandler handler;
  @Autowired DSLContext db;
  @Autowired StringRedisTemplate redis;
  String id;
  List<OrderDomain> orders;

  @BeforeEach
  void setup() {
    id = UUID.randomUUID().toString();
    orders = new ArrayList<>();
    repository.createPromotion(
        PromotionDomain.builder()
            .promotionId(id)
            .promotionName("Step3 test")
            .commodityId("test")
            .originalPrice(100)
            .promotionalPrice(80)
            .startTime(LocalDateTime.now().minusDays(1))
            .endTime(LocalDateTime.now().plusDays(1))
            .status(1)
            .totalStock(5L)
            .availableStock(5L)
            .lockStock(0L)
            .imageUrl("Iphone13")
            .build());
    cache.preheat(id);
  }

  @AfterEach
  void cleanup() {
    db.execute("delete from promotion_log where promotion_id=?", id);
    db.execute("delete from promotion where promotion_id=?", id);
    redis.delete(Arrays.asList(PromotionCache.stockKey(id), PromotionCache.infoKey(id)));
    for (OrderDomain o : orders) {
      redis.delete(
          Arrays.asList(
              PromotionCache.stateKey(id, o.getOrderNumber()), PromotionCache.snapshotKey(o)));
      redis.opsForHash().delete(PromotionCache.PENDING, String.valueOf(o.getOrderNumber()));
    }
  }

  OrderDomain order(long number) {
    OrderDomain o =
        OrderDomain.builder()
            .orderNumber(number)
            .userId("1")
            .promotionId(id)
            .promotionName("tampered")
            .orderAmount(1)
            .orderStatus(OrderStatus.READY)
            .build();
    orders.add(o);
    return o;
  }

  void event(String topic, OrderDomain o) {
    handler.handle(new OrderMessageEvent(topic, o));
  }

  int logs() {
    return db.fetchOne("select count(*) from promotion_log where promotion_id=?", id)
        .get(0, Integer.class);
  }

  @Test
  void duplicateRemoteReservationKeepsSnapshotAndStock() {
    OrderDomain o = order(1);
    OrderDomain first = reservations.reserve(o);
    assertEquals(80, first.getOrderAmount());
    assertEquals(OrderStatus.READY, first.getOrderStatus());
    assertEquals(first, reservations.reserve(o));
    assertEquals("4", redis.opsForValue().get(PromotionCache.stockKey(id)));
    assertFalse(cache.preheat(id));
    assertEquals("4", redis.opsForValue().get(PromotionCache.stockKey(id)));
  }

  @Test
  void concurrentReservationsCannotOversell() throws Exception {
    List<OrderDomain> attempts = new ArrayList<>();
    for (int i = 0; i < 30; i++) attempts.add(order(100 + i));
    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      List<Future<OrderDomain>> results = new ArrayList<>();
      for (OrderDomain o : attempts) results.add(pool.submit(() -> reservations.reserve(o)));
      int accepted = 0;
      for (Future<OrderDomain> f : results)
        if (f.get().getOrderStatus() == OrderStatus.READY) accepted++;
      assertEquals(5, accepted);
      assertEquals("0", redis.opsForValue().get(PromotionCache.stockKey(id)));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void duplicateStockEventsHaveOneEffect() {
    OrderDomain o = reservations.reserve(order(2));
    o.setOrderStatus(OrderStatus.CREATED);
    event(Topics.LOCK, o);
    event(Topics.LOCK, o);
    o.setOrderStatus(OrderStatus.PAYED);
    event(Topics.DEDUCT, o);
    event(Topics.DEDUCT, o);
    assertEquals(2, logs());
    PromotionDomain p = repository.getById(id);
    assertEquals(4L, p.getAvailableStock());
    assertEquals(0L, p.getLockStock());
  }

  @Test
  void outOfOrderTerminalRetriesUntilOwnLock() {
    OrderDomain o = reservations.reserve(order(3));
    o.setOrderStatus(OrderStatus.PAYED);
    assertThrows(IllegalStateException.class, () -> event(Topics.DEDUCT, o));
    assertEquals(0, logs());
    event(Topics.LOCK, o);
    event(Topics.DEDUCT, o);
    assertEquals(2, logs());
  }

  @Test
  void releaseIsIdempotentAndCannotReserveAgain() {
    OrderDomain o = reservations.reserve(order(4));
    event(Topics.LOCK, o);
    o.setOrderStatus(OrderStatus.OVERTIME);
    event(Topics.REVERT, o);
    event(Topics.REVERT, o);
    assertEquals("5", redis.opsForValue().get(PromotionCache.stockKey(id)));
    assertEquals(5L, repository.getById(id).getAvailableStock());
    assertEquals(-3, cache.reserve(o));
    assertEquals(2, logs());
  }

  @Test
  void failedStockUpdateRollsBackLog() {
    OrderDomain o = reservations.reserve(order(5));
    db.execute("update promotion set available_stock=0 where promotion_id=?", id);
    assertThrows(IllegalStateException.class, () -> event(Topics.LOCK, o));
    assertEquals(0, logs());
    db.execute("update promotion set available_stock=5 where promotion_id=?", id);
    event(Topics.LOCK, o);
    assertEquals(1, logs());
  }

  @Test
  void conflictingTerminalEventDoesNotInflateStock() {
    OrderDomain o = reservations.reserve(order(6));
    event(Topics.LOCK, o);
    o.setOrderStatus(OrderStatus.PAYED);
    event(Topics.DEDUCT, o);
    o.setOrderStatus(OrderStatus.OVERTIME);
    assertThrows(IllegalStateException.class, () -> event(Topics.REVERT, o));
    assertEquals("4", redis.opsForValue().get(PromotionCache.stockKey(id)));
    assertEquals(2, logs());
  }
}
