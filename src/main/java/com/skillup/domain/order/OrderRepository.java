package com.skillup.domain.order;

public interface OrderRepository {

    public void createOrder(OrderDomain orderDomain);

    public OrderDomain getOrderById(Long id);

    public void updateOrder(OrderDomain orderDomain);
}
