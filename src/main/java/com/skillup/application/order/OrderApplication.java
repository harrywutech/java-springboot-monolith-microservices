package com.skillup.application.order;

import com.skillup.domain.order.OrderDomain;
import com.skillup.domain.order.OrderService;
import com.skillup.domain.order.util.OrderStatus;
import com.skillup.domain.promotion.PromotionService;
import com.skillup.domain.promotion.PromotionDomain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Application layer: orchestrates operations across domains.
 *
 * Stage 1 transaction boundary:
 * Stock reservation and order creation share one JVM and one local transaction, calling
 * PromotionService directly. No network call is required; an exception rolls back the transaction.
 * After the microservice split, this @Transactional boundary cannot cover the promotion service,
 * which motivates eventual consistency and idempotent processing.
 */
@Service
@org.springframework.context.annotation.Profile("!step2")
public class OrderApplication implements OrderWorkflow {

    @Autowired
    OrderService orderService;

    @Autowired
    PromotionService promotionService;

    @Transactional
    public OrderDomain createOrder(OrderDomain orderDomain) {
        PromotionDomain promotion = promotionService.getById(orderDomain.getPromotionId());
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC());
        if (promotion == null || !Integer.valueOf(1).equals(promotion.getStatus())
                || now.isBefore(promotion.getStartTime()) || !now.isBefore(promotion.getEndTime())) {
            orderDomain.setOrderStatus(OrderStatus.ITEM_ERROR);
            return orderDomain;
        }
        orderDomain.setPromotionName(promotion.getPromotionName());
        orderDomain.setOrderAmount(promotion.getPromotionalPrice());
        // 1. Reserve stock with a local call in the same transaction as order creation.
        boolean isLocked = promotionService.lockStock(orderDomain.getPromotionId());
        if (!isLocked) {
            orderDomain.setOrderStatus(OrderStatus.OUT_OF_STOCK);
            return orderDomain;
        }
        // 2. Complete the order details and persist the order.
        orderDomain.setCreateTime(LocalDateTime.now());
        orderDomain.setOrderStatus(OrderStatus.CREATED);
        orderService.createOrder(orderDomain);
        return orderDomain;
    }

    @Transactional
    public OrderDomain payOrder(Long orderNumber) {
        OrderDomain orderDomain = orderService.getOrderById(orderNumber);
        if (Objects.isNull(orderDomain)) {
            return null;
        }
        if (!OrderStatus.CREATED.equals(orderDomain.getOrderStatus())) {
            return orderDomain;
        }
        if (thirdPartyPay()) {
            // Payment succeeds: mark the order PAYED and consume the reserved stock.
            orderDomain.setOrderStatus(OrderStatus.PAYED);
            orderDomain.setPayTime(LocalDateTime.now());
            orderService.updateOrder(orderDomain);
            if (!promotionService.deductStock(orderDomain.getPromotionId())) {
                throw new IllegalStateException("No reserved stock; payment transaction rolled back");
            }
        }
        return orderDomain;
    }

    /** Simulate third-party payment */
    public boolean thirdPartyPay() {
        return true;
    }
}
