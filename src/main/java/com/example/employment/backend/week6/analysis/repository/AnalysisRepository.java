package com.example.employment.backend.week6.analysis.repository;

import com.example.employment.backend.week6.analysis.dao.AnalysisDao;
import com.example.employment.backend.week6.analysis.dao.AnalysisDao.Option;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisRepository {
    private final AnalysisDao dao;
    public AnalysisRepository(AnalysisDao dao) { this.dao = dao; }
    public Optional<Option> option(String code) { return dao.option(code); }
}
