package com.skillup.application.performance;

import com.skillup.domain.order.*;
import com.skillup.domain.order.util.OrderStatus;
import com.skillup.infrastructure.redis.PromotionCache;
import com.skillup.infrastructure.repoImpl.JooqPromotionRepo;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StockMessageHandler {
  private final DSLContext db;
  private final JooqPromotionRepo stock;
  private final PromotionCache cache;

  @EventListener
  @Transactional
  public void handle(OrderMessageEvent event) {
    OrderDomain order = event.getOrder();
    String topic = event.getTopic();
    // Separate database: lock the promotion row, not an order row in another service's schema.
    if (db.fetchOne(
            "select promotion_id from promotion where promotion_id=? for update",
            order.getPromotionId())
        == null) throw new IllegalStateException("Unknown promotion");
    String operation;
    if (Topics.LOCK.equals(topic)) operation = "LOCK_STOCK";
    else if (Topics.REVERT.equals(topic)) operation = "REVERT_STOCK";
    else if (Topics.DEDUCT.equals(topic)) operation = "DEDUCT_STOCK";
    else throw new IllegalArgumentException("Unknown topic");
    if (logged(order.getOrderNumber(), operation)) return;
    if (!Topics.LOCK.equals(topic) && !logged(order.getOrderNumber(), "LOCK_STOCK"))
      throw new IllegalStateException("Own LOCK has not arrived; retry");
    if (!Topics.LOCK.equals(topic)
        && (logged(order.getOrderNumber(), "DEDUCT_STOCK")
            || logged(order.getOrderNumber(), "REVERT_STOCK")))
      throw new IllegalStateException("Conflicting terminal stock action; reconcile");
    boolean ok;
    if (Topics.LOCK.equals(topic)) ok = stock.lockStock(order.getPromotionId());
    else if (Topics.DEDUCT.equals(topic)) {
      if (order.getOrderStatus() != OrderStatus.PAYED)
        throw new IllegalStateException("Expected paid event");
      ok = stock.deductStock(order.getPromotionId());
    } else {
      if (order.getOrderStatus() != OrderStatus.OVERTIME)
        throw new IllegalStateException("Expected expired event");
      cache.release(order);
      ok = stock.revertStock(order.getPromotionId());
    }
    if (!ok) throw new IllegalStateException("Stock invariant failed; retry/reconcile");
    db.execute(
        "insert into promotion_log(order_number,user_id,promotion_id,operation_name,create_time)"
            + " values(?,?,?,?,?)",
        order.getOrderNumber(),
        order.getUserId(),
        order.getPromotionId(),
        operation,
        LocalDateTime.now());
  }

  private boolean logged(Long id, String operation) {
    return db.fetchOne(
                "select count(*) from promotion_log where order_number=? and operation_name=?",
                id,
                operation)
            .get(0, Integer.class)
        > 0;
  }
}
