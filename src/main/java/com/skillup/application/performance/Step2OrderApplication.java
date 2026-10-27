package com.skillup.application.performance;

import com.skillup.application.order.OrderWorkflow;
import com.skillup.domain.order.*;
import com.skillup.domain.order.util.OrderStatus;
import com.skillup.domain.promotion.PromotionDomain;
import com.skillup.infrastructure.redis.PromotionCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;

@Service @Profile("step2") @RequiredArgsConstructor
public class Step2OrderApplication implements OrderWorkflow {
    private final PromotionCache cache;
    private final OrderService orders;
    private final MessageOutbox outbox;
    @org.springframework.beans.factory.annotation.Value("${step2.pay-timeout-seconds:60}")
    private int timeoutSeconds;
    @Override public OrderDomain createOrder(OrderDomain order) {
        PromotionDomain p=cache.get(order.getPromotionId());
        LocalDateTime now=LocalDateTime.now();
        if(p==null || !Integer.valueOf(1).equals(p.getStatus()) || now.isBefore(p.getStartTime()) || !now.isBefore(p.getEndTime())) {
            order.setOrderStatus(OrderStatus.ITEM_ERROR);return order;
        }
        order.setPromotionName(p.getPromotionName());order.setOrderAmount(p.getPromotionalPrice());
        order.setCreateTime(now);order.setOrderStatus(OrderStatus.READY);
        long result=cache.reserve(order);
        if(result==-1) order.setOrderStatus(OrderStatus.OUT_OF_STOCK);
        else if(result<0) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Stock is not warmed or reservation is closed");
        // 202 means accepted in Redis. A relay sends CREATE to RocketMQ; no MySQL write here.
        return order;
    }
    @Override @Transactional public OrderDomain payOrder(Long id) {
        OrderDomain order=orders.getOrderById(id); // SELECT FOR UPDATE: serialize pay vs expiry.
        if(order==null || order.getOrderStatus()!=OrderStatus.CREATED) return order;
        if(!LocalDateTime.now().isBefore(order.getCreateTime().plusSeconds(timeoutSeconds))) {
            order.setOrderStatus(OrderStatus.OVERTIME);orders.updateOrder(order);
            outbox.enqueue(Topics.REVERT,order,0);return order;
        }
        order.setOrderStatus(OrderStatus.PAYED);order.setPayTime(LocalDateTime.now());
        orders.updateOrder(order);
        outbox.enqueue(Topics.DEDUCT,order,0);
        return order;
    }
}
