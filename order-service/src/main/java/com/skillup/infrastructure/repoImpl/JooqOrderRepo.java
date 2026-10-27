package com.skillup.infrastructure.repoImpl;

import com.skillup.domain.order.OrderDomain;
import com.skillup.domain.order.OrderRepository;
import com.skillup.domain.order.util.OrderStatus;
import com.skillup.infrastructure.jooq.tables.Order;
import com.skillup.infrastructure.jooq.tables.records.OrderRecord;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqOrderRepo implements OrderRepository {
  @Autowired DSLContext dslContext;

  private static final Order OT = new Order();

  @Override
  public void createOrder(OrderDomain orderDomain) {
    dslContext.executeInsert(toRecord(orderDomain));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public OrderDomain getOrderById(Long id) {
    return dslContext
        .selectFrom(OT)
        .where(OT.ORDER_NUMBER.eq(id))
        .forUpdate()
        .fetchOptional(this::toDomain)
        .orElse(null);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void updateOrder(OrderDomain orderDomain) {
    dslContext.executeUpdate(toRecord(orderDomain));
  }

  private OrderDomain toDomain(OrderRecord record) {
    return OrderDomain.builder()
        .orderNumber(record.getOrderNumber())
        .orderStatus(OrderStatus.CACHE.get(record.getOrderStatus()))
        .promotionId(record.getPromotionId())
        .promotionName(record.getPromotionName())
        .userId(record.getUserId())
        .orderAmount(record.getOrderAmount())
        .createTime(record.getCreateTime())
        .payTime(record.getPayTime())
        .build();
  }

  private OrderRecord toRecord(OrderDomain domain) {
    return new OrderRecord(
        domain.getOrderNumber(),
        domain.getOrderStatus().code,
        domain.getPromotionId(),
        domain.getPromotionName(),
        domain.getUserId(),
        domain.getOrderAmount(),
        domain.getCreateTime(),
        domain.getPayTime());
  }
}
