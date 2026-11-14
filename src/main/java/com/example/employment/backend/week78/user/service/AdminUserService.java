package com.example.employment.backend.week78.user.service;

import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week1.identity.repository.UserRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week78.role.service.RoleService;
import com.example.employment.backend.week78.shared.AdminConflictException;
import com.example.employment.backend.week78.shared.AdminGuard;
import com.example.employment.backend.week78.shared.AdminInputs;
import com.example.employment.backend.week78.user.dao.AdminUserDao.OrgRow;
import com.example.employment.backend.week78.user.dao.AdminUserDao.UserRow;
import com.example.employment.backend.week78.user.repository.AdminUserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final AdminUserRepository users;
    private final UserRepository authUsers;
    private final RoleService roles;
    private final ScopeService scopes;
    private final AdminGuard guard;
    private final PasswordEncoder passwords;

    public AdminUserService(AdminUserRepository users, UserRepository authUsers,
            RoleService roles, ScopeService scopes, AdminGuard guard, PasswordEncoder passwords) {
        this.users = users;
        this.authUsers = authUsers;
        this.roles = roles;
        this.scopes = scopes;
        this.guard = guard;
        this.passwords = passwords;
    }

    public OrgPage organizations(UserAccount actor, String type, String keyword,
            Integer page, Integer size) {
        long provinceId = scopes.province(actor).id();
        if (type != null) checkLevel(type);
        checkKeyword(keyword, 120);
        int p = AdminInputs.page(page), s = AdminInputs.size(size);
        return new OrgPage(users.organizations(provinceId, type, keyword, p, s).stream()
                .map(this::orgView).toList(), p, s,
                users.organizationCount(provinceId, type, keyword));
    }

    public UserPage list(UserAccount actor, String orgId, String roleCode, Boolean enabled,
            String keyword, String functionalRoleId, Integer page, Integer size) {
        long provinceId = scopes.province(actor).id();
        if (roleCode != null) checkLevel(roleCode);
        checkKeyword(keyword, 80);
        Long organization = orgId == null ? null : AdminInputs.id(orgId, "orgId");
        if (organization != null && users.organization(organization, provinceId).isEmpty()) {
            throw new NotFoundException();
        }
        Long role = functionalRoleId == null ? null
                : AdminInputs.id(functionalRoleId, "functionalRoleId");
        if (role != null) roles.detail(actor, role);
        int p = AdminInputs.page(page), s = AdminInputs.size(size);
        return new UserPage(users.users(provinceId, organization, roleCode, enabled,
                keyword, role, p, s).stream().map(this::view).toList(), p, s,
                users.userCount(provinceId, organization, roleCode, enabled, keyword, role));
    }

    public UserView detail(UserAccount actor, long id) {
        long provinceId = scopes.province(actor).id();
        return view(users.user(id, provinceId, false).orElseThrow(NotFoundException::new));
    }

    @Transactional
    public UserView create(UserAccount actor, Map<String, Object> body) {
        long provinceId = scopes.province(actor).id();
        long orgId = AdminInputs.id(AdminInputs.text(body, "orgId", 20), "orgId");
        String username = AdminInputs.text(body, "username", 64);
        String name = AdminInputs.text(body, "displayName", 80);
        String password = AdminInputs.password(body, "initialPassword");
        List<String> roleIds = AdminInputs.strings(body, "functionalRoleIds");
        guard.lockProvince(provinceId);
        OrgRow org = users.organization(orgId, provinceId).orElseThrow(NotFoundException::new);
        if (!org.enabled()) throw new NotFoundException();
        roles.validateAssignment(provinceId, org.type(), roleIds);
        long id = users.nextId();
        if (users.usernameExists(username)) {
            throw new AdminConflictException("USERNAME_EXISTS", "用户名已存在");
        }
        try {
            users.create(id, orgId, username, name, passwords.encode(password),
                    org.type(), roleIds.stream().map(v -> AdminInputs.id(v, "functionalRoleIds"))
                            .toList(), now());
        } catch (DuplicateKeyException exception) {
            throw new AdminConflictException("USERNAME_EXISTS", "用户名已存在");
        }
        users.audit(actor.id(), "USER_CREATE", id, now());
        return detail(actor, id);
    }

    @Transactional
    public UserView update(UserAccount actor, long id, Map<String, Object> body) {
        long provinceId = scopes.province(actor).id();
        String name = AdminInputs.text(body, "displayName", 80);
        List<String> roleIds = AdminInputs.strings(body, "functionalRoleIds");
        long version = AdminInputs.version(body);
        guard.lockProvince(provinceId);
        UserRow current = locked(id, provinceId, version);
        roles.validateAssignment(provinceId, current.org().type(), roleIds);
        users.update(id, name, version,
                roleIds.stream().map(v -> AdminInputs.id(v, "functionalRoleIds")).toList());
        guard.ensureOne(provinceId);
        users.audit(actor.id(), "USER_UPDATE", id, now());
        return detail(actor, id);
    }

    @Transactional
    public UserView status(UserAccount actor, long id, Map<String, Object> body) {
        long provinceId = scopes.province(actor).id();
        boolean enabled = AdminInputs.enabled(body);
        long version = AdminInputs.version(body);
        guard.lockProvince(provinceId);
        locked(id, provinceId, version);
        users.status(id, enabled, version);
        guard.ensureOne(provinceId);
        users.audit(actor.id(), enabled ? "USER_ENABLE" : "USER_DISABLE", id, now());
        return detail(actor, id);
    }

    @Transactional
    public UserView resetPassword(UserAccount actor, long id, Map<String, Object> body) {
        long provinceId = scopes.province(actor).id();
        String password = AdminInputs.password(body, "newPassword");
        long version = AdminInputs.version(body);
        locked(id, provinceId, version);
        users.reset(id, passwords.encode(password), version);
        users.audit(actor.id(), "USER_PASSWORD_RESET", id, now());
        return detail(actor, id);
    }

    @Transactional
    public UserView delete(UserAccount actor, long id, Map<String, Object> body) {
        long provinceId = scopes.province(actor).id();
        long version = AdminInputs.version(body);
        guard.lockProvince(provinceId);
        UserRow current = locked(id, provinceId, version);
        List<String> history = users.historyTypes(id);
        if (!history.isEmpty()) {
            throw new AdminConflictException("USER_HAS_HISTORY",
                    "账号有正式报表或审核记录，不能删除；可停用", history);
        }
        UserView before = view(current);
        users.delete(id, version, now());
        guard.ensureOne(provinceId);
        users.audit(actor.id(), "USER_DELETE", id, now());
        return new UserView(before.id(),before.username(),before.displayName(),before.org(),
                before.roleCode(),before.functionalRoles(),before.permissions(),
                false,version+1,before.createdAt());
    }

    private UserRow locked(long id, long provinceId, long expectedVersion) {
        UserRow row = users.user(id, provinceId, true).orElseThrow(NotFoundException::new);
        if (row.version() != expectedVersion) {
            throw new AdminConflictException("VERSION_CONFLICT", "版本已变化，请刷新后重试");
        }
        return row;
    }

    private UserView view(UserRow row) {
        UserAccount account = authUsers.findById(row.id()).orElseThrow(NotFoundException::new);
        return new UserView(Long.toString(row.id()),row.username(),row.displayName(),
                orgView(row.org()),row.org().type(),account.functionalRoles(),
                account.permissions(),row.enabled(),row.version(),
                row.createdAt().atOffset(ZoneOffset.UTC).toString());
    }

    private OrgView orgView(OrgRow row) {
        return new OrgView(Long.toString(row.id()),row.name(),row.type(),row.regionCode(),
                row.regionName(),row.parentId()==null?null:Long.toString(row.parentId()),
                row.enabled());
    }

    private void checkLevel(String level) {
        if (!Set.of("PROVINCE","CITY","ENTERPRISE").contains(level)) {
            throw AdminInputs.field("roleCode", "身份必须是省、市或企业");
        }
    }

    private void checkKeyword(String keyword, int max) {
        if (keyword != null && keyword.trim().length() > max) {
            throw AdminInputs.field("keyword", "检索词过长");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public record OrgView(String id, String name, String orgType, String regionCode,
            String regionName, String parentOrgId, boolean enabled) {
    }
    public record OrgPage(List<OrgView> items, int page, int size, long total) {
    }
    public record UserView(String id, String username, String displayName, OrgView org,
            String roleCode, List<UserAccount.FunctionalRole> functionalRoles,
            List<String> permissions, boolean enabled, long version, String createdAt) {
    }
    public record UserPage(List<UserView> items, int page, int size, long total) {
    }
}
