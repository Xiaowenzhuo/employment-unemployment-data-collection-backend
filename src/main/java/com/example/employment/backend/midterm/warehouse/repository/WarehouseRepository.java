package com.example.employment.backend.midterm.warehouse.repository;

import com.example.employment.backend.midterm.warehouse.dao.WarehouseDao;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class WarehouseRepository {
    private final WarehouseDao dao;
    public WarehouseRepository(WarehouseDao dao) { this.dao=dao; }
    public int replaceSnapshot(LocalDateTime now) { return dao.replaceSnapshot(now); }
    public void run(LocalDateTime start,LocalDateTime end,String result,int count,String error) {
        dao.run(start,end,result,count,error);
    }
    public List<Map<String,Object>> rows(long province,String period,List<String> regions) {
        return dao.rows(province,period,regions);
    }
}
