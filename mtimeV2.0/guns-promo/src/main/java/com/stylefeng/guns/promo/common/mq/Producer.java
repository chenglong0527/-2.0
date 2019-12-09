package com.stylefeng.guns.promo.common.mq;

import com.alibaba.fastjson.JSON;
import com.stylefeng.guns.promo.common.persistence.dao.MtimeStockLogMapper;
import com.stylefeng.guns.promo.common.persistence.model.MtimeStockLog;
import com.stylefeng.guns.promo.common.persistence.vo.StockLogStatus;
import com.stylefeng.guns.service.film.vo.BaseRespVo;
import com.stylefeng.guns.service.promo.PromoService;
import com.stylefeng.guns.service.promo.vo.PromoOrderVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class Producer {
    private DefaultMQProducer producer;

    private TransactionMQProducer transactionMQProducer;

    @Value("${mq.nameService}")
    private String nameServiceAddress;

    @Value("${mq.topic}")
    private String topic;

    @Autowired
    private PromoService promoService;

    @Autowired
    private MtimeStockLogMapper stockLogMapper;

    @PostConstruct
    public void init() {
        producer = new DefaultMQProducer("producerGroup_stocks");
        producer.setNamesrvAddr(nameServiceAddress);
        try {
            producer.start();
        } catch (MQClientException e) {
            e.printStackTrace();
        }
        log.info("producer初始化成功");
        transactionMQProducer = new TransactionMQProducer("transaction_group");
        transactionMQProducer.setNamesrvAddr(nameServiceAddress);

        try {
            transactionMQProducer.start();
        } catch (MQClientException e) {
            e.printStackTrace();
        }

        //设置一个事务监听回调器
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            //第一个方法，执行本地事务
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object o) {
                Map argsMap = (Map) o;
                Integer promoId = (Integer) argsMap.get("promoId");
                Integer amount = (Integer) argsMap.get("amount");
                Integer userId = (Integer) argsMap.get("userId");
                String stockLogId = (String) argsMap.get("stockLogId");
                String promoToken = (String) argsMap.get("promoToken");

                //执行本地事务
                //1. 创建订单 2. 扣减redis中的库存
                BaseRespVo promoOrder = null;
                try {
                    promoOrder = promoService.createPromoOrder(promoId, amount, userId, stockLogId, promoToken);
                } catch (Exception e) {
                    e.printStackTrace();
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                //执行完之后库存流水状态被更改
                if (promoOrder == null) {//失败回滚
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            //回查本地事务状态
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                String bodyStr = new String(messageExt.getBody());
                Map map = JSON.parseObject(bodyStr, Map.class);
                String stockLogId = (String) map.get("stockLogId");
                MtimeStockLog stockLog = stockLogMapper.selectById(stockLogId);
                Integer status = stockLog.getStatus();
                //判断库存流水中的状态
                if (status == StockLogStatus.SUCCESS.getStatus()) {
                    return LocalTransactionState.COMMIT_MESSAGE;
                }
                if (status == StockLogStatus.FAIL.getStatus()) {
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.UNKNOW;
            }
        });
    }

    public String decreaseStocks(Integer promoId, Integer amount) {
        HashMap<String, Integer> stockMap = new HashMap<>();
        stockMap.put("promoId", promoId);
        stockMap.put("amount", amount);

        Message message = new Message(topic, JSON.toJSONBytes(stockMap));

        SendResult sendResult = null;
        try {
            sendResult = producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
        } catch (RemotingException e) {
            e.printStackTrace();
        } catch (MQBrokerException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.debug("发送结果：{}", JSON.toJSONString(sendResult));
        return sendResult.getSendStatus().toString();
    }

    //发送事务型消息
    public Boolean sendStockMessageIntransaction(Integer promoId, Integer amount, Integer userId, String stockLogId, String promoToken) {
        HashMap<String, Object> msgMap = new HashMap<>();
        msgMap.put("promoId", promoId);
        msgMap.put("amount", amount);
        msgMap.put("userId", userId);
        msgMap.put("stockLogId", stockLogId);

        HashMap<String, Object> argsMap = new HashMap<>();
        argsMap.put("promoId", promoId);
        argsMap.put("amount", amount);
        argsMap.put("userId", userId);
        argsMap.put("stockLogId", stockLogId);
        argsMap.put("promoToken", promoToken);

        String msgStr = JSON.toJSONString(msgMap);
        TransactionSendResult transactionSendResult = null;
        try {
            Message message = new Message(topic, msgStr.getBytes("utf-8"));
            transactionSendResult = transactionMQProducer.sendMessageInTransaction(message, argsMap);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (MQClientException e) {
            e.printStackTrace();
            log.error("事务消息发送异常");
            return false;
        }

        //发送事务型消息失败
        if (transactionSendResult == null) {
            return false;
        }
        //本地事务执行状态
        LocalTransactionState localTransactionState = transactionSendResult.getLocalTransactionState();
        if (LocalTransactionState.COMMIT_MESSAGE.equals(localTransactionState)) {
            return true;
        }
        return false;
    }
}
