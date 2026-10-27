package com.skillup.application.performance;

import com.skillup.domain.order.*;
import com.skillup.domain.order.util.OrderStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderMessageHandler {
  private final OrderService orders;

  private final MessageOutbox outbox;

  private final DSLContext db;

  @Value("${step3.pay-delay-level:5}")
  private int delayLevel;

  @Value("${step3.pay-timeout-seconds:60}")
  private int timeoutSeconds;

  @Value("${step3.shard}")
  private int shard;

  @EventListener
  @Transactional
  public void handle(OrderMessageEvent event) {
    OrderDomain incoming = event.getOrder();
    String topic = event.getTopic();
    if (com.skillup.common.Shard.of(incoming.getUserId()) != shard)
      throw new IllegalStateException("Message delivered to the wrong shard");
    if (Topics.create(shard).equals(topic)) {
      // Unique PK arbitrates concurrent delivery. Follow-up events commit with the order.
      db.execute(
          "insert into"
              + " `order`(order_number,order_status,promotion_id,promotion_name,user_id,order_amount,create_time)"
              + " values(?,1,?,?,?,?,?) on duplicate key update order_number=order_number",
          incoming.getOrderNumber(),
          incoming.getPromotionId(),
          incoming.getPromotionName(),
          incoming.getUserId(),
          incoming.getOrderAmount(),
          LocalDateTime.now());
      OrderDomain saved = orders.getOrderById(incoming.getOrderNumber());
      outbox.enqueue(Topics.LOCK, saved, 0);
      outbox.enqueue(Topics.check(shard), saved, delayLevel);
      return;
    }
    OrderDomain order = orders.getOrderById(incoming.getOrderNumber());
    if (order == null)
      throw new IllegalStateException("Order not materialized yet; retry delivery");
    if (Topics.check(shard).equals(topic)) {
      if (order.getOrderStatus() == OrderStatus.CREATED) {
        if (LocalDateTime.now().isBefore(order.getCreateTime().plusSeconds(timeoutSeconds)))
          throw new IllegalStateException("Payment deadline not reached; retry later");
        order.setOrderStatus(OrderStatus.OVERTIME);
        orders.updateOrder(order);
        outbox.enqueue(Topics.REVERT, order, 0);
      }
      return;
    }
    throw new IllegalArgumentException("Unexpected topic " + topic);
  }
}
