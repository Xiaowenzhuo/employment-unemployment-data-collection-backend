package com.example.employment.backend.week78.role.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RoleDao {
    private final JdbcTemplate jdbc;

    public RoleDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PermissionRow> permissions() {
        return jdbc.query("""
                SELECT code,name,level_code,feature,operation FROM permission
                ORDER BY CASE level_code WHEN 'PROVINCE' THEN 0 WHEN 'CITY' THEN 1 ELSE 2 END,code
                """, (rs, row) -> new PermissionRow(rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5)));
    }

    public Optional<RoleRow> find(long id, long provinceId, boolean lock) {
        String sql = """
                SELECT id,code,name,level_code,system_protected,version,owner_province_org_id
                FROM `role` WHERE id=? AND role_kind='FUNCTION' AND deleted_at IS NULL
                AND (owner_province_org_id IS NULL OR owner_province_org_id=?)
                """ + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, row) -> role(rs), id, provinceId).stream().findFirst();
    }

    public List<RoleRow> list(long provinceId, String level, String keyword, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = filter(provinceId, level, keyword, args);
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("""
                SELECT id,code,name,level_code,system_protected,version,owner_province_org_id
                FROM `role`
                """ + where + """
                ORDER BY CASE level_code WHEN 'PROVINCE' THEN 0 WHEN 'CITY' THEN 1 ELSE 2 END,
                system_protected DESC,name,id LIMIT ? OFFSET ?
                """, (rs, row) -> role(rs), args.toArray());
    }

    public long count(long provinceId, String level, String keyword) {
        List<Object> args = new ArrayList<>();
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM `role`" +
                filter(provinceId, level, keyword, args), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private String filter(long provinceId, String level, String keyword, List<Object> args) {
        StringBuilder where = new StringBuilder("""
                WHERE role_kind='FUNCTION' AND deleted_at IS NULL
                AND (owner_province_org_id IS NULL OR owner_province_org_id=?)
                """);
        args.add(provinceId);
        if (level != null) {
            where.append(" AND level_code=?");
            args.add(level);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        return " " + where;
    }

    private RoleRow role(ResultSet rs) throws SQLException {
        return new RoleRow(rs.getLong("id"),rs.getString("code"),rs.getString("name"),
                rs.getString("level_code"),rs.getBoolean("system_protected"),
                rs.getLong("version"),(Long)rs.getObject("owner_province_org_id"));
    }

    public List<String> permissionCodes(long roleId) {
        return jdbc.query("SELECT permission_code FROM role_permission WHERE role_id=? ORDER BY permission_code",
                (rs, row) -> rs.getString(1), roleId);
    }

    public long assignedCount(long roleId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_role ur JOIN user_account u ON u.id=ur.user_id
                WHERE ur.role_id=? AND u.deleted_at IS NULL
                """, Long.class, roleId);
        return count == null ? 0 : count;
    }

    public List<Long> assignedUserIds(long roleId) {
        return jdbc.query("""
                SELECT u.id FROM user_role ur JOIN user_account u ON u.id=ur.user_id
                WHERE ur.role_id=? AND u.deleted_at IS NULL ORDER BY u.id
                """, (rs, row) -> rs.getLong(1), roleId);
    }

    public boolean nameExists(long provinceId, String level, String name, long exceptId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM `role` WHERE owner_province_org_id=? AND level_code=?
                AND LOWER(name)=LOWER(?) AND id<>?
                """, Long.class, provinceId, level, name, exceptId);
        return count != null && count > 0;
    }

    public long nextId() {
        Long id = jdbc.queryForObject("SELECT next_id FROM id_allocator WHERE entity_name='role' FOR UPDATE",
                Long.class);
        if (id == null) throw new IllegalStateException("Missing role allocator");
        jdbc.update("UPDATE id_allocator SET next_id=? WHERE entity_name='role'", id + 1);
        return id;
    }

    public void create(long id, String name, String level, long provinceId) {
        jdbc.update("""
                INSERT INTO `role` (id,code,name,role_kind,level_code,owner_province_org_id,
                  system_protected,version)
                VALUES (?, ?, ?, 'FUNCTION', ?, ?, false, 1)
                """, id, "CUSTOM_" + id, name, level, provinceId);
    }

    public void replacePermissions(long roleId, List<String> codes) {
        jdbc.update("DELETE FROM role_permission WHERE role_id=?", roleId);
        for (String code : codes) {
            jdbc.update("INSERT INTO role_permission (role_id,permission_code) VALUES (?,?)", roleId, code);
        }
    }

    public void update(long roleId, String name, long version) {
        jdbc.update("UPDATE `role` SET name=?,version=version+1 WHERE id=? AND version=?",
                name, roleId, version);
    }

    public void replaceAndDelete(long roleId, Long replacementId, List<Long> users,
            LocalDateTime now, long version) {
        for (long userId : users) {
            Long existing = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM user_role WHERE user_id=? AND role_id=?
                    """, Long.class, userId, replacementId);
            if (existing == null || existing == 0) {
                jdbc.update("INSERT INTO user_role (user_id,role_id) VALUES (?,?)",
                        userId, replacementId);
            }
            jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=?", userId, roleId);
            jdbc.update("UPDATE user_account SET version=version+1 WHERE id=?", userId);
        }
        jdbc.update("UPDATE `role` SET deleted_at=?,version=version+1 WHERE id=? AND version=?",
                now, roleId, version);
    }

    public void audit(long actorId, String action, long roleId, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO admin_audit_event (actor_user_id,action,target_role_id,event_at)
                VALUES (?,?,?,?)
                """, actorId, action, roleId, now);
    }

    public record PermissionRow(String code, String name, String level, String feature,
            String operation) {
    }

    public record RoleRow(long id, String code, String name, String level,
            boolean protectedRole, long version, Long ownerProvinceId) {
    }
}
