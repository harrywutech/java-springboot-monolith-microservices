package com.skillup.application.performance;

import com.skillup.application.order.MqSendRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageRelay {
  private final DSLContext db;
  private final MqSendRepo mq;

  @Value("${step3.relay-enabled:true}")
  private boolean enabled;

  @Scheduled(fixedDelay = 500, initialDelay = 5000)
  public void scheduled() {
    if (!enabled) return;
    try {
      drain();
    } catch (Exception e) {
      log.warn("Pending messages retained for retry: {}", e.toString());
    }
  }

  public void drain() {
    for (Record event :
        db.fetch(
            "select event_key,topic,payload,delay_level from mq_outbox where sent_at is null order"
                + " by created_at limit 50")) {
      mq.send(
          event.get("topic", String.class),
          event.get("payload", String.class),
          event.get("delay_level", Integer.class));
      db.execute(
          "update mq_outbox set sent_at=current_timestamp where event_key=? and sent_at is null",
          event.get("event_key"));
    }
  }
}
