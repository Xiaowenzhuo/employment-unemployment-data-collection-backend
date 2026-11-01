package com.example.employment.backend.week5.correction.repository;

import com.example.employment.backend.week3.report.model.ReportForm;
import com.example.employment.backend.week5.correction.dao.CorrectionDao;
import com.example.employment.backend.week5.correction.dao.CorrectionDao.CorrectionEntry;
import com.example.employment.backend.week5.correction.dao.CorrectionDao.VoidEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CorrectionRepository {
    private final CorrectionDao dao;
    public CorrectionRepository(CorrectionDao dao) { this.dao = dao; }
    public boolean voided(long id) { return dao.voided(id); }
    public Instant voidedAt(long id) { return dao.voidedAt(id); }
    public long nextCorrectionId() { return dao.nextId("report_correction_event"); }
    public long nextVoidId() { return dao.nextId("report_void_event"); }
    public int update(long id, int version, int submission, ReportForm form, LocalDateTime now) {
        return dao.update(id, version, submission, form, now);
    }
    public int voidReport(long id, int version, int submission, LocalDateTime now) {
        return dao.voidReport(id, version, submission, now);
    }
    public void insertCorrection(long id, long operation, long report, int submission,
            String field, Object oldValue, Object newValue, String reason, long actor,
            String actorName, LocalDateTime now) {
        dao.insertCorrection(id, operation, report, submission, field, oldValue, newValue,
                reason, actor, actorName, now);
    }
    public void insertVoid(long id, long report, int submission, String reason,
            long actor, String actorName, LocalDateTime now) {
        dao.insertVoid(id, report, submission, reason, actor, actorName, now);
    }
    public List<CorrectionEntry> corrections(long id) { return dao.corrections(id); }
    public Optional<VoidEvent> voidEvent(long id) { return dao.voidEvent(id); }
}
