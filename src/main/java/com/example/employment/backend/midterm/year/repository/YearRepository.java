package com.example.employment.backend.midterm.year.repository;

import com.example.employment.backend.midterm.year.dao.YearDao;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class YearRepository {
    private final YearDao dao;
    public YearRepository(YearDao dao) { this.dao=dao; }
    public void lockProvince(long id) { dao.lockProvince(id); }
    public List<SurveyPeriod> provincePeriods(long id) { return dao.provincePeriods(id); }
    public Optional<SurveyPeriod> byCode(String code) { return dao.byCode(code); }
    public void insert(SurveyPeriod period) { dao.insert(period); }
}
