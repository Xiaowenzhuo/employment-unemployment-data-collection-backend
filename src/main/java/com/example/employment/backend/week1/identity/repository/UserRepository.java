package com.example.employment.backend.week1.identity.repository;

import com.example.employment.backend.week1.identity.dao.UserDao;
import com.example.employment.backend.week1.identity.dao.UserDao.UserRow;
import com.example.employment.backend.week1.identity.model.UserAccount;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final UserDao dao;

    public UserRepository(UserDao dao) {
        this.dao = dao;
    }

    public Optional<UserAccount> findByUsername(String username) {
        return dao.findByUsername(username).map(this::toAccount);
    }

    public Optional<UserAccount> findById(long id) {
        return dao.findById(id).map(this::toAccount);
    }

    private UserAccount toAccount(UserRow row) {
        return new UserAccount(row.id(), row.orgId(), row.username(), row.passwordHash(),
                row.displayName(), row.enabled(), row.orgName(), row.orgEnabled(),
                dao.roleCodes(row.id()));
    }
}
