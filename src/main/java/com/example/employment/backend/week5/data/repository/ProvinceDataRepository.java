package com.example.employment.backend.week5.data.repository;

import com.example.employment.backend.week5.data.dao.ProvinceDataDao;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class ProvinceDataRepository {
    private final ProvinceDataDao dao;
    public ProvinceDataRepository(ProvinceDataDao dao) { this.dao = dao; }
    public List<Map<String, Object>> rows(long province, String period, List<String> regions) {
        return dao.rows(province, period, regions);
    }
}
