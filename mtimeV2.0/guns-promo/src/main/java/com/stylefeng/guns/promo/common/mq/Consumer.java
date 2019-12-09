package com.stylefeng.guns.promo.common.mq;

import com.alibaba.fastjson.JSON;
import com.stylefeng.guns.promo.common.persistence.dao.MtimePromoStockMapper;
import com.stylefeng.guns.promo.common.persistence.model.MtimePromoStock;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class Consumer {

    private DefaultMQPushConsumer consumer;

    @Value("${mq.nameService}")
    private String nameServiceAddress;

    @Value("${mq.topic}")
    private String topic;

    @Autowired
    MtimePromoStockMapper promoStockMapper;

    @PostConstruct
    public void init() {
        consumer = new DefaultMQPushConsumer("consumerGroup_stocks");
        consumer.setNamesrvAddr(nameServiceAddress);
        try {
            consumer.subscribe(topic, "*");
        } catch (MQClientException e) {
            e.printStackTrace();
        }
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
                MessageExt messageExt = list.get(0);
                byte[] body = messageExt.getBody();
                Map mapStocks = JSON.parseObject(new String(body), Map.class);
                Integer promoId = (Integer) mapStocks.get("promoId");
                Integer amount = (Integer) mapStocks.get("amount");
                promoStockMapper.updateStockByPromoId(promoId, amount);
                log.info("promoId{}--amount{}", promoId, amount);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        try {
            consumer.start();
        } catch (MQClientException e) {
            e.printStackTrace();
        }
    }
}
