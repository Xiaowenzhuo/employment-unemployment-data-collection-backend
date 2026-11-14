package com.example.employment.backend.week78.user.repository;

import com.example.employment.backend.week78.user.dao.AdminUserDao;
import com.example.employment.backend.week78.user.dao.AdminUserDao.OrgRow;
import com.example.employment.backend.week78.user.dao.AdminUserDao.UserRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {
    private final AdminUserDao dao;

    public AdminUserRepository(AdminUserDao dao) { this.dao = dao; }
    public Optional<OrgRow> organization(long id, long provinceId) {
        return dao.organization(id, provinceId);
    }
    public List<OrgRow> organizations(long provinceId, String type, String keyword,
            int page, int size) { return dao.organizations(provinceId, type, keyword, page, size); }
    public long organizationCount(long provinceId, String type, String keyword) {
        return dao.organizationCount(provinceId, type, keyword);
    }
    public Optional<UserRow> user(long id, long provinceId, boolean lock) {
        return dao.user(id, provinceId, lock);
    }
    public List<UserRow> users(long provinceId, Long orgId, String roleCode, Boolean enabled,
            String keyword, Long roleId, int page, int size) {
        return dao.users(provinceId, orgId, roleCode, enabled, keyword, roleId, page, size);
    }
    public long userCount(long provinceId, Long orgId, String roleCode, Boolean enabled,
            String keyword, Long roleId) {
        return dao.userCount(provinceId, orgId, roleCode, enabled, keyword, roleId);
    }
    public boolean usernameExists(String name) { return dao.usernameExists(name); }
    public long nextId() { return dao.nextId(); }
    public void create(long id, long orgId, String username, String name, String hash,
            String identity, List<Long> roleIds, LocalDateTime now) {
        dao.create(id, orgId, username, name, hash, identity, roleIds, now);
    }
    public void update(long id, String name, long version, List<Long> roleIds) {
        dao.update(id, name, version, roleIds);
    }
    public void status(long id, boolean enabled, long version) {
        dao.status(id, enabled, version);
    }
    public void reset(long id, String hash, long version) { dao.reset(id, hash, version); }
    public void delete(long id, long version, LocalDateTime now) {
        dao.delete(id, version, now);
    }
    public List<String> historyTypes(long id) { return dao.historyTypes(id); }
    public void audit(long actorId, String action, long targetId, LocalDateTime now) {
        dao.audit(actorId, action, targetId, now);
    }
}
