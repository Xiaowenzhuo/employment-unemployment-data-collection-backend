package com.example.employment.backend.week2.account.repository;

import com.example.employment.backend.week2.account.dao.AccountDao;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private final AccountDao dao;

    public AccountRepository(AccountDao dao) {
        this.dao = dao;
    }

    public boolean usernameExists(String username) { return dao.usernameExists(username); }
    public long nextOrgId() { return dao.allocate("org_unit"); }
    public long nextUserId() { return dao.allocate("user_account"); }
    public void create(long orgId, long cityId, String regionCode, String orgName,
            long userId, String username, String hash, LocalDateTime now) {
        dao.create(orgId, cityId, regionCode, orgName, userId, username, hash, now);
    }
    public void audit(long actorId, long orgId, LocalDateTime now) {
        dao.audit(actorId, orgId, now);
    }
}
