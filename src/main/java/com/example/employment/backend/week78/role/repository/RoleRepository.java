package com.example.employment.backend.week78.role.repository;

import com.example.employment.backend.week78.role.dao.RoleDao;
import com.example.employment.backend.week78.role.dao.RoleDao.PermissionRow;
import com.example.employment.backend.week78.role.dao.RoleDao.RoleRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepository {
    private final RoleDao dao;

    public RoleRepository(RoleDao dao) {
        this.dao = dao;
    }

    public List<PermissionRow> permissions() { return dao.permissions(); }
    public Optional<RoleRow> find(long id, long provinceId, boolean lock) {
        return dao.find(id, provinceId, lock);
    }
    public List<RoleRow> list(long provinceId, String level, String keyword, int page, int size) {
        return dao.list(provinceId, level, keyword, page, size);
    }
    public long count(long provinceId, String level, String keyword) {
        return dao.count(provinceId, level, keyword);
    }
    public List<String> permissionCodes(long id) { return dao.permissionCodes(id); }
    public long assignedCount(long id) { return dao.assignedCount(id); }
    public List<Long> assignedUserIds(long id) { return dao.assignedUserIds(id); }
    public boolean nameExists(long provinceId, String level, String name, long exceptId) {
        return dao.nameExists(provinceId, level, name, exceptId);
    }
    public long nextId() { return dao.nextId(); }
    public void create(long id, String name, String level, long provinceId) {
        dao.create(id, name, level, provinceId);
    }
    public void replacePermissions(long id, List<String> codes) {
        dao.replacePermissions(id, codes);
    }
    public void update(long id, String name, long version) { dao.update(id, name, version); }
    public void replaceAndDelete(long id, Long replacementId, List<Long> users,
            LocalDateTime now, long version) {
        dao.replaceAndDelete(id, replacementId, users, now, version);
    }
    public void audit(long actorId, String action, long id, LocalDateTime now) {
        dao.audit(actorId, action, id, now);
    }
}
