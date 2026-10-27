package com.skillup.application.performance;

import com.skillup.application.order.MqSendRepo;
import com.skillup.infrastructure.redis.PromotionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Map;

@Component @Profile("step2") @RequiredArgsConstructor @Slf4j
public class MessageRelay {
    private final StringRedisTemplate redis;
    private final DSLContext db;
    private final MqSendRepo mq;
    @Value("${step2.relay-enabled:true}") private boolean enabled;
    @Scheduled(fixedDelay=500,initialDelay=5000) public void scheduled() {
        if(!enabled) return;
        try {drain();} catch(Exception e){log.warn("Pending messages retained for retry: {}",e.toString());}
    }
    public void drain() {
        for(Map.Entry<Object,Object> event:redis.opsForHash().entries(PromotionCache.PENDING).entrySet()) {
            mq.send(Topics.CREATE,event.getValue().toString(),0);
            redis.opsForHash().delete(PromotionCache.PENDING,event.getKey());
        }
        for(Record event:db.fetch("select event_key,topic,payload,delay_level from mq_outbox where sent_at is null order by created_at limit 50")) {
            mq.send(event.get("topic",String.class),event.get("payload",String.class),event.get("delay_level",Integer.class));
            db.execute("update mq_outbox set sent_at=current_timestamp where event_key=? and sent_at is null",event.get("event_key"));
        }
    }
}
