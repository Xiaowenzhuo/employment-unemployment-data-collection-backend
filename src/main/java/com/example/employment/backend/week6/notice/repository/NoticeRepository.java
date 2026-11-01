package com.example.employment.backend.week6.notice.repository;

import com.example.employment.backend.week6.notice.dao.NoticeDao;
import com.example.employment.backend.week6.notice.dao.NoticeDao.NoticeRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class NoticeRepository {
    private final NoticeDao dao;
    public NoticeRepository(NoticeDao dao) { this.dao = dao; }
    public long nextNoticeId() { return dao.nextId("province_notice"); }
    public long nextEventId() { return dao.nextId("province_notice_event"); }
    public List<NoticeRow> list(long province, int size, int offset) { return dao.list(province,size,offset); }
    public long count(long province) { return dao.count(province); }
    public Optional<NoticeRow> find(long id, long province, boolean deleted, boolean lock) {
        return dao.find(id,province,deleted,lock);
    }
    public void insert(long id, long province, long user, String unit, String title,
            String content, LocalDateTime now) { dao.insert(id,province,user,unit,title,content,now); }
    public int update(long id, long province, int expected, String title, String content,
            LocalDateTime now) { return dao.update(id,province,expected,title,content,now); }
    public int delete(long id, long province, int expected, LocalDateTime now) {
        return dao.delete(id,province,expected,now);
    }
    public void event(long id, long notice, String action, long actor,
            int from, int to, LocalDateTime now) { dao.event(id,notice,action,actor,from,to,now); }
}
