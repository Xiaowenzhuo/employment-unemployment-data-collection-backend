package com.example.employment.backend.reminder.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReminderDao {
    private final JdbcTemplate jdbc;
    public ReminderDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Map<String,Long> cityIds(long province) {
        Map<String,Long> result=new java.util.HashMap<>();
        jdbc.query("SELECT id,region_code FROM org_unit WHERE parent_id=? AND org_type='CITY'",
                rs->{result.put(rs.getString("region_code"),rs.getLong("id"));},province);
        return result;
    }
    public void preview(String id,long actor,long province,String period,String audience,String hash,
            int cities,int enterprises,LocalDateTime expires) {
        jdbc.update("""
                INSERT INTO notice_reminder_preview
                (preview_id,actor_user_id,province_org_id,period_code,audience,
                 recipient_hash,city_count,enterprise_count,expires_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,id,actor,province,period,audience,hash,cities,enterprises,expires);
    }
    public Optional<Preview> preview(String id) {
        return jdbc.query("SELECT * FROM notice_reminder_preview WHERE preview_id=? FOR UPDATE",
                (rs,i)->new Preview(rs.getString("preview_id"),rs.getLong("actor_user_id"),
                        rs.getLong("province_org_id"),rs.getString("period_code"),
                        rs.getString("audience"),rs.getString("recipient_hash"),
                        rs.getInt("city_count"),rs.getInt("enterprise_count"),
                        rs.getObject("expires_at",LocalDateTime.class),
                        (Long)rs.getObject("notice_id"),rs.getString("content_hash")),id)
                .stream().findFirst();
    }
    public void targeted(long notice) {
        jdbc.update("UPDATE province_notice SET targeted=true WHERE id=?",notice);
    }
    public void recipients(long notice,List<Long> recipients) {
        for(long org:recipients)
            jdbc.update("INSERT INTO notice_recipient(notice_id,org_id) VALUES (?,?)",notice,org);
    }
    public void sent(String preview,long notice,String contentHash) {
        jdbc.update("UPDATE notice_reminder_preview SET notice_id=?,content_hash=? "
                + "WHERE preview_id=? AND notice_id IS NULL",notice,contentHash,preview);
    }
    public record Preview(String id,long actor,long province,String period,String audience,
            String hash,int cities,int enterprises,LocalDateTime expires,Long noticeId,
            String contentHash) { }
}
