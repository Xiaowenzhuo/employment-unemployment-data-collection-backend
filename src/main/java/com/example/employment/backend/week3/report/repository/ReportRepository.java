package com.example.employment.backend.week3.report.repository;

import com.example.employment.backend.week3.report.dao.ReportDao;
import com.example.employment.backend.week3.report.dao.ReportDao.OptionRow;
import com.example.employment.backend.week3.report.model.Report;
import com.example.employment.backend.week3.report.model.ReportForm;
import com.example.employment.backend.week3.report.model.ReportViews.ListItem;
import com.example.employment.backend.week3.report.model.ReportViews.Snapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {
    private final ReportDao dao;

    public ReportRepository(ReportDao dao) { this.dao = dao; }

    public Optional<Report> byOrgPeriod(long orgId, String code, boolean lock) {
        return dao.byOrgPeriod(orgId, code, lock);
    }
    public Optional<Report> byId(long id, boolean lock) { return dao.byId(id, lock); }
    public long nextId() { return dao.nextId(); }
    public void lockOrg(long orgId) { dao.lockOrg(orgId); }
    public void insert(long id, long orgId, String code, ReportForm form, LocalDateTime now) {
        dao.insert(id, orgId, code, form, now);
    }
    public int updateDraft(long id, int version, ReportForm form, LocalDateTime now) {
        return dao.updateDraft(id, version, form, now);
    }
    public int submit(long id, int version, LocalDateTime now) { return dao.submit(id, version, now); }
    public Optional<Integer> baseline(long orgId) { return dao.baseline(orgId); }
    public void insertBaseline(long orgId, int count, long reportId) {
        dao.insertBaseline(orgId, count, reportId);
    }
    public void snapshot(Report report, int version, long actorId, LocalDateTime now,
            long filingId, int filingVersion) {
        dao.snapshot(report, version, actorId, now, filingId, filingVersion);
    }
    public List<Snapshot> snapshots(long reportId) { return dao.snapshots(reportId); }
    public List<ListItem> list(long orgId, String code, String status, int size, int offset) {
        return dao.list(orgId, code, status, size, offset);
    }
    public long count(long orgId, String code, String status) { return dao.count(orgId, code, status); }
    public List<OptionRow> options() { return dao.options(); }
}
