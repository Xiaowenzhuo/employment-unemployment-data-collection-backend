package com.example.employment.backend.week78.role.service;

import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week78.role.dao.RoleDao.PermissionRow;
import com.example.employment.backend.week78.role.dao.RoleDao.RoleRow;
import com.example.employment.backend.week78.role.repository.RoleRepository;
import com.example.employment.backend.week78.shared.AdminConflictException;
import com.example.employment.backend.week78.shared.AdminGuard;
import com.example.employment.backend.week78.shared.AdminInputs;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    private final RoleRepository roles;
    private final ScopeService scopes;
    private final AdminGuard guard;

    public RoleService(RoleRepository roles, ScopeService scopes, AdminGuard guard) {
        this.roles = roles;
        this.scopes = scopes;
        this.guard = guard;
    }

    public Map<String, List<PermissionRow>> permissions(UserAccount actor) {
        scopes.province(actor);
        return Map.of("items", roles.permissions());
    }

    public Page list(UserAccount actor, String level, String keyword, Integer page, Integer size) {
        long provinceId = scopes.province(actor).id();
        if (level != null) checkLevel(level);
        int p = AdminInputs.page(page), s = AdminInputs.size(size);
        return new Page(roles.list(provinceId, level, keyword, p, s).stream()
                .map(this::view).toList(), p, s, roles.count(provinceId, level, keyword));
    }

    public RoleView detail(UserAccount actor, long id) {
        long provinceId = scopes.province(actor).id();
        return view(roles.find(id, provinceId, false).orElseThrow(NotFoundException::new));
    }

    @Transactional
    public RoleView create(UserAccount actor, Map<String, Object> body) {
        long provinceId = scopes.province(actor).id();
        String name = AdminInputs.text(body, "name", 50);
        String level = AdminInputs.text(body, "level", 20);
        checkLevel(level);
        List<String> codes = AdminInputs.strings(body, "permissionCodes");
        validatePermissions(level, codes);
        guard.lockProvince(provinceId);
        if (roles.nameExists(provinceId, level, name, -1)) {
            throw new AdminConflictException("ROLE_NAME_EXISTS", "同级角色名称已存在");
        }
        long id = roles.nextId();
        roles.create(id, name, level, provinceId);
        roles.replacePermissions(id, codes);
        roles.audit(actor.id(), "ROLE_CREATE", id, now());
        return detail(actor, id);
    }

    @Transactional
    public RoleView update(UserAccount actor, long id, Map<String, Object> body) {
        long provinceId = scopes.province(actor).id();
        String name = AdminInputs.text(body, "name", 50);
        List<String> codes = AdminInputs.strings(body, "permissionCodes");
        long version = AdminInputs.version(body);
        guard.lockProvince(provinceId);
        RoleRow role = roles.find(id, provinceId, true).orElseThrow(NotFoundException::new);
        editable(role);
        if (role.version() != version) conflictVersion();
        validatePermissions(role.level(), codes);
        if (roles.nameExists(provinceId, role.level(), name, id)) {
            throw new AdminConflictException("ROLE_NAME_EXISTS", "同级角色名称已存在");
        }
        roles.update(id, name, version);
        roles.replacePermissions(id, codes);
        if ("PROVINCE".equals(role.level())) guard.ensureOne(provinceId);
        roles.audit(actor.id(), "ROLE_UPDATE", id, now());
        return detail(actor, id);
    }

    @Transactional
    public DeleteResult delete(UserAccount actor, long id, Map<String, Object> body) {
        long provinceId = scopes.province(actor).id();
        long version = AdminInputs.version(body);
        String rawReplacement = com.example.employment.backend.week2.shared.web.StrictBody
                .string(body, "replacementRoleId");
        guard.lockProvince(provinceId);
        RoleRow role = roles.find(id, provinceId, true).orElseThrow(NotFoundException::new);
        editable(role);
        if (role.version() != version) conflictVersion();
        List<Long> users = roles.assignedUserIds(id);
        Long replacementId = null;
        if (!users.isEmpty()) {
            if (rawReplacement == null) {
                throw new AdminConflictException("ROLE_REPLACEMENT_REQUIRED", "请先选择同级替代角色");
            }
            replacementId = AdminInputs.id(rawReplacement, "replacementRoleId");
            if (replacementId == id) {
                throw new AdminConflictException("ROLE_REPLACEMENT_REQUIRED", "替代角色不能是原角色");
            }
            RoleRow replacement = roles.find(replacementId, provinceId, true)
                    .orElseThrow(NotFoundException::new);
            if (!replacement.level().equals(role.level())) {
                throw new AdminConflictException("ROLE_LEVEL_MISMATCH", "替代角色层级不一致");
            }
        } else if (rawReplacement != null) {
            throw AdminInputs.field("replacementRoleId", "未分配角色时不传替代角色");
        }
        roles.replaceAndDelete(id, replacementId, users, now(), version);
        if ("PROVINCE".equals(role.level())) guard.ensureOne(provinceId);
        roles.audit(actor.id(), "ROLE_DELETE", id, now());
        return new DeleteResult(Long.toString(id), users.size(),
                replacementId == null ? null : Long.toString(replacementId));
    }

    public void validateAssignment(long provinceId, String level, List<String> ids) {
        for (String value : ids) {
            long id = AdminInputs.id(value, "functionalRoleIds");
            RoleRow role = roles.find(id, provinceId, false).orElseThrow(NotFoundException::new);
            if (!role.level().equals(level)) {
                throw new AdminConflictException("ROLE_LEVEL_MISMATCH", "功能角色层级与账号不一致");
            }
        }
    }

    private void validatePermissions(String level, List<String> codes) {
        Map<String, String> available = roles.permissions().stream()
                .collect(Collectors.toMap(PermissionRow::code, PermissionRow::level));
        for (String code : codes) {
            String owner = available.get(code);
            if (owner == null) throw AdminInputs.field("permissionCodes", "权限编码不存在");
            if (!owner.equals(level)) {
                throw new AdminConflictException("ROLE_LEVEL_MISMATCH", "权限编码不属于所选层级");
            }
        }
    }

    private void checkLevel(String level) {
        if (!Set.of("PROVINCE", "CITY", "ENTERPRISE").contains(level)) {
            throw AdminInputs.field("level", "层级必须是省、市或企业");
        }
    }

    private void editable(RoleRow role) {
        if (role.protectedRole()) {
            throw new AdminConflictException("PROTECTED_ROLE", "系统默认角色不能修改或删除");
        }
    }

    private void conflictVersion() {
        throw new AdminConflictException("VERSION_CONFLICT", "版本已变化，请刷新后重试");
    }

    private RoleView view(RoleRow role) {
        return new RoleView(Long.toString(role.id()), role.code(), role.name(), role.level(),
                roles.permissionCodes(role.id()), roles.assignedCount(role.id()),
                role.protectedRole(), role.version());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public record RoleView(String id, String code, String name, String level,
            List<String> permissionCodes, long assignedUserCount,
            boolean systemProtected, long version) {
    }
    public record Page(List<RoleView> items, int page, int size, long total) {
    }
    public record DeleteResult(String deletedRoleId, int affectedUserCount,
            String replacementRoleId) {
    }
}
