package com.skillup.application.performance;

import com.skillup.domain.order.*;
import com.skillup.domain.order.util.OrderStatus;
import com.skillup.domain.promotion.PromotionRepository;
import com.skillup.infrastructure.repoImpl.JooqPromotionRepo;
import com.skillup.infrastructure.redis.PromotionCache;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component @Profile("step2") @RequiredArgsConstructor
public class OrderMessageHandler {
    private final OrderService orders;
    private final JooqPromotionRepo stock;
    private final MessageOutbox outbox;
    private final PromotionCache cache;
    private final DSLContext db;
    @Value("${step2.pay-delay-level:5}") private int delayLevel;
    @Value("${step2.pay-timeout-seconds:60}") private int timeoutSeconds;
    @EventListener @Transactional public void handle(OrderMessageEvent event) {
        OrderDomain incoming=event.getOrder();
        String topic=event.getTopic();
        if(Topics.CREATE.equals(topic)) {
            // Unique PK arbitrates concurrent delivery. Follow-up events commit with the order.
            db.execute("insert into `order`(order_number,order_status,promotion_id,promotion_name,user_id,order_amount,create_time) values(?,1,?,?,?,?,?) on duplicate key update order_number=order_number",
                incoming.getOrderNumber(),incoming.getPromotionId(),incoming.getPromotionName(),incoming.getUserId(),incoming.getOrderAmount(),LocalDateTime.now());
            OrderDomain saved=orders.getOrderById(incoming.getOrderNumber());
            outbox.enqueue(Topics.LOCK,saved,0);outbox.enqueue(Topics.CHECK,saved,delayLevel);return;
        }
        OrderDomain order=orders.getOrderById(incoming.getOrderNumber());
        if(order==null) throw new IllegalStateException("Order not materialized yet; retry delivery");
        if(Topics.CHECK.equals(topic)) {
            if(order.getOrderStatus()==OrderStatus.CREATED) {
                if(LocalDateTime.now().isBefore(order.getCreateTime().plusSeconds(timeoutSeconds)))
                    throw new IllegalStateException("Payment deadline not reached; retry later");
                order.setOrderStatus(OrderStatus.OVERTIME);orders.updateOrder(order);
                outbox.enqueue(Topics.REVERT,order,0);
            }
            return;
        }
        String operation;
        if(Topics.LOCK.equals(topic)) operation="LOCK_STOCK";
        else if(Topics.REVERT.equals(topic)) operation="REVERT_STOCK";
        else if(Topics.DEDUCT.equals(topic)) operation="DEDUCT_STOCK";
        else throw new IllegalArgumentException("Unknown topic "+topic);
        // The order row lock serializes all stock actions for this order before log checks.
        if(logged(order.getOrderNumber(),operation)) return;
        if(!Topics.LOCK.equals(topic) && !logged(order.getOrderNumber(),"LOCK_STOCK"))
            throw new IllegalStateException("Stock lock message not consumed yet; retry");
        boolean success;
        if(Topics.LOCK.equals(topic)) success=stock.lockStock(order.getPromotionId());
        else if(Topics.DEDUCT.equals(topic)) {
            if(order.getOrderStatus()!=OrderStatus.PAYED) throw new IllegalStateException("Order is not paid");
            success=stock.deductStock(order.getPromotionId());
        } else {
            if(order.getOrderStatus()!=OrderStatus.OVERTIME) throw new IllegalStateException("Order is not expired");
            cache.release(order); // idempotent Lua survives DB rollback/re-delivery
            success=stock.revertStock(order.getPromotionId());
        }
        if(!success) throw new IllegalStateException("Stock invariant failed; retry/reconcile");
        db.execute("insert into promotion_log(order_number,user_id,promotion_id,operation_name,create_time) values(?,?,?,?,?)",
            order.getOrderNumber(),order.getUserId(),order.getPromotionId(),operation,LocalDateTime.now());
    }
    private boolean logged(Long id,String operation) {
        return db.fetchOne("select count(*) from promotion_log where order_number=? and operation_name=?",id,operation).get(0,Integer.class)>0;
    }
}
