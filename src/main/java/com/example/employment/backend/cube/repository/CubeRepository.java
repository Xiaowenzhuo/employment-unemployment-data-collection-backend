package com.example.employment.backend.cube.repository;

import com.example.employment.backend.cube.dao.CubeDao;
import com.example.employment.backend.cube.dao.CubeDao.Fact;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class CubeRepository {
    private final CubeDao dao;
    public CubeRepository(CubeDao dao) { this.dao=dao; }
    public List<Map<String,Object>> regions(String province) { return dao.regions(province); }
    public List<Map<String,Object>> options(String category) { return dao.options(category); }
    public List<LocalDateTime> periods(long province) { return dao.periods(province); }
    public List<Fact> facts(long province) { return dao.facts(province); }
}
