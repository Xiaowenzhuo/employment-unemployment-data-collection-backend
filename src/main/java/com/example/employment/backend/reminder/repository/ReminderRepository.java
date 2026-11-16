package com.example.employment.backend.reminder.repository;

import com.example.employment.backend.reminder.dao.ReminderDao;
import com.example.employment.backend.reminder.dao.ReminderDao.Preview;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ReminderRepository {
    private final ReminderDao dao;
    public ReminderRepository(ReminderDao dao) { this.dao=dao; }
    public Map<String,Long> cityIds(long province) { return dao.cityIds(province); }
    public void preview(String id,long actor,long province,String period,String audience,String hash,
            int cities,int enterprises,LocalDateTime expires) {
        dao.preview(id,actor,province,period,audience,hash,cities,enterprises,expires);
    }
    public Optional<Preview> preview(String id) { return dao.preview(id); }
    public void targeted(long notice) { dao.targeted(notice); }
    public void recipients(long notice,List<Long> orgs) { dao.recipients(notice,orgs); }
    public void sent(String preview,long notice,String hash) { dao.sent(preview,notice,hash); }
}
