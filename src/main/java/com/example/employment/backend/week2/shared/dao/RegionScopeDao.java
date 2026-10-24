package com.example.employment.backend.week2.shared.dao;

import com.example.employment.backend.week2.shared.model.RegionInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RegionScopeDao {
    private final JdbcTemplate jdbc;

    public RegionScopeDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Org> org(long id) {
        return jdbc.query("SELECT id, parent_id, org_type, region_code, enabled FROM org_unit WHERE id = ?",
                (rs, row) -> new Org(rs.getLong("id"), (Long) rs.getObject("parent_id"),
                        rs.getString("org_type"), rs.getString("region_code"),
                        rs.getBoolean("enabled")), id).stream().findFirst();
    }

    public Optional<Region> region(String code) {
        return jdbc.query("SELECT code, parent_code, name, level FROM region WHERE code = ?",
                (rs, row) -> new Region(rs.getString("code"), rs.getString("parent_code"),
                        rs.getString("name"), rs.getInt("level")), code).stream().findFirst();
    }

    public List<Region> children(String code) {
        return jdbc.query("SELECT code, parent_code, name, level FROM region WHERE parent_code = ?",
                (rs, row) -> new Region(rs.getString("code"), rs.getString("parent_code"),
                        rs.getString("name"), rs.getInt("level")), code);
    }

    public Optional<Org> cityOrg(String code, long provinceOrgId) {
        return jdbc.query("""
                SELECT id, parent_id, org_type, region_code, enabled FROM org_unit
                WHERE region_code = ? AND parent_id = ? AND org_type = 'CITY' AND enabled = true
                ORDER BY id LIMIT 1
                """, (rs, row) -> new Org(rs.getLong("id"), (Long) rs.getObject("parent_id"),
                rs.getString("org_type"), rs.getString("region_code"),
                rs.getBoolean("enabled")), code, provinceOrgId).stream().findFirst();
    }

    public List<Region> path(String code) {
        List<Region> reversed = new ArrayList<>();
        var seen = new HashSet<String>();
        String current = code;
        while (current != null) {
            if (!seen.add(current)) {
                throw new IllegalStateException("Region cycle");
            }
            Region node = region(current).orElseThrow();
            reversed.add(node);
            current = node.parentCode();
        }
        return reversed.reversed();
    }

    public RegionInfo info(String code) {
        List<Region> path = path(code);
        Region last = path.getLast();
        return new RegionInfo(last.code(), last.name(), path.stream()
                .map(r -> new RegionInfo.Node(r.code(), r.name(), r.level())).toList());
    }

    public List<String> descendants(String code) {
        var result = new ArrayList<String>();
        var seen = new HashSet<String>();
        var queue = new ArrayList<String>();
        queue.add(code);
        for (int i = 0; i < queue.size(); i++) {
            String current = queue.get(i);
            if (!seen.add(current)) {
                continue;
            }
            result.add(current);
            for (Region child : children(current)) {
                queue.add(child.code());
            }
        }
        return result;
    }

    public record Region(String code, String parentCode, String name, int level) {
    }

    public record Org(long id, Long parentId, String type, String regionCode, boolean enabled) {
    }
}
