package com.skillup.application.performance;

import com.skillup.application.order.OrderWorkflow;
import com.skillup.domain.order.*;
import com.skillup.domain.order.util.OrderStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MicroserviceOrderApplication implements OrderWorkflow {
  private final com.skillup.application.promotion.PromotionServiceApi promotion;

  @org.springframework.beans.factory.annotation.Value("${step3.shard}")
  private int shard;

  private final OrderService orders;
  private final MessageOutbox outbox;

  @org.springframework.beans.factory.annotation.Value("${step3.pay-timeout-seconds:60}")
  private int timeoutSeconds;

  @Override
  public OrderDomain createOrder(OrderDomain order) {
    if (com.skillup.common.Shard.of(order.getUserId()) != shard)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "User does not belong to this shard");
    try {
      return promotion.reserve(order);
    } catch (feign.FeignException failure) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Promotion service unavailable; reservation may already be accepted",
          failure);
    }
  }

  @Override
  @Transactional
  public OrderDomain payOrder(Long id) {
    OrderDomain order = orders.getOrderById(id); // SELECT FOR UPDATE: serialize pay vs expiry.
    if (order == null || order.getOrderStatus() != OrderStatus.CREATED) return order;
    if (!LocalDateTime.now().isBefore(order.getCreateTime().plusSeconds(timeoutSeconds))) {
      order.setOrderStatus(OrderStatus.OVERTIME);
      orders.updateOrder(order);
      outbox.enqueue(Topics.REVERT, order, 0);
      return order;
    }
    order.setOrderStatus(OrderStatus.PAYED);
    order.setPayTime(LocalDateTime.now());
    orders.updateOrder(order);
    outbox.enqueue(Topics.DEDUCT, order, 0);
    return order;
  }
}
