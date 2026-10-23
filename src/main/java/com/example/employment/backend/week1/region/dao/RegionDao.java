package com.example.employment.backend.week1.region.dao;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RegionDao {
    private final JdbcTemplate jdbc;

    public RegionDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RegionRow> findChildren(String parentCode) {
        String sql = "SELECT code, name, level FROM region WHERE parent_code "
                + (parentCode == null ? "IS NULL" : "= ?") + " ORDER BY code";
        var mapper = (org.springframework.jdbc.core.RowMapper<RegionRow>) (rs, rowNum) ->
                new RegionRow(rs.getString("code"), rs.getString("name"), rs.getInt("level"));
        return parentCode == null ? jdbc.query(sql, mapper) : jdbc.query(sql, mapper, parentCode);
    }

    public record RegionRow(String code, String name, int level) {
    }
}
