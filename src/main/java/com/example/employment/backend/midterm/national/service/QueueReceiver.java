package com.example.employment.backend.midterm.national.service;

import com.example.employment.backend.midterm.national.dao.QueueDao.Batch;
import com.example.employment.backend.midterm.national.dao.QueueDao.Chunk;
import com.example.employment.backend.midterm.national.repository.QueueRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.w3c.dom.Element;

@Component
@ConditionalOnProperty(name="app.national.role",havingValue="receiver")
public class QueueReceiver {
    private final QueueRepository queue;
    private final XmlReport xml;
    private final TransactionTemplate tx;
    public QueueReceiver(QueueRepository queue,XmlReport xml,TransactionTemplate tx) {
        this.queue=queue;this.xml=xml;this.tx=tx;
    }
    @RabbitListener(queues=QueueSender.QUEUE)
    public void receive(Message message) {
        String id=String.valueOf(message.getMessageProperties().getHeaders().get("batchId"));
        try {
            tx.executeWithoutResult(status->accept(id,message));
        } catch(IllegalArgumentException ex) {
            String error=ex.getMessage()==null?"接收报文无效":ex.getMessage();
            if(error.length()>500) error=error.substring(0,500);
            String summary=error;
            tx.executeWithoutResult(status->queue.fail(id,summary,Instant.now()));
        }
    }
    private void accept(String id,Message message) {
        Batch batch=queue.batch(id);
        if(batch==null || !"demo-2".equals(batch.schema())) throw new IllegalArgumentException("未知 V2 批次");
        if("MOCK_ACCEPTED".equals(batch.status())) return;
        var headers=message.getMessageProperties().getHeaders();
        int index=number(headers.get("chunkIndex")),count=number(headers.get("chunkCount"));
        String hash=String.valueOf(headers.get("payloadSha256"));
        String chunkHash=String.valueOf(headers.get("chunkSha256"));
        byte[] bytes=message.getBody();
        if(index<0 || index>=count || count!=batch.chunks() || bytes.length<1 || bytes.length>1024 ||
                !hash.equals(batch.hash()) || !XmlReport.hash(bytes).equals(chunkHash))
            throw new IllegalArgumentException("消息分片头或哈希不匹配");
        queue.store(id,index,count,hash,chunkHash,bytes,Instant.now());
        List<Chunk> chunks=queue.chunks(id);
        if(chunks.size()<count) return;
        if(chunks.size()!=count) throw new IllegalArgumentException("消息分片数量不匹配");
        ByteArrayOutputStream assembled=new ByteArrayOutputStream();
        for(int i=0;i<count;i++) {
            Chunk chunk=chunks.get(i);
            if(chunk.index()!=i || chunk.count()!=count || !hash.equals(chunk.hash()) ||
                    !XmlReport.hash(chunk.bytes()).equals(chunk.chunkHash()))
                throw new IllegalArgumentException("消息分片序列或内容不匹配");
            assembled.writeBytes(chunk.bytes());
        }
        byte[] full=assembled.toByteArray();
        if(!XmlReport.hash(full).equals(batch.hash()) ||
                !java.util.Arrays.equals(full,batch.xml().getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("拼装内容与冻结报文不匹配");
        String payload=new String(full,StandardCharsets.UTF_8);
        if(payload.contains("<!DOCTYPE") || payload.contains("<!ENTITY"))
            throw new IllegalArgumentException("XML 不允许 DTD 或实体");
        try {
            xml.validate(payload);
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            var document=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(full));
            Element root=document.getDocumentElement();
            Element summary=(Element)root.getElementsByTagName("Summary").item(0);
            int enterprise=Integer.parseInt(text(summary,"EnterpriseCount"));
            int real=Integer.parseInt(text(summary,"RealCount"));
            int proxy=Integer.parseInt(text(summary,"ProxyCount"));
            long baseline=Long.parseLong(text(summary,"BaselineEmploymentTotal"));
            long current=Long.parseLong(text(summary,"PeriodEmploymentTotal"));
            long net=Long.parseLong(text(summary,"NetChange"));
            var nodes=root.getElementsByTagName("Enterprise");
            int realActual=0,proxyActual=0;long baselineActual=0,currentActual=0;
            for(int i=0;i<nodes.getLength();i++) {
                Element e=(Element)nodes.item(i);
                if("REAL".equals(e.getAttribute("reportMode"))) realActual++;
                else if("PROXY".equals(e.getAttribute("reportMode"))) proxyActual++;
                else throw new IllegalArgumentException("报表类型不合法");
                baselineActual+=Long.parseLong(text(e,"BaselineEmploymentCount"));
                currentActual+=Long.parseLong(text(e,"PeriodEmploymentCount"));
                if(Long.parseLong(text(e,"NetChange"))!=
                        Long.parseLong(text(e,"PeriodEmploymentCount"))-
                        Long.parseLong(text(e,"BaselineEmploymentCount")))
                    throw new IllegalArgumentException("企业净变化不匹配");
            }
            if(enterprise!=batch.enterprise() || real!=batch.real() || proxy!=batch.proxy() ||
                    real+proxy!=enterprise || nodes.getLength()!=enterprise ||
                    realActual!=real || proxyActual!=proxy || baseline!=batch.baseline() ||
                    current!=batch.current() || current-baseline!=net ||
                    baselineActual!=baseline || currentActual!=current)
                throw new IllegalArgumentException("接收报文统计合计不匹配");
            String receipt="MOCK-"+UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
            queue.receipt(id,receipt,enterprise,real,proxy,count,hash,Instant.now());
        } catch(IllegalArgumentException ex) { throw ex; }
        catch(Exception ex) { throw new IllegalArgumentException("XML 校验失败",ex); }
    }
    private static int number(Object value) {
        if(!(value instanceof Number n)) throw new IllegalArgumentException("分片编号缺失");
        return n.intValue();
    }
    private static String text(Element element,String name) {
        return element.getElementsByTagName(name).item(0).getTextContent();
    }
}
