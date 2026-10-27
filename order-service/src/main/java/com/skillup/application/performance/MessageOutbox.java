package com.skillup.application.performance;

import com.skillup.common.JsonCodec;
import com.skillup.domain.order.OrderDomain;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageOutbox {
  private final DSLContext db;
  private final JsonCodec json;

  public void enqueue(String topic, OrderDomain order, int delayLevel) {
    db.execute(
        "insert into mq_outbox(event_key,topic,payload,delay_level) values(?,?,?,?) on duplicate"
            + " key update event_key=event_key",
        topic + ":" + order.getOrderNumber(),
        topic,
        json.write(order),
        delayLevel);
  }
}
