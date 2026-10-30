package com.example.employment.backend.week3.period.repository;

import com.example.employment.backend.week3.period.dao.PeriodDao;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PeriodRepository {
    private final PeriodDao dao;

    public PeriodRepository(PeriodDao dao) { this.dao = dao; }

    public List<SurveyPeriod> visible(long provinceId) { return dao.visible(provinceId); }
    public Optional<SurveyPeriod> byCode(String code, boolean lock) { return dao.byCode(code, lock); }
    public void insert(SurveyPeriod period) { dao.insert(period); }
    public int update(SurveyPeriod period, int expectedVersion) { return dao.update(period, expectedVersion); }
    public boolean hasReports(String code) { return dao.hasReports(code); }
}
