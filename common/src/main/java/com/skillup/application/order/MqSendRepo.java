package com.skillup.application.order;

public interface MqSendRepo {
  void send(String topic, String payload, int delayLevel);
}
