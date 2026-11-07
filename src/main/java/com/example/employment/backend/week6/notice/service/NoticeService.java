package com.example.employment.backend.week6.notice.service;

import com.example.employment.backend.common.ConflictException;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.ForbiddenException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week6.notice.dao.NoticeDao.NoticeRow;
import com.example.employment.backend.week6.notice.repository.NoticeRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeService {
    private final NoticeRepository notices;
    private final RegionScopeRepository regions;
    private final ScopeService scopes;
    public NoticeService(NoticeRepository notices, RegionScopeRepository regions,
            ScopeService scopes) {
        this.notices = notices; this.regions = regions; this.scopes = scopes;
    }
    public NoticePage list(UserAccount actor, int page, int size) {
        NoticeScope scope = scope(actor);
        if (page < 0 || (long)page * size > Integer.MAX_VALUE)
            throw field("page", "页码不合法");
        if (size < 1 || size > 100) throw field("size", "每页应为1至100条");
        return new NoticePage(notices.list(scope.provinceId(),scope.cityId(),actor.id(),
                scope.role(),size,page*size).stream()
                .map(row -> summary(row,canEdit(row,actor,scope))).toList(),
                notices.count(scope.provinceId(),scope.cityId(),actor.id(),scope.role()),page,size);
    }
    public NoticeDetail detail(UserAccount actor, String rawId) {
        NoticeScope scope = scope(actor);
        NoticeRow row = notices.find(id(rawId),scope.provinceId(),false,false)
                .orElseThrow(NotFoundException::new);
        if (!visible(row,actor,scope)) throw new NotFoundException();
        return detail(row,canEdit(row,actor,scope));
    }
    @Transactional
    public NoticeDetail create(UserAccount actor, String title, String content) {
        Org province = scopes.province(actor);
        String cleanTitle = text(title,"title",50);
        String cleanContent = text(content,"content",2000);
        LocalDateTime now = now();
        long id = notices.nextNoticeId();
        notices.insert(id,province.id(),actor.id(),actor.orgName(),cleanTitle,cleanContent,
                now,"PROVINCE",null);
        notices.event(notices.nextEventId(),id,"CREATE",actor.id(),0,1,now);
        return detail(notices.find(id,province.id(),false,false).orElseThrow(),true);
    }
    @Transactional
    public NoticeDetail update(UserAccount actor, String rawId, Integer expected,
            String title, String content) {
        Org province = scopes.province(actor);
        int version = version(expected);
        String cleanTitle = text(title,"title",50);
        String cleanContent = text(content,"content",2000);
        long id = id(rawId);
        NoticeRow current = notices.find(id,province.id(),true,true)
                .orElseThrow(NotFoundException::new);
        if (!"PROVINCE".equals(current.publisherLevel())) throw new NotFoundException();
        if (current.deleted() || current.currentVersion()!=version) throw conflict();
        LocalDateTime now = now();
        if (notices.update(id,province.id(),version,cleanTitle,cleanContent,now)!=1)
            throw conflict();
        notices.event(notices.nextEventId(),id,"UPDATE",actor.id(),version,version+1,now);
        return detail(notices.find(id,province.id(),false,false).orElseThrow(),true);
    }
    @Transactional
    public DeleteResult delete(UserAccount actor, String rawId, Integer expected) {
        Org province = scopes.province(actor);
        int version = version(expected);
        long id = id(rawId);
        NoticeRow current = notices.find(id,province.id(),true,true)
                .orElseThrow(NotFoundException::new);
        if (!"PROVINCE".equals(current.publisherLevel())) throw new NotFoundException();
        if (current.deleted() || current.currentVersion()!=version) throw conflict();
        LocalDateTime now = now();
        if (notices.delete(id,province.id(),version,now)!=1) throw conflict();
        notices.event(notices.nextEventId(),id,"DELETE",actor.id(),version,version+1,now);
        return new DeleteResult(rawId,version+1,true,now.toInstant(ZoneOffset.UTC));
    }
    @Transactional
    public NoticeDetail createCity(UserAccount actor,String title,String content) {
        NoticeScope scope=scope(actor);
        if (!"CITY".equals(scope.role())) throw new ForbiddenException();
        String cleanTitle=text(title,"title",50),cleanContent=text(content,"content",2000);
        LocalDateTime now=now();
        long id=notices.nextNoticeId();
        notices.insert(id,scope.provinceId(),actor.id(),actor.orgName(),
                cleanTitle,cleanContent,now,"CITY",scope.cityId());
        notices.event(notices.nextEventId(),id,"CREATE",actor.id(),0,1,now);
        return detail(notices.find(id,scope.provinceId(),false,false).orElseThrow(),true);
    }
    @Transactional
    public NoticeDetail updateCity(UserAccount actor,String rawId,Integer expected,
            String title,String content) {
        NoticeScope scope=scope(actor);
        if (!"CITY".equals(scope.role())) throw new ForbiddenException();
        int version=version(expected);
        String cleanTitle=text(title,"title",50),cleanContent=text(content,"content",2000);
        long id=id(rawId);
        NoticeRow current=notices.find(id,scope.provinceId(),true,true)
                .orElseThrow(NotFoundException::new);
        if (!canEdit(current,actor,scope)) throw new NotFoundException();
        if (current.deleted() || current.currentVersion()!=version) throw conflict();
        LocalDateTime now=now();
        if (notices.updateCity(id,scope.cityId(),actor.id(),version,cleanTitle,cleanContent,now)!=1)
            throw conflict();
        notices.event(notices.nextEventId(),id,"UPDATE",actor.id(),version,version+1,now);
        return detail(notices.find(id,scope.provinceId(),false,false).orElseThrow(),true);
    }
    @Transactional
    public DeleteResult deleteCity(UserAccount actor,String rawId,Integer expected) {
        NoticeScope scope=scope(actor);
        if (!"CITY".equals(scope.role())) throw new ForbiddenException();
        int version=version(expected), idVersion=version+1;
        long id=id(rawId);
        NoticeRow current=notices.find(id,scope.provinceId(),true,true)
                .orElseThrow(NotFoundException::new);
        if (!canEdit(current,actor,scope)) throw new NotFoundException();
        if (current.deleted() || current.currentVersion()!=version) throw conflict();
        LocalDateTime now=now();
        if (notices.deleteCity(id,scope.cityId(),actor.id(),version,now)!=1) throw conflict();
        notices.event(notices.nextEventId(),id,"DELETE",actor.id(),version,idVersion,now);
        return new DeleteResult(rawId,idVersion,true,now.toInstant(ZoneOffset.UTC));
    }
    private NoticeScope scope(UserAccount actor) {
        Org org = regions.org(actor.orgId()).orElseThrow(ForbiddenException::new);
        if (!org.enabled() || !actor.roleCodes().contains(org.type())) throw new ForbiddenException();
        if ("PROVINCE".equals(org.type())) return new NoticeScope(org.id(),null,"PROVINCE");
        Org city = org;
        if ("ENTERPRISE".equals(org.type())) {
            city = parent(org);
            if (!"CITY".equals(city.type()) || !city.enabled()) throw new ForbiddenException();
        } else if (!"CITY".equals(org.type())) throw new ForbiddenException();
        Org province = parent(city);
        if (!"PROVINCE".equals(province.type()) || !province.enabled())
            throw new ForbiddenException();
        return new NoticeScope(province.id(),city.id(),org.type());
    }
    private Org parent(Org child) {
        if (child.parentId()==null) throw new ForbiddenException();
        return regions.org(child.parentId()).orElseThrow(ForbiddenException::new);
    }
    private static boolean visible(NoticeRow row,UserAccount actor,NoticeScope scope) {
        return "PROVINCE".equals(row.publisherLevel()) ||
                ("CITY".equals(row.publisherLevel()) && scope.cityId()!=null
                 && scope.cityId().equals(row.cityOrgId())
                 && ("ENTERPRISE".equals(scope.role()) || row.publisherUserId()==actor.id()));
    }
    private static boolean canEdit(NoticeRow row,UserAccount actor,NoticeScope scope) {
        return ("PROVINCE".equals(scope.role()) && "PROVINCE".equals(row.publisherLevel()))
                || ("CITY".equals(scope.role()) && "CITY".equals(row.publisherLevel())
                    && scope.cityId().equals(row.cityOrgId()) && row.publisherUserId()==actor.id());
    }
    private static NoticeSummary summary(NoticeRow row, boolean editable) {
        return new NoticeSummary(Long.toString(row.id()),row.title(),
                row.publisherLevel(),Long.toString("CITY".equals(row.publisherLevel())
                        ? row.cityOrgId() : row.provinceOrgId()),row.publisherUnitName(),
                row.publishedAt(),row.updatedAt(),row.currentVersion(),editable,editable);
    }
    private static NoticeDetail detail(NoticeRow row, boolean editable) {
        return new NoticeDetail(Long.toString(row.id()),row.title(),
                row.publisherLevel(),Long.toString("CITY".equals(row.publisherLevel())
                        ? row.cityOrgId() : row.provinceOrgId()),row.publisherUnitName(),
                row.publishedAt(),row.updatedAt(),row.currentVersion(),editable,editable,
                row.content(),Long.toString(row.publisherUserId()));
    }
    private static String text(String raw, String key, int max) {
        if (raw == null || raw.trim().isEmpty()) throw field(key,"必填");
        String value = raw.trim();
        if (value.codePointCount(0,value.length())>max) throw field(key,"最多"+max+"字");
        return value;
    }
    private static int version(Integer expected) {
        if (expected==null || expected<1) throw field("expectedVersion","应为正整数");
        return expected;
    }
    private static long id(String raw) {
        if (raw==null || !raw.matches("[1-9][0-9]*")) throw field("noticeId","应为正整数");
        try { return Long.parseLong(raw); }
        catch (NumberFormatException ex) { throw field("noticeId","应为正整数"); }
    }
    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
    private static ConflictException conflict() {
        return new ConflictException("通知状态或版本已变化，请刷新后重试");
    }
    private static FieldErrorException field(String key, String message) {
        return new FieldErrorException(Map.of(key,message));
    }
    private record NoticeScope(long provinceId,Long cityId,String role) { }
    public record NoticeSummary(String noticeId,String title,String publisherLevel,String publisherOrgId,
            String publisherUnitName,Instant publishedAt,Instant updatedAt,int currentVersion,
            boolean canEdit,boolean canDelete) { }
    public record NoticeDetail(String noticeId,String title,String publisherLevel,String publisherOrgId,
            String publisherUnitName,Instant publishedAt,Instant updatedAt,int currentVersion,
            boolean canEdit,boolean canDelete,String content,String publisherUserId) { }
    public record NoticePage(List<NoticeSummary> items,long total,int page,int size) { }
    public record DeleteResult(String noticeId,int currentVersion,boolean deleted,
            Instant deletedAt) { }
}
