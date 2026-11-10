package com.example.employment.backend.midterm.warehouse.service;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.midterm.warehouse.repository.WarehouseRepository;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.week5.data.service.ProvinceDataService.DataSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WarehouseDataService {
    private final WarehouseRepository warehouse;
    private final ScopeService scopes;
    private final RegionScopeRepository regions;
    private final PeriodRepository periods;
    private final WarehouseSyncService sync;
    public WarehouseDataService(WarehouseRepository warehouse,ScopeService scopes,
            RegionScopeRepository regions,PeriodRepository periods,WarehouseSyncService sync) {
        this.warehouse=warehouse;this.scopes=scopes;this.regions=regions;this.periods=periods;
        this.sync=sync;
    }
    public DataSet load(UserAccount actor,String periodCode,String regionCode) {
        var province=scopes.province(actor);
        if (periodCode==null || periodCode.isBlank()) throw field("periodCode","必填");
        var period=periods.byCode(periodCode,false)
                .orElseThrow(()->field("periodCode","调查期不存在"));
        if (period.provinceOrgId()==null || period.provinceOrgId()!=province.id())
            throw field("periodCode","调查期不属于本省");
        List<String> codes=null;
        if (regionCode!=null && !regionCode.isBlank()) {
            try {
                scopes.pathWithin(regionCode,province.regionCode());
                codes=regions.descendants(regionCode);
            } catch (RuntimeException ex) { throw field("regionCode","地区不属于本省"); }
        }
        sync.sync();
        return new DataSet(period,regionCode,warehouse.rows(province.id(),periodCode,codes));
    }
    private static FieldErrorException field(String key,String value) {
        return new FieldErrorException(Map.of(key,value));
    }
}
