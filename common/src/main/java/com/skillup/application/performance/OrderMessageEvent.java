package com.skillup.application.performance;

import com.skillup.domain.order.OrderDomain;
import lombok.Value;

/** Framework-neutral application event; the MQ adapter knows RocketMQ, handlers do not. */
@Value
public class OrderMessageEvent {
  String topic;
  OrderDomain order;
}
