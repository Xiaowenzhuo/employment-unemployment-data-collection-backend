package com.example.employment.backend.week6.notice.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NoticeDao {
    private final JdbcTemplate jdbc;
    public NoticeDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public long nextId(String entity) {
        Long id = jdbc.queryForObject("SELECT next_id FROM id_allocator WHERE entity_name=? FOR UPDATE",
                Long.class, entity);
        if (id == null) throw new IllegalStateException("Missing allocator: " + entity);
        jdbc.update("UPDATE id_allocator SET next_id=? WHERE entity_name=?", id + 1, entity);
        return id;
    }
    public List<NoticeRow> list(long provinceId, Long cityId, long userId,
            String role, int size, int offset) {
        return jdbc.query("""
                SELECT * FROM province_notice WHERE province_org_id=? AND deleted=false
                  AND (publisher_level='PROVINCE' OR
                       (publisher_level='CITY' AND city_org_id=?
                        AND (?='ENTERPRISE' OR publisher_user_id=?)))
                ORDER BY published_at DESC, id DESC LIMIT ? OFFSET ?
                """, this::row, provinceId, cityId, role, userId, size, offset);
    }
    public long count(long provinceId, Long cityId, long userId, String role) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM province_notice WHERE province_org_id=? AND deleted=false
                  AND (publisher_level='PROVINCE' OR
                       (publisher_level='CITY' AND city_org_id=?
                        AND (?='ENTERPRISE' OR publisher_user_id=?)))
                """, Long.class, provinceId, cityId, role, userId);
        return count == null ? 0 : count;
    }
    public Optional<NoticeRow> find(long id, long provinceId, boolean includeDeleted,
            boolean lock) {
        return jdbc.query("SELECT * FROM province_notice WHERE id=? AND province_org_id=?"
                        + (includeDeleted ? "" : " AND deleted=false") + (lock ? " FOR UPDATE" : ""),
                this::row, id, provinceId).stream().findFirst();
    }
    public void insert(long id, long provinceId, long userId, String unitName,
            String title, String content, LocalDateTime now, String level, Long cityId) {
        jdbc.update("""
                INSERT INTO province_notice (id,province_org_id,publisher_user_id,
                    publisher_unit_name,title,content,current_version,published_at,
                    publisher_level,city_org_id)
                VALUES (?,?,?,?,?,?,1,?,?,?)
                """, id, provinceId, userId, unitName, title, content, now, level, cityId);
    }
    public int update(long id, long provinceId, int expected, String title, String content,
            LocalDateTime now) {
        return jdbc.update("""
                UPDATE province_notice SET title=?, content=?, updated_at=?,
                    current_version=current_version+1
                WHERE id=? AND province_org_id=? AND publisher_level='PROVINCE'
                  AND deleted=false AND current_version=?
                """, title, content, now, id, provinceId, expected);
    }
    public int delete(long id, long provinceId, int expected, LocalDateTime now) {
        return jdbc.update("""
                UPDATE province_notice SET deleted=true, deleted_at=?,
                    current_version=current_version+1
                WHERE id=? AND province_org_id=? AND publisher_level='PROVINCE'
                  AND deleted=false AND current_version=?
                """, now, id, provinceId, expected);
    }
    public int updateCity(long id, long cityId, long userId, int expected,
            String title, String content, LocalDateTime now) {
        return jdbc.update("""
                UPDATE province_notice SET title=?, content=?, updated_at=?,
                    current_version=current_version+1
                WHERE id=? AND city_org_id=? AND publisher_user_id=?
                  AND publisher_level='CITY' AND deleted=false AND current_version=?
                """, title, content, now, id, cityId, userId, expected);
    }
    public int deleteCity(long id, long cityId, long userId, int expected, LocalDateTime now) {
        return jdbc.update("""
                UPDATE province_notice SET deleted=true, deleted_at=?,
                    current_version=current_version+1
                WHERE id=? AND city_org_id=? AND publisher_user_id=?
                  AND publisher_level='CITY' AND deleted=false AND current_version=?
                """, now, id, cityId, userId, expected);
    }
    public void event(long id, long noticeId, String action, long actorId,
            int from, int to, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO province_notice_event (id,notice_id,action,actor_user_id,
                    from_version,to_version,occurred_at) VALUES (?,?,?,?,?,?,?)
                """, id, noticeId, action, actorId, from, to, now);
    }
    private NoticeRow row(ResultSet rs, int i) throws SQLException {
        return new NoticeRow(rs.getLong("id"), rs.getLong("province_org_id"),
                rs.getLong("publisher_user_id"), rs.getString("publisher_unit_name"),
                rs.getString("title"), rs.getString("content"), rs.getInt("current_version"),
                instant(rs, "published_at"), instant(rs, "updated_at"),
                rs.getBoolean("deleted"), instant(rs, "deleted_at"),
                rs.getString("publisher_level"), (Long) rs.getObject("city_org_id"));
    }
    private static Instant instant(ResultSet rs, String field) throws SQLException {
        LocalDateTime time = rs.getObject(field, LocalDateTime.class);
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
    public record NoticeRow(long id, long provinceOrgId, long publisherUserId,
            String publisherUnitName, String title, String content, int currentVersion,
            Instant publishedAt, Instant updatedAt, boolean deleted, Instant deletedAt,
            String publisherLevel, Long cityOrgId) { }
}
