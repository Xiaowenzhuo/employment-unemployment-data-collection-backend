package com.example.employment.backend.week78.monitor.repository;

import com.example.employment.backend.week78.monitor.dao.MonitorDao;
import org.springframework.stereotype.Repository;

@Repository
public class MonitorRepository {
    private final MonitorDao dao;

    public MonitorRepository(MonitorDao dao) {
        this.dao = dao;
    }

    public boolean mysql() throws Exception { return dao.mysql(); }
    public boolean rabbitmq() throws Exception { return dao.rabbitmq(); }
    public boolean receiver() throws Exception { return dao.receiver(); }
}
