package com.skillup.application.performance;
import com.skillup.domain.order.OrderDomain;
import com.skillup.infrastructure.redis.PromotionCache;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
@Repository @Profile("step2") @RequiredArgsConstructor
public class MessageOutbox {
    private final DSLContext db;
    private final PromotionCache json;
    public void enqueue(String topic,OrderDomain order,int delayLevel) {
        db.execute("insert into mq_outbox(event_key,topic,payload,delay_level) values(?,?,?,?) on duplicate key update event_key=event_key",
            topic+":"+order.getOrderNumber(),topic,json.write(order),delayLevel);
    }
}
