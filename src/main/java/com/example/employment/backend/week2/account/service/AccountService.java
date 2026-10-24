package com.example.employment.backend.week2.account.service;

import com.example.employment.backend.common.ConflictException;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.account.repository.AccountRepository;
import com.example.employment.backend.week2.account.model.CreateAccountRequest;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Region;
import com.example.employment.backend.week2.shared.model.RegionInfo;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final RegionScopeRepository regions;
    private final ScopeService scopes;
    private final PasswordEncoder passwords;

    public AccountService(AccountRepository accounts, RegionScopeRepository regions,
            ScopeService scopes, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.regions = regions;
        this.scopes = scopes;
        this.passwords = passwords;
    }

    @Transactional
    public Created create(UserAccount actor, CreateAccountRequest request) {
        Org province = scopes.province(actor);
        String username = trim(request.username());
        String orgName = trim(request.orgName());
        String regionCode = trim(request.regionCode());
        String password = request.initialPassword();
        if (username == null || username.length() > 64) {
            throw field("username", "登录名不能为空且最多64字");
        }
        if (orgName == null || orgName.length() > 120) {
            throw field("orgName", "组织名称不能为空且最多120字");
        }
        if (password == null || !password.matches("^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{8,64}$")) {
            throw field("initialPassword", "密码需为8至64位可见ASCII字符，且包含字母和数字");
        }
        if (regionCode == null || regionCode.length() > 12) {
            throw field("regionCode", "地区代码不能为空且最多12字");
        }
        Region selected = regions.region(regionCode)
                .orElseThrow(() -> field("regionCode", "地区不存在"));
        List<Region> path = scopes.pathWithin(regionCode, province.regionCode());
        if (selected.level() < 2) {
            throw field("regionCode", "请选择市级或下级地区");
        }
        Region city = path.stream().filter(r -> r.level() == 2).reduce((first, last) -> last)
                .orElseThrow(() -> field("regionCode", "找不到所属市级地区"));
        Org cityOrg = regions.cityOrg(city.code(), province.id())
                .orElseThrow(() -> field("regionCode", "找不到对应的市级组织"));
        if (accounts.usernameExists(username)) {
            throw new ConflictException("登录名已存在");
        }
        long orgId = accounts.nextOrgId();
        long userId = accounts.nextUserId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        try {
            accounts.create(orgId, cityOrg.id(), regionCode, orgName, userId,
                    username, passwords.encode(password), now);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("登录名已存在");
        }
        accounts.audit(actor.id(), orgId, now);
        return new Created(Long.toString(orgId), Long.toString(userId), username, orgName,
                regions.info(regionCode), true);
    }

    private static String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static FieldErrorException field(String field, String message) {
        return new FieldErrorException(Map.of(field, message));
    }

    public record Created(String orgId, String userId, String username, String orgName,
            RegionInfo region, boolean enabled) {
    }
}
