package com.example.employment.backend.national.repository;

import com.example.employment.backend.national.dao.NationalDao;
import com.example.employment.backend.national.dao.NationalDao.AttemptRow;
import com.example.employment.backend.national.dao.NationalDao.BatchRow;
import com.example.employment.backend.national.dao.NationalDao.SourceRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class NationalRepository {
    private final NationalDao dao;
    public NationalRepository(NationalDao dao) { this.dao = dao; }
    public void lockReports(String period) { dao.lockReports(period); }
    public boolean containsReport(long id) { return dao.containsReport(id); }
    public List<SourceRow> source(long province, String period) { return dao.source(province, period); }
    public Optional<BatchRow> byPeriod(long province, String period) {
        return dao.byPeriod(province, period);
    }
    public Optional<BatchRow> byId(long province, String id) { return dao.byId(province, id); }
    public Optional<BatchRow> byId(String id) { return dao.byId(id); }
    public List<BatchRow> list(long province, String period, String status, int page, int size) {
        return dao.list(province, period, status, page, size);
    }
    public long count(long province, String period, String status) {
        return dao.count(province, period, status);
    }
    public void insert(BatchRow b) { dao.insert(b); }
    public void insertItem(String batch, long report, int submission, int version,
            int baseline, int current) {
        dao.insertItem(batch, report, submission, version, baseline, current);
    }
    public int queueRetry(String batch) { return dao.queueRetry(batch); }
    public boolean start(String batch, int number, Instant now) { return dao.start(batch, number, now); }
    public int attemptCount(String batch) { return dao.attemptCount(batch); }
    public void accepted(String batch, int number, String receipt, Instant received, Instant now) {
        dao.accepted(batch, number, receipt, received, now);
    }
    public void failed(String batch, int number, String error, Instant now) {
        dao.failed(batch, number, error, now);
    }
    public void recover(Instant now) { dao.recover(now); }
    public List<String> queued() { return dao.queued(); }
    public List<AttemptRow> attempts(String batch) { return dao.attempts(batch); }
}
