package com.example.employment.backend.midterm.national.service;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.midterm.national.dao.QueueDao.Proxy;
import com.example.employment.backend.midterm.national.dao.QueueDao.Source;
import com.example.employment.backend.midterm.national.dao.QueueDao.Batch;
import com.example.employment.backend.midterm.national.dao.QueueDao.Receipt;
import com.example.employment.backend.midterm.national.repository.QueueRepository;
import com.example.employment.backend.midterm.national.service.XmlReport.Entry;
import com.example.employment.backend.national.dao.NationalDao.SourceRow;
import com.example.employment.backend.national.repository.NationalRepository;
import com.example.employment.backend.national.service.NationalApiException;
import com.example.employment.backend.revision.dao.ProgressDao.Row;
import com.example.employment.backend.revision.repository.ProgressRepository;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NationalV2Service {
    private final QueueRepository queue;
    private final ProgressRepository progress;
    private final NationalRepository national;
    private final PeriodRepository periods;
    private final ScopeService scopes;
    private final XmlReport xml;
    private final QueueSender sender;
    public NationalV2Service(QueueRepository queue,ProgressRepository progress,
            NationalRepository national,PeriodRepository periods,ScopeService scopes,
            XmlReport xml,QueueSender sender) {
        this.queue=queue;this.progress=progress;this.national=national;this.periods=periods;
        this.scopes=scopes;this.xml=xml;this.sender=sender;
    }
    public Map<String,Object> readiness(UserAccount actor,String code) {
        long province=scopes.province(actor).id();
        return inspect(province,period(province,code,false)).view();
    }
    public boolean isV2(UserAccount actor,String id) {
        long province=scopes.province(actor).id();
        var batch=national.byId(province,id).orElseThrow(NotFoundException::new);
        return batch.payloadJson()==null;
    }
    public Map<String,Object> list(UserAccount actor,String period,String status,int page,int size) {
        long province=scopes.province(actor).id();
        if(period!=null && !period.matches("[A-Z0-9_]{1,32}"))
            throw new FieldErrorException(Map.of("periodCode","期次代码不合法"));
        if(status!=null && !List.of("QUEUED","SENDING","ASSEMBLING","MOCK_ACCEPTED","SEND_FAILED").contains(status))
            throw new FieldErrorException(Map.of("status","批次状态不合法"));
        if(page<0 || size<1 || size>100) throw new FieldErrorException(Map.of("page/size","分页参数不合法"));
        var rows=national.list(province,period,status,page,size);
        return Map.of("items",rows.stream().map(this::summary).toList(),
                "total",national.count(province,period,status),"page",page,"size",size);
    }
    public Map<String,Object> detail(UserAccount actor,String id,Object legacyPayload) {
        long province=scopes.province(actor).id();
        var row=national.byId(province,id).orElseThrow(NotFoundException::new);
        Batch batch=queue.batch(id);
        Map<String,Object> out=new LinkedHashMap<>(summary(row));
        out.put("payload",batch!=null && "demo-2".equals(batch.schema())?null:legacyPayload);
        out.put("payloadXml",batch!=null && "demo-2".equals(batch.schema())?batch.xml():null);
        if(batch!=null && "demo-2".equals(batch.schema())) {
            Receipt receipt=queue.receiptByBatch(id);
            Map<String,Object> chunk=new LinkedHashMap<>();
            chunk.put("chunkCount",batch.chunks());chunk.put("brokerConfirmedCount",queue.confirmed(id));
            chunk.put("receiverStoredCount",queue.stored(id));
            chunk.put("assembledAt",receipt==null?null:receipt.at());
            chunk.put("lastError",row.lastError());out.put("chunkProgress",chunk);
            if(receipt==null) out.put("receipt",null);
            else out.put("receipt",Map.of("batchId",id,"mockReceiptId",receipt.id(),
                    "result","ACCEPTED","receivedAt",receipt.at(),
                    "acceptedEnterpriseCount",receipt.enterprise(),"realCount",receipt.real(),
                    "proxyCount",receipt.proxy(),"chunkCount",receipt.chunks(),
                    "payloadSha256",receipt.hash()));
        } else {
            out.put("chunkProgress",null);
            if(row.receiptId()==null) out.put("receipt",null);
            else out.put("receipt",Map.of("batchId",id,"mockReceiptId",row.receiptId(),
                    "result","ACCEPTED","receivedAt",row.receiptAt(),
                    "acceptedEnterpriseCount",row.enterpriseCount(),
                    "payloadSha256",row.payloadSha256()));
        }
        out.put("attempts",national.attempts(id).stream().map(a-> {
            Map<String,Object> attempt=new LinkedHashMap<>();
            attempt.put("attemptNo",a.attemptNo());attempt.put("startedAt",a.startedAt());
            attempt.put("finishedAt",a.finishedAt());attempt.put("result",a.result());
            attempt.put("errorSummary",a.errorSummary());
            attempt.put("mockReceiptId",a.mockReceiptId());return attempt;
        }).toList());
        return out;
    }
    @Transactional
    public Map<String,Object> retry(UserAccount actor,String id) {
        long province=scopes.province(actor).id();
        var row=national.byId(province,id).orElseThrow(NotFoundException::new);
        if(row.payloadJson()!=null) throw conflict("V1_READ_ONLY","V1 历史批次只读");
        if(!"QUEUED".equals(row.status()) && !"SEND_FAILED".equals(row.status()))
            throw conflict("RETRY_NOT_ALLOWED","当前批次不能重发");
        if("SEND_FAILED".equals(row.status()) && national.queueRetry(id)!=1)
            throw conflict("RETRY_NOT_ALLOWED","批次状态已变化");
        afterCommit(id);
        return Map.of("batchId",id,"status","QUEUED","mode","MOCK_QUEUE",
                "payloadFormat","XML","enterpriseCount",row.enterpriseCount(),
                "realCount",queue.batch(id).real(),"proxyCount",queue.batch(id).proxy(),
                "detailPath","/province/national/batches/"+id);
    }
    private Map<String,Object> summary(com.example.employment.backend.national.dao.NationalDao.BatchRow row) {
        Batch batch=queue.batch(row.batchId());
        boolean v2=batch!=null && "demo-2".equals(batch.schema());
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("batchId",row.batchId());out.put("periodCode",row.periodCode());
        out.put("schemaVersion",v2?"demo-2":"demo-1");
        out.put("mode",v2?"MOCK_QUEUE":"MOCK");out.put("payloadFormat",v2?"XML":"JSON");
        out.put("status",row.status());out.put("enterpriseCount",row.enterpriseCount());
        out.put("realCount",v2?batch.real():row.enterpriseCount());
        out.put("proxyCount",v2?batch.proxy():0);
        out.put("payloadSha256",row.payloadSha256());out.put("createdAt",row.createdAt());
        out.put("lastSentAt",row.lastSentAt());
        out.put("retryCount",Math.max(0,national.attemptCount(row.batchId())-1));
        out.put("lastError",row.lastError());return out;
    }
    @Transactional
    public Map<String,Object> fill(UserAccount actor,String code,Integer expected) {
        long province=scopes.province(actor).id();
        queue.lockProvince(province);
        SurveyPeriod period=period(province,code,true);
        if(national.byPeriod(province,code).isPresent()) throw conflict("BATCH_FROZEN","本期已冻结");
        int version=queue.version(province,code);
        if(expected!=null && expected!=version) throw conflict("VERSION_CONFLICT","代填版本已变化");
        List<Row> rows=progress.rows(province,code);
        Map<Long,SourceRow> sourceRows=sources(province,code);
        List<Long> missingSource=new ArrayList<>();
        List<Proxy> replacements=new ArrayList<>();
        Instant now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for(Row row:rows) {
            if(!row.eligible() || row.realSubmitted()) continue;
            Source source=queue.previous(row.enterpriseId(),period.startAt()).stream().findFirst().orElse(null);
            if(source==null) { missingSource.add(row.enterpriseId());continue; }
            SourceRow filing=sourceRows.get(row.enterpriseId());
            if(filing==null || filing.currentOrgCode()==null || filing.currentNatureCode()==null ||
                    filing.currentIndustryCode()==null) throw conflict("PROXY_SOURCE_CHANGED","代填企业备案资料不完整");
            replacements.add(new Proxy(row.enterpriseId(),code,province,filing.currentOrgCode(),
                    row.enterpriseName(),filing.regionCode(),filing.currentNatureCode(),
                    filing.currentIndustryCode(),source.period(),source.reportId(),
                    source.submission(),source.version(),source.employment(),actor.id(),now));
        }
        if(!missingSource.isEmpty()) throw new NationalApiException(HttpStatus.CONFLICT,
                "部分缺报企业没有此前省审通过的真实报表","NO_REAL_SOURCE",
                Map.of("enterpriseOrgIds",missingSource.stream().map(String::valueOf).toList()));
        queue.clear(province,code);
        replacements.forEach(queue::insertProxy);
        int next=queue.nextVersion(province,code);
        Snapshot snapshot=inspect(province,period);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("periodCode",code);out.put("proxyVersion",next);
        out.put("generatedCount",replacements.size());
        out.put("items",snapshot.proxyItems());out.put("previewSummary",snapshot.totals());
        return out;
    }
    @Transactional
    public Map<String,Object> delete(UserAccount actor,String code,Integer expected) {
        long province=scopes.province(actor).id();
        queue.lockProvince(province);period(province,code,true);
        if(national.byPeriod(province,code).isPresent()) throw conflict("BATCH_FROZEN","本期已冻结");
        int version=queue.version(province,code);
        if(expected!=null && expected!=version) throw conflict("VERSION_CONFLICT","代填版本已变化");
        int removed=queue.clear(province,code);
        return Map.of("periodCode",code,"proxyVersion",queue.nextVersion(province,code),
                "removedCount",removed);
    }
    @Transactional
    public Map<String,Object> create(UserAccount actor,String code,String schema) {
        if(!"demo-2".equals(schema)) throw new FieldErrorException(Map.of("schemaVersion","只能是 demo-2"));
        long province=scopes.province(actor).id();
        queue.lockProvince(province);
        SurveyPeriod period=period(province,code,true);
        national.lockReports(code);
        Snapshot snapshot=inspect(province,period);
        if(national.byPeriod(province,code).isPresent()) throw conflict("BATCH_EXISTS","本期已有上报批次");
        if(!snapshot.blockers().isEmpty()) throw new NationalApiException(HttpStatus.CONFLICT,
                "本期尚不满足上报条件","NOT_READY",Map.of("blockers",snapshot.blockers()));
        for(Proxy proxy:snapshot.proxies()) {
            Source source=queue.previous(proxy.orgId(),period.startAt()).stream().findFirst().orElse(null);
            if(source==null || source.reportId()!=proxy.sourceReport() ||
                    source.submission()!=proxy.sourceSubmission() ||
                    source.version()!=proxy.sourceVersion() || source.employment()!=proxy.employment())
                throw conflict("PROXY_SOURCE_CHANGED","代填来源已变化，请重新生成");
        }
        String id="YN-"+code+"-0001";
        Instant now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String payload=xml.create(id,code,period.startAt(),period.endExclusiveAt(),now,snapshot.entries());
        byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
        String hash=XmlReport.hash(bytes);
        int chunks=(bytes.length+1023)/1024;
        long baseline=snapshot.entries().stream().mapToLong(Entry::baseline).sum();
        long current=snapshot.entries().stream().mapToLong(Entry::current).sum();
        queue.insertBatch(id,province,code,payload,hash,snapshot.entries().size(),
                snapshot.realCount(),snapshot.proxies().size(),baseline,current,chunks,now);
        for(int i=0;i<chunks;i++) {
            int from=i*1024,size=Math.min(1024,bytes.length-from);
            queue.insertChunk(id,i,XmlReport.hash(java.util.Arrays.copyOfRange(bytes,from,from+size)),size);
        }
        for(Entry entry:snapshot.entries()) if("REAL".equals(entry.mode()))
            national.insertItem(id,entry.reportId(),entry.submission(),entry.version(),
                    entry.baseline(),entry.current());
        queue.freezeProxies(province,code,id);
        afterCommit(id);
        return Map.of("batchId",id,"status","QUEUED","mode","MOCK_QUEUE",
                "payloadFormat","XML","enterpriseCount",snapshot.entries().size(),
                "realCount",snapshot.realCount(),"proxyCount",snapshot.proxies().size(),
                "detailPath","/province/national/batches/"+id);
    }
    private Snapshot inspect(long province,SurveyPeriod period) {
        List<Row> rows=progress.rows(province,period.code());
        Map<Long,SourceRow> sources=sources(province,period.code());
        List<Proxy> proxies=queue.proxies(province,period.code());
        Map<Long,Proxy> proxyMap=new HashMap<>();
        proxies.forEach(p->proxyMap.put(p.orgId(),p));
        List<Entry> entries=new ArrayList<>();
        List<Map<String,Object>> realItems=new ArrayList<>(),proxyItems=new ArrayList<>(),
                fillable=new ArrayList<>(),noSource=new ArrayList<>(),blockers=new ArrayList<>();
        long eligible=0,pendingCity=0,pendingProvince=0,returnedCity=0,returnedProvince=0,voided=0,unapproved=0;
        for(Row row:rows) {
            if(!row.eligible()) continue;
            eligible++;
            if(row.voided()) voided++;
            String status=row.effectiveReportStatus();
            if("SUBMITTED_CITY".equals(status)) pendingCity++;
            if("SUBMITTED_PROVINCE".equals(status)) pendingProvince++;
            if("RETURNED_CITY".equals(status)) returnedCity++;
            if("RETURNED_PROVINCE".equals(status)) returnedProvince++;
            SourceRow source=sources.get(row.enterpriseId());
            if("APPROVED_PROVINCE".equals(status) && source!=null) {
                Entry entry=real(source);entries.add(entry);
                realItems.add(Map.of("enterpriseOrgId",Long.toString(row.enterpriseId()),
                        "enterpriseName",entry.name(),"reportId",Long.toString(entry.reportId()),
                        "regionCode",entry.region(),"baselineEmploymentCount",entry.baseline(),
                        "periodEmploymentCount",entry.current(),"netChange",entry.current()-entry.baseline()));
            } else if(row.realSubmitted()) {
                unapproved++;
                continue;
            } else if(proxyMap.containsKey(row.enterpriseId())) {
                Proxy proxy=proxyMap.get(row.enterpriseId());
                entries.add(proxyEntry(proxy));proxyItems.add(proxyView(proxy));
            } else {
                Source previous=queue.previous(row.enterpriseId(),period.startAt()).stream().findFirst().orElse(null);
                if(previous==null) noSource.add(Map.of("enterpriseOrgId",Long.toString(row.enterpriseId()),
                        "enterpriseName",row.enterpriseName(),"cityCode",row.cityCode(),
                        "regionCode",source.regionCode(),"reason","NO_PREVIOUS_APPROVED_REAL_REPORT"));
                else fillable.add(Map.of("enterpriseOrgId",Long.toString(row.enterpriseId()),
                        "enterpriseName",row.enterpriseName(),"cityCode",row.cityCode(),
                        "regionCode",source.regionCode(),"source",Map.of(
                        "sourcePeriodCode",previous.period(),"sourceReportId",Long.toString(previous.reportId()),
                        "sourceSubmissionVersion",previous.submission(),
                        "sourceCurrentVersion",previous.version(),
                        "periodEmploymentCount",previous.employment())));
            }
        }
        entries.sort(Comparator.comparing(Entry::region).thenComparing(Entry::orgCode));
        int real=realItems.size(),proxy=proxyItems.size();
        int missing=(int)(eligible-real-proxy);
        if(period.legacy()) blocker(blockers,"LEGACY_PERIOD","旧演示调查期不可上报");
        if(Instant.now().isBefore(period.closeAt())) blocker(blockers,"WINDOW_NOT_CLOSED","填报窗口尚未关闭");
        if(eligible==0) blocker(blockers,"NO_ELIGIBLE_ENTERPRISE","本期没有应报企业");
        if(pendingCity>0) blocker(blockers,"PENDING_CITY","仍有市级待审报表");
        if(pendingProvince>0) blocker(blockers,"PENDING_PROVINCE","仍有省级待审报表");
        if(returnedCity>0) blocker(blockers,"RETURNED_CITY","仍有市级退回报表");
        if(returnedProvince>0) blocker(blockers,"RETURNED_PROVINCE","仍有省级退回报表");
        if(unapproved>0) blocker(blockers,"UNAPPROVED_REAL_REPORT","仍有未省审通过的真实报表");
        if(missing>0) blocker(blockers,"UNCOVERED_ENTERPRISE","仍有未覆盖企业");
        var existing=national.byPeriod(province,period.code()).orElse(null);
        if(existing!=null) blocker(blockers,"BATCH_EXISTS","本期已有批次");
        long baseline=entries.stream().mapToLong(Entry::baseline).sum();
        long current=entries.stream().mapToLong(Entry::current).sum();
        Map<String,Object> totals=Map.of("enterpriseCount",entries.size(),"realCount",real,
                "proxyCount",proxy,"baselineEmploymentTotal",baseline,
                "periodEmploymentTotal",current,"netChange",current-baseline);
        Map<String,Object> counts=Map.ofEntries(Map.entry("eligibleCount",eligible),
                Map.entry("realCount",real),Map.entry("proxyCount",proxy),
                Map.entry("missingCount",missing),Map.entry("fillableCount",fillable.size()),
                Map.entry("noSourceCount",noSource.size()),Map.entry("pendingCityCount",pendingCity),
                Map.entry("pendingProvinceCount",pendingProvince),Map.entry("returnedCityCount",returnedCity),
                Map.entry("returnedProvinceCount",returnedProvince),Map.entry("voidedCount",voided));
        Map<String,Object> view=new LinkedHashMap<>();
        view.put("period",Map.of("periodCode",period.code(),"periodLabel",period.label(),
                "startAt",period.startAt(),"endExclusiveAt",period.endExclusiveAt(),
                "closeAt",period.closeAt(),"windowStatus",period.windowStatus(Instant.now()),
                "legacy",period.legacy()));
        view.put("canSubmit",blockers.isEmpty());view.put("blockers",blockers);
        view.put("counts",counts);view.put("proxyVersion",queue.version(province,period.code()));
        view.put("realEnterprises",realItems);view.put("proxyItems",proxyItems);
        view.put("fillable",fillable);view.put("noRealSource",noSource);
        view.put("previewSummary",totals);
        view.put("existingBatch",existing==null?null:Map.of("batchId",existing.batchId(),
                "status",existing.status(),"payloadFormat",existing.payloadJson()==null?"XML":"JSON"));
        return new Snapshot(view,blockers,totals,entries,proxies,proxyItems,real);
    }
    private Map<Long,SourceRow> sources(long province,String period) {
        Map<Long,SourceRow> result=new HashMap<>();
        national.source(province,period).forEach(row->result.put(row.orgId(),row));
        return result;
    }
    private static Entry real(SourceRow r) {
        if(r.reportId()==null || r.submissionVersion()==null || r.currentVersion()==null ||
                r.baselineCount()==null || r.periodCount()==null || r.baselineSourcePeriodCode()==null)
            throw conflict("PAYLOAD_INVALID","真实报表缺少必填资料");
        return new Entry("REAL",nonblank(r.versionOrgCode(),r.currentOrgCode()),
                nonblank(r.versionEnterpriseName(),r.currentEnterpriseName()),r.regionCode(),
                nonblank(r.versionNatureCode(),r.currentNatureCode()),
                nonblank(r.versionIndustryCode(),r.currentIndustryCode()),r.reportId(),
                r.submissionVersion(),r.currentVersion(),r.baselineSourcePeriodCode(),null,
                r.baselineCount(),r.periodCount(),r.reductionTypeCode(),r.primaryReasonCode(),
                r.primaryReasonDetail(),r.otherReason());
    }
    private static Entry proxyEntry(Proxy p) {
        return new Entry("PROXY",p.orgCode(),p.name(),p.region(),p.nature(),p.industry(),
                p.sourceReport(),p.sourceSubmission(),p.sourceVersion(),null,p.sourcePeriod(),
                p.employment(),p.employment(),null,null,null,null);
    }
    private static Map<String,Object> proxyView(Proxy p) {
        return Map.ofEntries(Map.entry("enterpriseOrgId",Long.toString(p.orgId())),
                Map.entry("enterpriseName",p.name()),Map.entry("organizationCode",p.orgCode()),
                Map.entry("cityCode",p.region().substring(0,4)),Map.entry("regionCode",p.region()),
                Map.entry("proxySourcePeriodCode",p.sourcePeriod()),
                Map.entry("sourceReportId",Long.toString(p.sourceReport())),
                Map.entry("sourceSubmissionVersion",p.sourceSubmission()),
                Map.entry("sourceCurrentVersion",p.sourceVersion()),
                Map.entry("baselineEmploymentCount",p.employment()),
                Map.entry("periodEmploymentCount",p.employment()),Map.entry("netChange",0),
                Map.entry("generatedAt",p.generatedAt()));
    }
    private SurveyPeriod period(long province,String code,boolean lock) {
        if(code==null || !code.matches("[A-Z0-9_]{1,32}"))
            throw new FieldErrorException(Map.of("periodCode","期次代码不合法"));
        SurveyPeriod p=periods.byCode(code,lock).orElseThrow(NotFoundException::new);
        if(p.provinceOrgId()==null || p.provinceOrgId()!=province) throw new NotFoundException();
        return p;
    }
    private void afterCommit(String id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { sender.send(id); }
        });
    }
    private static String nonblank(String first,String fallback) {
        return first!=null && !first.isBlank()?first:fallback;
    }
    private static void blocker(List<Map<String,Object>> list,String code,String message) {
        list.add(Map.of("code",code,"message",message));
    }
    private static NationalApiException conflict(String code,String message) {
        return new NationalApiException(HttpStatus.CONFLICT,message,code,Map.of());
    }
    private record Snapshot(Map<String,Object> view,List<Map<String,Object>> blockers,
            Map<String,Object> totals,List<Entry> entries,List<Proxy> proxies,
            List<Map<String,Object>> proxyItems,int realCount) { }
}
