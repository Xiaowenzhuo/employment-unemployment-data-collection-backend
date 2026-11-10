package com.example.employment.backend.midterm.national.service;

import com.example.employment.backend.midterm.national.dao.QueueDao.Batch;
import com.example.employment.backend.midterm.national.repository.QueueRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class QueueSender {
    public static final String QUEUE="employment.national.v2.chunks";
    private static final Logger log=LoggerFactory.getLogger(QueueSender.class);
    private final QueueRepository queue;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;
    public QueueSender(QueueRepository queue,RabbitTemplate rabbit,TransactionTemplate tx) {
        this.queue=queue;this.rabbit=rabbit;this.tx=tx;
    }
    @Async
    public void send(String id) {
        Integer attempt=tx.execute(status-> {
            Batch batch=queue.batch(id);
            if(batch==null || !"demo-2".equals(batch.schema())) return null;
            int number=attemptCount(id)+1;
            return queue.start(id,number,Instant.now())?number:null;
        });
        if(attempt==null) return;
        try {
            Batch batch=queue.batch(id);
            byte[] bytes=batch.xml().getBytes(StandardCharsets.UTF_8);
            if(!XmlReport.hash(bytes).equals(batch.hash()) ||
                    (bytes.length+1023)/1024!=batch.chunks())
                throw new IllegalStateException("冻结报文哈希或分片数不匹配");
            for(int index=0;index<batch.chunks();index++) {
                int from=index*1024;
                byte[] chunk=Arrays.copyOfRange(bytes,from,Math.min(from+1024,bytes.length));
                CorrelationData correlation=new CorrelationData(UUID.randomUUID().toString());
                int current=index;
                rabbit.convertAndSend("",QUEUE,chunk,message-> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setHeader("batchId",id);
                    message.getMessageProperties().setHeader("chunkIndex",current);
                    message.getMessageProperties().setHeader("chunkCount",batch.chunks());
                    message.getMessageProperties().setHeader("payloadSha256",batch.hash());
                    message.getMessageProperties().setHeader("chunkSha256",XmlReport.hash(chunk));
                    return message;
                },correlation);
                var confirm=correlation.getFuture().get(10,TimeUnit.SECONDS);
                if(!confirm.isAck()) throw new IllegalStateException("RabbitMQ 未确认分片 "+index);
                tx.executeWithoutResult(status->queue.confirmed(id,current,Instant.now()));
            }
            tx.executeWithoutResult(status->queue.assembling(id));
        } catch(Exception ex) {
            log.warn("National V2 queue send failed for {}",id,ex);
            String error=ex.getMessage()==null?"消息发送失败":ex.getMessage();
            if(error.length()>500) error=error.substring(0,500);
            String summary=error;
            tx.executeWithoutResult(status->queue.fail(id,summary,Instant.now()));
        }
    }
    private int attemptCount(String id) {
        return queue.attemptCount(id);
    }
}
