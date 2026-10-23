package com.example.employment.backend.week1.region.repository;

import com.example.employment.backend.week1.region.dao.RegionDao;
import com.example.employment.backend.week1.region.model.Region;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RegionRepository {
    private final RegionDao dao;

    public RegionRepository(RegionDao dao) {
        this.dao = dao;
    }

    public List<Region> findChildren(String parentCode) {
        return dao.findChildren(parentCode).stream()
                .map(row -> new Region(row.code(), row.name(), row.level())).toList();
    }
}
