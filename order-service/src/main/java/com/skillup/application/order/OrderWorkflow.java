package com.skillup.application.order;

import com.skillup.domain.order.OrderDomain;

public interface OrderWorkflow {
  OrderDomain createOrder(OrderDomain order);

  OrderDomain payOrder(Long id);
}
