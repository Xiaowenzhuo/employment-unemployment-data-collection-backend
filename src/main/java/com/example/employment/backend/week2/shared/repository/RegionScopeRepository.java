package com.example.employment.backend.week2.shared.repository;

import com.example.employment.backend.week2.shared.dao.RegionScopeDao;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Region;
import com.example.employment.backend.week2.shared.model.RegionInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RegionScopeRepository {
    private final RegionScopeDao dao;

    public RegionScopeRepository(RegionScopeDao dao) {
        this.dao = dao;
    }

    public Optional<Org> org(long id) { return dao.org(id); }
    public Optional<Region> region(String code) { return dao.region(code); }
    public Optional<Org> cityOrg(String code, long provinceOrgId) { return dao.cityOrg(code, provinceOrgId); }
    public List<Region> path(String code) { return dao.path(code); }
    public List<String> descendants(String code) { return dao.descendants(code); }
    public RegionInfo info(String code) { return dao.info(code); }
}
