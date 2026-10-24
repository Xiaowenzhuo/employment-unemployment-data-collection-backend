package com.example.employment.backend.week2.shared.service;

import com.example.employment.backend.common.ForbiddenException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Region;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScopeService {
    private final RegionScopeRepository regions;

    public ScopeService(RegionScopeRepository regions) {
        this.regions = regions;
    }

    public Org province(UserAccount actor) {
        Org org = regions.org(actor.orgId()).orElseThrow(ForbiddenException::new);
        if (!org.enabled() || !"PROVINCE".equals(org.type())) {
            throw new ForbiddenException();
        }
        return org;
    }

    public Org enterprise(UserAccount actor) {
        Org org = regions.org(actor.orgId()).orElseThrow(ForbiddenException::new);
        if (!org.enabled() || !"ENTERPRISE".equals(org.type())) {
            throw new ForbiddenException();
        }
        return org;
    }

    public List<Region> pathWithin(String code, String provinceCode) {
        List<Region> path = regions.path(code);
        if (path.stream().noneMatch(r -> r.code().equals(provinceCode))) {
            throw new ForbiddenException();
        }
        return path;
    }

    public void visibleFiling(long orgId, Org province) {
        Org enterprise = regions.org(orgId).orElseThrow(NotFoundException::new);
        if (!"ENTERPRISE".equals(enterprise.type()) || enterprise.parentId() == null) {
            throw new NotFoundException();
        }
        Org city = regions.org(enterprise.parentId()).orElseThrow(NotFoundException::new);
        if (!"CITY".equals(city.type()) || !Long.valueOf(province.id()).equals(city.parentId())) {
            throw new NotFoundException();
        }
        try {
            pathWithin(enterprise.regionCode(), province.regionCode());
        } catch (ForbiddenException exception) {
            throw new NotFoundException();
        }
    }
}
