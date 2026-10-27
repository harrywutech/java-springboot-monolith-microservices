package com.skillup.infrastructure.rocketmq;

import com.skillup.application.order.MqSendRepo;
import com.skillup.application.performance.*;
import com.skillup.domain.order.OrderDomain;
import com.skillup.infrastructure.redis.PromotionCache;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component @Profile("step2") @RequiredArgsConstructor
public class RocketMqGateway implements MqSendRepo {
    private final ApplicationEventPublisher events;
    private final PromotionCache json;
    @Value("${step2.nameserver}") private String nameserver;
    @Value("${step2.messaging-enabled:true}") private boolean enabled;
    private DefaultMQProducer producer;
    private final List<DefaultMQPushConsumer> consumers=new ArrayList<>();
    @EventListener(ApplicationReadyEvent.class) public void start() throws Exception {
        if(!enabled) return;
        producer=new DefaultMQProducer("step2-producer");
        producer.setNamesrvAddr(nameserver);producer.setVipChannelEnabled(false);
        producer.setSendMsgTimeout(5000);producer.start();
        for(String topic:Topics.ALL) {
            DefaultMQPushConsumer consumer=new DefaultMQPushConsumer(topic+"-group");
            consumer.setNamesrvAddr(nameserver);consumer.setVipChannelEnabled(false);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
            consumer.setConsumeThreadMin(2);consumer.setConsumeThreadMax(2);
            consumer.subscribe(topic,"*");
            consumer.registerMessageListener((MessageListenerConcurrently)(messages,context)->{
                try {
                    for(org.apache.rocketmq.common.message.MessageExt message:messages) {
                        OrderDomain order=json.read(new String(message.getBody(),StandardCharsets.UTF_8),OrderDomain.class);
                        // Synchronous Spring events: exceptions reach MQ so it retries, never ACK early.
                        events.publishEvent(new OrderMessageEvent(message.getTopic(),order));
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                } catch(Exception failure) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Message will retry: {}",failure.toString());
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            });
            consumer.start();consumers.add(consumer);
        }
    }
    @Override public void send(String topic,String payload,int delayLevel) {
        if(producer==null) throw new IllegalStateException("RocketMQ producer is not started");
        try {
            Message message=new Message(topic,payload.getBytes(StandardCharsets.UTF_8));
            if(delayLevel>0) message.setDelayTimeLevel(delayLevel);
            SendResult result=producer.send(message);
            if(result.getSendStatus()!=SendStatus.SEND_OK) throw new IllegalStateException("Unconfirmed MQ send: "+result.getSendStatus());
        } catch(Exception e) {throw new IllegalStateException("RocketMQ send failed; event stays pending",e);}
    }
    @PreDestroy public void close() {
        for(DefaultMQPushConsumer consumer:consumers) consumer.shutdown();
        if(producer!=null) producer.shutdown();
    }
}
