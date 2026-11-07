package com.example.employment.backend.revision.repository;

import com.example.employment.backend.revision.dao.ProgressDao;
import com.example.employment.backend.revision.dao.ProgressDao.Row;
import com.example.employment.backend.revision.dao.ProgressDao.City;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ProgressRepository {
    private final ProgressDao dao;
    public ProgressRepository(ProgressDao dao) { this.dao=dao; }
    public List<Row> rows(long provinceId,String periodCode) {
        return dao.rows(provinceId,periodCode);
    }
    public List<City> cities(long provinceId) { return dao.cities(provinceId); }
}
