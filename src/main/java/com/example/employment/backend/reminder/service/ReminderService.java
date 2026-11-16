package com.example.employment.backend.reminder.service;

import com.example.employment.backend.common.ConflictException;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.reminder.dao.ReminderDao.Preview;
import com.example.employment.backend.reminder.repository.ReminderRepository;
import com.example.employment.backend.revision.dao.ProgressDao.Row;
import com.example.employment.backend.revision.repository.ProgressRepository;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.week6.notice.repository.NoticeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {
    private final ReminderRepository reminders;
    private final ProgressRepository progress;
    private final PeriodRepository periods;
    private final ScopeService scopes;
    private final NoticeRepository notices;
    public ReminderService(ReminderRepository reminders,ProgressRepository progress,
            PeriodRepository periods,ScopeService scopes,NoticeRepository notices) {
        this.reminders=reminders;this.progress=progress;this.periods=periods;
        this.scopes=scopes;this.notices=notices;
    }
    @Transactional
    public PreviewResult preview(UserAccount actor,String period,String audience) {
        long province=scopes.province(actor).id();
        if(audience==null || !Set.of("CITIES","ENTERPRISES","BOTH").contains(audience))
            throw field("audience","范围须为 CITIES、ENTERPRISES 或 BOTH");
        checkPeriod(province,period);
        Recipients recipients=recipients(province,period,audience);
        String id=UUID.randomUUID().toString();
        reminders.preview(id,actor.id(),province,period,audience,recipients.hash(),
                recipients.cities(),recipients.enterprises(),now().plusMinutes(15));
        return new PreviewResult(id,recipients.cities(),recipients.enterprises());
    }
    @Transactional
    public SendResult send(UserAccount actor,String previewId,String title,String content) {
        if(previewId==null || !previewId.matches("[0-9a-fA-F-]{36}"))
            throw field("previewId","预览令牌不合法");
        String cleanTitle=text(title,"title",50),cleanContent=text(content,"content",2000);
        long province=scopes.province(actor).id();
        Preview preview=reminders.preview(previewId).orElseThrow(ReminderService::conflict);
        if(preview.actor()!=actor.id() || preview.province()!=province
                || !now().isBefore(preview.expires())) throw conflict();
        String bodyHash=sha(cleanTitle+"\n"+cleanContent);
        if(preview.noticeId()!=null) {
            if(!bodyHash.equals(preview.contentHash())) throw conflict();
            return new SendResult(Long.toString(preview.noticeId()),preview.cities(),
                    preview.enterprises());
        }
        checkPeriod(province,preview.period());
        Recipients current=recipients(province,preview.period(),preview.audience());
        if(current.ids().isEmpty() || !current.hash().equals(preview.hash())) throw conflict();
        LocalDateTime now=now();
        long notice=notices.nextNoticeId();
        notices.insert(notice,province,actor.id(),actor.orgName(),cleanTitle,cleanContent,
                now,"PROVINCE",null);
        reminders.targeted(notice);
        reminders.recipients(notice,current.ids());
        notices.event(notices.nextEventId(),notice,"CREATE",actor.id(),0,1,now);
        reminders.sent(previewId,notice,bodyHash);
        return new SendResult(Long.toString(notice),current.cities(),current.enterprises());
    }
    private Recipients recipients(long province,String period,String audience) {
        Map<String,Long> cityIds=reminders.cityIds(province);
        Set<Long> cities=new java.util.HashSet<>(),enterprises=new java.util.HashSet<>();
        for(Row row:progress.rows(province,period)) {
            if(!row.eligible() || row.realSubmitted()) continue;
            Long city=cityIds.get(row.cityCode());
            if(city==null) continue;
            cities.add(city);
            enterprises.add(row.enterpriseId());
        }
        int cityCount=audience.equals("ENTERPRISES")?0:cities.size();
        int enterpriseCount=audience.equals("CITIES")?0:enterprises.size();
        List<Long> ids=new ArrayList<>();
        if(!audience.equals("ENTERPRISES")) ids.addAll(cities);
        if(!audience.equals("CITIES")) ids.addAll(enterprises);
        ids.sort(Comparator.naturalOrder());
        return new Recipients(ids,cityCount,enterpriseCount,sha(ids.toString()));
    }
    private void checkPeriod(long province,String code) {
        if(code==null || code.isBlank()) throw field("periodCode","必填");
        var period=periods.byCode(code,false).orElseThrow(NotFoundException::new);
        if(period.provinceOrgId()==null || period.provinceOrgId()!=province)
            throw new NotFoundException();
    }
    private static String text(String raw,String field,int max) {
        if(raw==null || raw.trim().isEmpty()) throw field(field,"必填");
        String value=raw.trim();
        if(value.codePointCount(0,value.length())>max) throw field(field,"最多"+max+"字");
        return value;
    }
    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
    private static String sha(String value) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static FieldErrorException field(String key,String message) {
        return new FieldErrorException(Map.of(key,message));
    }
    private static ConflictException conflict() {
        return new ConflictException("预览已失效或收件范围已变化，请重新预览");
    }
    private record Recipients(List<Long> ids,int cities,int enterprises,String hash) { }
    public record PreviewResult(String previewId,int cityCount,int enterpriseCount) { }
    public record SendResult(String noticeId,int cityCount,int enterpriseCount) { }
}
