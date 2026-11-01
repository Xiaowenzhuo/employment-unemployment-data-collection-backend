package com.example.employment.backend.week6.analysis.dao;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnalysisDao {
    private final JdbcTemplate jdbc;
    public AnalysisDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Option> option(String code) {
        return jdbc.query("SELECT code,category,parent_code FROM filing_option WHERE code=?",
                (rs, i) -> new Option(rs.getString("code"), rs.getString("category"),
                        rs.getString("parent_code")), code).stream().findFirst();
    }
    public record Option(String code, String category, String parentCode) { }
}
