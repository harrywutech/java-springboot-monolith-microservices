package com.skillup;

import com.skillup.application.order.OrderApplication;
import com.skillup.domain.order.*;
import com.skillup.domain.order.util.OrderStatus;
import com.skillup.domain.promotion.*;
import com.skillup.apiPresentation.util.SnowFlake;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real MySQL tests. Only rows with this test's generated IDs are removed. */
@org.springframework.context.annotation.Import(TestDatabaseConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties="spring.datasource.url=jdbc:mysql://127.0.0.1:13306/skillup_test?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true")
class MonolithIntegrationTest {
    @Autowired PromotionService promotions;
    @Autowired OrderApplication orders;
    @Autowired OrderService orderService;
    @Autowired SnowFlake ids;
    @Autowired DSLContext db;
    @Autowired TestRestTemplate http;
    String promotionId;
    String userId;

    @BeforeEach void fixture() {
        promotionId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        db.execute("insert into user(user_id,user_name,password) values(?,?,?)", userId, "test-" + userId, "fixture");
        promotions.createPromotion(PromotionDomain.builder().promotionId(promotionId)
            .promotionName("Integration fixture").commodityId("b859ee94-20f1-11ed-861d-0242ac120002")
            .originalPrice(100).promotionalPrice(80).startTime(LocalDateTime.now().minusDays(1))
            .endTime(LocalDateTime.now().plusDays(1)).status(1).totalStock(20L)
            .availableStock(20L).lockStock(0L).imageUrl("Iphone13").build());
    }
    @AfterEach void cleanup() {
        db.execute("delete from `order` where promotion_id = ?", promotionId);
        db.execute("delete from promotion where promotion_id = ?", promotionId);
        db.execute("delete from user where user_id = ?", userId);
    }
    OrderDomain order() {
        return OrderDomain.builder().orderNumber(ids.nextId()).userId(userId).promotionId(promotionId)
            .promotionName("Untrusted client name").orderAmount(1).orderStatus(OrderStatus.READY).build();
    }
    void stock(long available, long locked) {
        PromotionDomain p = promotions.getById(promotionId);
        assertThat(p.getAvailableStock()).isEqualTo(available);
        assertThat(p.getLockStock()).isEqualTo(locked);
        assertThat(p.getTotalStock()).isEqualTo(20L);
    }
    @Test void orderAndPaymentUseOneDatabaseAndServerPrice() {
        OrderDomain order = orders.createOrder(order());
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getOrderAmount()).isEqualTo(80);
        assertThat(order.getPromotionName()).isEqualTo("Integration fixture");
        stock(19, 1);
        assertThat(orders.payOrder(order.getOrderNumber()).getOrderStatus()).isEqualTo(OrderStatus.PAYED);
        stock(19, 0);
    }
    @Test void failedOrderInsertRollsBackReservedStock() {
        OrderDomain first = orders.createOrder(order());
        OrderDomain duplicate = order();
        duplicate.setOrderNumber(first.getOrderNumber());
        assertThatThrownBy(() -> orders.createOrder(duplicate)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        stock(19, 1);
        assertThat(db.fetchCount(db.selectFrom(org.jooq.impl.DSL.table("`order`")).where(org.jooq.impl.DSL.field("promotion_id").eq(promotionId)))).isEqualTo(1);
    }
    @Test void concurrentReservationCannotOversell() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i = 0; i < 80; i++) work.add(() -> promotions.lockStock(promotionId));
            int success = 0;
            for (Future<Boolean> result : pool.invokeAll(work)) if (result.get()) success++;
            assertThat(success).isEqualTo(20);
            stock(0, 20);
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentOrdersCannotOversell() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<OrderStatus>> work = new ArrayList<>();
            for (int i = 0; i < 50; i++) work.add(() -> orders.createOrder(order()).getOrderStatus());
            int success = 0;
            for (Future<OrderStatus> result : pool.invokeAll(work)) if (result.get() == OrderStatus.CREATED) success++;
            assertThat(success).isEqualTo(20);
            stock(0, 20);
            assertThat(db.fetchOne("select count(*) from `order` where promotion_id=?", promotionId).get(0, Integer.class)).isEqualTo(20);
        } finally { pool.shutdownNow(); }
    }
    @Test void repeatedConcurrentPaymentDeductsOnlyOnce() throws Exception {
        OrderDomain o = orders.createOrder(order());
        // A second reservation detects accidental double deduction.
        orders.createOrder(order());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<OrderDomain>> work = new ArrayList<>();
            for (int i = 0; i < 16; i++) work.add(() -> orders.payOrder(o.getOrderNumber()));
            for (Future<OrderDomain> result : pool.invokeAll(work)) assertThat(result.get().getOrderStatus()).isEqualTo(OrderStatus.PAYED);
            stock(18, 1);
        } finally { pool.shutdownNow(); }
    }
    @Test void paymentWithoutReservationRollsBackStatus() {
        OrderDomain o = orders.createOrder(order());
        assertThat(promotions.revertStock(promotionId)).isTrue();
        assertThatThrownBy(() -> orders.payOrder(o.getOrderNumber())).isInstanceOf(IllegalStateException.class);
        assertThat(orderService.getOrderById(o.getOrderNumber()).getOrderStatus()).isEqualTo(OrderStatus.CREATED);
        stock(20, 0);
    }
    @Test void revertAndDeductNeverMakeNegativeLockedStock() {
        assertThat(promotions.revertStock(promotionId)).isFalse();
        assertThat(promotions.deductStock(promotionId)).isFalse();
        assertThat(promotions.lockStock(promotionId)).isTrue();
        assertThat(promotions.revertStock(promotionId)).isTrue();
        stock(20, 0);
        assertThat(promotions.lockStock(promotionId)).isTrue();
        assertThat(promotions.deductStock(promotionId)).isTrue();
        assertThat(promotions.deductStock(promotionId)).isFalse();
        stock(19, 0);
    }
    @Test void missingAndExpiredPromotionsDoNotCreateOrders() {
        OrderDomain missing = order(); missing.setPromotionId(UUID.randomUUID().toString());
        assertThat(orders.createOrder(missing).getOrderStatus()).isEqualTo(OrderStatus.ITEM_ERROR);
        db.execute("update promotion set end_time = ? where promotion_id=?", LocalDateTime.now().minusHours(1), promotionId);
        assertThat(orders.createOrder(order()).getOrderStatus()).isEqualTo(OrderStatus.ITEM_ERROR);
        stock(20, 0);
    }
    @Test void httpOrderNumberIsAStringAndRoundTripsExactly() {
        Map<String,Object> body = new HashMap<>();
        body.put("promotionId",promotionId); body.put("userId",userId);
        ResponseEntity<Map> response = http.postForEntity("/order",body,Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object number = response.getBody().get("orderNumber");
        assertThat(number).isInstanceOf(String.class);
        assertThat(Long.parseLong((String)number)).isGreaterThan(9007199254740991L);
        Map fetched = http.getForObject("/order/id/" + number,Map.class);
        assertThat(fetched.get("orderNumber")).isEqualTo(number);
        assertThat(fetched.get("orderAmount")).isEqualTo(80);
        assertThat(http.getForEntity("/order/id/1",String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
