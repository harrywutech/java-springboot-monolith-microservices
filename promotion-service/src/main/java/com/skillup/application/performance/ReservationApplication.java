package com.skillup.application.performance;

import com.skillup.domain.order.*;
import com.skillup.domain.order.util.OrderStatus;
import com.skillup.domain.promotion.PromotionDomain;
import com.skillup.infrastructure.redis.PromotionCache;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReservationApplication {
  private final PromotionCache cache;

  public OrderDomain reserve(OrderDomain order) {
    if (order.getOrderNumber() == null
        || order.getPromotionId() == null
        || order.getUserId() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing reservation identity");
    PromotionDomain p = cache.get(order.getPromotionId());
    LocalDateTime now = LocalDateTime.now();
    if (p == null
        || !Integer.valueOf(1).equals(p.getStatus())
        || now.isBefore(p.getStartTime())
        || !now.isBefore(p.getEndTime())) {
      order.setOrderStatus(OrderStatus.ITEM_ERROR);
      return order;
    }
    order.setPromotionName(p.getPromotionName());
    order.setOrderAmount(p.getPromotionalPrice());
    order.setCreateTime(now);
    order.setOrderStatus(OrderStatus.READY);
    long result = cache.reserve(order);
    if (result == -1) order.setOrderStatus(OrderStatus.OUT_OF_STOCK);
    else if (result < 0)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Reservation unavailable; reconcile before retrying with a new order");
    else {
      OrderDomain saved = cache.snapshot(order);
      if (!saved.getUserId().equals(order.getUserId()))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation owner mismatch");
      return saved;
    }
    return order;
  }
}
