package com.skillup;

import com.skillup.application.performance.*;
import com.skillup.application.order.MqSendRepo;
import com.skillup.domain.order.*;
import com.skillup.domain.order.util.OrderStatus;
import com.skillup.domain.promotion.*;
import com.skillup.infrastructure.redis.PromotionCache;
import com.skillup.apiPresentation.util.SnowFlake;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL + Redis. MQ is mocked only here for deterministic failure injection.
 * scripts/smoke-step2.py separately validates the real RocketMQ path. */
@ActiveProfiles("step2")
@org.springframework.context.annotation.Import(TestDatabaseConfiguration.class)
@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://127.0.0.1:13307/skillup_test?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true","spring.redis.database=15","step2.messaging-enabled=false","step2.relay-enabled=false","step2.pay-timeout-seconds=1"})
class Step2IntegrationTest {
    @Autowired Step2OrderApplication app;
    @Autowired OrderMessageHandler handler;
    @Autowired MessageRelay relay;
    @Autowired PromotionCache cache;
    @Autowired PromotionService promotions;
    @Autowired OrderService orders;
    @Autowired SnowFlake ids;
    @Autowired DSLContext db;
    @Autowired StringRedisTemplate redis;
    @MockBean MqSendRepo mq;
    String promotion;
    List<Long> orderIds;
    @BeforeEach void setup() {
        orderIds=Collections.synchronizedList(new ArrayList<>());
        promotion=UUID.randomUUID().toString();
        promotions.createPromotion(PromotionDomain.builder().promotionId(promotion).promotionName("Step2 fixture")
            .commodityId("b859ee94-20f1-11ed-861d-0242ac120002").originalPrice(100).promotionalPrice(80)
            .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(1))
            .status(1).totalStock(20L).availableStock(20L).lockStock(0L).imageUrl("Iphone13").build());
        cache.preheat(promotion);
    }
    @AfterEach void cleanup() {
        for(Long id:orderIds) {
            redis.opsForHash().delete(PromotionCache.PENDING,id.toString());
            redis.delete(PromotionCache.stateKey(promotion,id));
            db.execute("delete from mq_outbox where event_key like ?","%:"+id);
            db.execute("delete from promotion_log where order_number=?",id);
            db.execute("delete from `order` where order_number=?",id);
        }
        redis.delete(Arrays.asList(PromotionCache.stockKey(promotion),PromotionCache.infoKey(promotion)));
        db.execute("delete from promotion where promotion_id=?",promotion);
    }
    OrderDomain fresh() {
        long id=ids.nextId();orderIds.add(id);
        return OrderDomain.builder().orderNumber(id).userId("1").promotionId(promotion)
            .promotionName("client tampering").orderAmount(1).orderStatus(OrderStatus.READY).build();
    }
    void consume(String topic,OrderDomain o) {handler.handle(new OrderMessageEvent(topic,o));}
    OrderDomain created() {
        OrderDomain o=app.createOrder(fresh());consume(Topics.CREATE,o);consume(Topics.LOCK,o);return o;
    }
    int logs(OrderDomain o) {return db.fetchOne("select count(*) from promotion_log where order_number=?",o.getOrderNumber()).get(0,Integer.class);}
    void stock(long available,long locked) {
        PromotionDomain p=promotions.getById(promotion);
        assertThat(p.getAvailableStock()).isEqualTo(available);assertThat(p.getLockStock()).isEqualTo(locked);
    }
    void expire(OrderDomain o) {
        db.execute("update `order` set create_time=? where order_number=?",LocalDateTime.now().minusMinutes(2),o.getOrderNumber());
        consume(Topics.CHECK,o);
    }
    @Test void cacheAsideAndPreheatNeverResetActiveStock() {
        app.createOrder(fresh());
        assertThat(cache.preheat(promotion)).isFalse();
        redis.delete(PromotionCache.infoKey(promotion));
        assertThat(cache.get(promotion).getAvailableStock()).isEqualTo(19L);
        assertThat(redis.getExpire(PromotionCache.infoKey(promotion))).isBetween(1L,300L);
        assertThat(redis.getExpire(PromotionCache.stockKey(promotion))).isEqualTo(-1);
    }
    @Test void concurrentLuaAdmissionsCannotOversell() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(12);
        try {
            List<Callable<OrderStatus>> jobs=new ArrayList<>();
            for(int i=0;i<80;i++) jobs.add(()->app.createOrder(fresh()).getOrderStatus());
            int accepted=0;
            for(Future<OrderStatus> f:pool.invokeAll(jobs)) if(f.get()==OrderStatus.READY) accepted++;
            assertThat(accepted).isEqualTo(20);
            assertThat(cache.get(promotion).getAvailableStock()).isZero();stock(20,0);
        } finally {pool.shutdownNow();}
    }
    @Test void luaDuplicateAndReorderedReleaseDoNotInflateStock() {
        OrderDomain o=fresh();
        assertThatThrownBy(()->cache.release(o)).isInstanceOf(IllegalStateException.class);
        assertThat(cache.reserve(o)).isEqualTo(1);assertThat(cache.reserve(o)).isZero();
        cache.release(o);cache.release(o);
        assertThat(cache.reserve(o)).isEqualTo(-3);
        assertThat(cache.get(promotion).getAvailableStock()).isEqualTo(20);
    }
    @Test void missingStockFailsClosedAndDoesNotCreateIntent() {
        redis.delete(PromotionCache.stockKey(promotion));OrderDomain o=fresh();
        assertThatThrownBy(()->app.createOrder(o)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(redis.opsForHash().hasKey(PromotionCache.PENDING,o.getOrderNumber().toString())).isFalse();
    }
    @Test void asyncAdmissionUsesServerPriceAndOnlyWritesRedis() {
        OrderDomain o=app.createOrder(fresh());assertThat(o.getOrderAmount()).isEqualTo(80);
        assertThat(o.getOrderStatus()).isEqualTo(OrderStatus.READY);
        assertThat(orders.getOrderById(o.getOrderNumber())).isNull();stock(20,0);
        assertThat(redis.opsForHash().hasKey(PromotionCache.PENDING,o.getOrderNumber().toString())).isTrue();
    }
    @Test void repeatedMqMessagesOnlyCreateOneOrderAndOneLockLog() throws Exception {
        OrderDomain o=app.createOrder(fresh());
        ExecutorService pool=Executors.newFixedThreadPool(5);
        try {
            List<Callable<Void>> jobs=new ArrayList<>();
            for(int i=0;i<10;i++)jobs.add(()->{consume(Topics.CREATE,o);consume(Topics.LOCK,o);return null;});
            for(Future<Void> f:pool.invokeAll(jobs)) f.get();
        } finally {pool.shutdownNow();}
        stock(19,1);assertThat(logs(o)).isEqualTo(1);
        assertThat(db.fetchOne("select count(*) from mq_outbox where event_key like ?","%:"+o.getOrderNumber()).get(0,Integer.class)).isEqualTo(2);
    }
    @Test void paymentAndTimeoutRaceHasOnlyOneTerminalStockEffect() throws Exception {
        OrderDomain o=created();db.execute("update `order` set create_time=? where order_number=?",LocalDateTime.now().minusMinutes(2),o.getOrderNumber());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> pay=pool.submit(()->app.payOrder(o.getOrderNumber()));
            Future<?> timeout=pool.submit(()->consume(Topics.CHECK,o));pay.get();timeout.get();
        } finally {pool.shutdownNow();}
        OrderStatus result=orders.getOrderById(o.getOrderNumber()).getOrderStatus();
        if(result==OrderStatus.PAYED) {consume(Topics.DEDUCT,o);consume(Topics.DEDUCT,o);stock(19,0);}
        else {assertThat(result).isEqualTo(OrderStatus.OVERTIME);consume(Topics.REVERT,o);consume(Topics.REVERT,o);stock(20,0);}
        assertThat(logs(o)).isEqualTo(2);
    }
    @Test void expiredOrderReturnsRedisAndMysqlStockOnlyOnceAndCannotPay() {
        OrderDomain o=created();expire(o);consume(Topics.REVERT,o);consume(Topics.REVERT,o);
        assertThat(app.payOrder(o.getOrderNumber()).getOrderStatus()).isEqualTo(OrderStatus.OVERTIME);
        stock(20,0);assertThat(cache.get(promotion).getAvailableStock()).isEqualTo(20L);assertThat(logs(o)).isEqualTo(2);
    }
    @Test void outOfOrderDeductRetriesUntilItsOwnReservationExists() {
        OrderDomain o=app.createOrder(fresh());consume(Topics.CREATE,o);app.payOrder(o.getOrderNumber());
        assertThatThrownBy(()->consume(Topics.DEDUCT,o)).isInstanceOf(IllegalStateException.class);
        stock(20,0);consume(Topics.LOCK,o);consume(Topics.DEDUCT,o);stock(19,0);
    }
    @Test void failedStockMutationRollsBackItsIdempotencyLog() {
        OrderDomain o=app.createOrder(fresh());consume(Topics.CREATE,o);
        db.execute("update promotion set available_stock=0 where promotion_id=?",promotion);
        assertThatThrownBy(()->consume(Topics.LOCK,o)).isInstanceOf(IllegalStateException.class);
        assertThat(logs(o)).isZero();
        db.execute("update promotion set available_stock=20 where promotion_id=?",promotion);
        consume(Topics.LOCK,o);stock(19,1);assertThat(logs(o)).isEqualTo(1);
    }
    @Test void redisIntentSurvivesSendFailureThenRetries() {
        OrderDomain o=app.createOrder(fresh());doThrow(new IllegalStateException("broker offline")).when(mq).send(anyString(),anyString(),anyInt());
        assertThatThrownBy(()->relay.drain()).isInstanceOf(IllegalStateException.class);
        assertThat(redis.opsForHash().hasKey(PromotionCache.PENDING,o.getOrderNumber().toString())).isTrue();
        reset(mq);relay.drain();
        assertThat(redis.opsForHash().hasKey(PromotionCache.PENDING,o.getOrderNumber().toString())).isFalse();
        verify(mq).send(eq(Topics.CREATE),anyString(),eq(0));
    }
    @Test void databaseOutboxSurvivesSendFailureThenRetries() {
        OrderDomain o=created();redis.opsForHash().delete(PromotionCache.PENDING,o.getOrderNumber().toString());
        doThrow(new IllegalStateException("broker offline")).when(mq).send(anyString(),anyString(),anyInt());
        assertThatThrownBy(()->relay.drain()).isInstanceOf(IllegalStateException.class);
        assertThat(db.fetchOne("select count(*) from mq_outbox where event_key like ? and sent_at is null","%:"+o.getOrderNumber()).get(0,Integer.class)).isEqualTo(2);
        reset(mq);relay.drain();
        assertThat(db.fetchOne("select count(*) from mq_outbox where event_key like ? and sent_at is null","%:"+o.getOrderNumber()).get(0,Integer.class)).isZero();
    }
}
