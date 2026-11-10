package com.example.employment.backend.midterm.national.repository;

import com.example.employment.backend.midterm.national.dao.QueueDao;
import org.springframework.stereotype.Repository;

@Repository
public class QueueRepository extends QueueDao {
    public QueueRepository(org.springframework.jdbc.core.JdbcTemplate jdbc) { super(jdbc); }
}
