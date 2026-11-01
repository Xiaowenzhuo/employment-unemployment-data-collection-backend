package com.example.employment.backend.week5.accounts.repository;

import com.example.employment.backend.week5.accounts.dao.ProvinceAccountDao;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class ProvinceAccountRepository {
    private final ProvinceAccountDao dao;
    public ProvinceAccountRepository(ProvinceAccountDao dao) { this.dao = dao; }
    public List<Map<String, Object>> rows(long province, Map<String, String> filters,
            Map<String, List<String>> regions, List<String> periods) {
        return dao.rows(province, filters, regions, periods);
    }
}
