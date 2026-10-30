package com.example.employment.backend.week2.filing.repository;

import com.example.employment.backend.week2.filing.dao.FilingDao;
import com.example.employment.backend.week2.filing.dao.FilingDao.ListRow;
import com.example.employment.backend.week2.filing.dao.FilingDao.OptionRow;
import com.example.employment.backend.week2.filing.model.Filing;
import com.example.employment.backend.week2.filing.model.FilingVersion;
import com.example.employment.backend.week2.filing.model.FilingViews.Period;
import com.example.employment.backend.week2.filing.model.Profile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class FilingRepository {
    private final FilingDao dao;

    public FilingRepository(FilingDao dao) { this.dao = dao; }

    public Optional<Filing> byOrg(long orgId, boolean lock) { return dao.byOrg(orgId, lock); }
    public Optional<Filing> byId(long id, boolean lock) { return dao.byId(id, lock); }
    public long nextId() { return dao.allocateFilingId(); }
    public void insertDraft(long id, long orgId, Profile p, LocalDateTime now) {
        dao.insertDraft(id, orgId, p, now);
    }
    public void updateDraft(long id, Profile p, LocalDateTime now) { dao.updateDraft(id, p, now); }
    public int setPending(long id, String oldStatus, int oldVersion, LocalDateTime now) {
        return dao.setPending(id, oldStatus, oldVersion, now);
    }
    public void insertVersion(long id, int version, Profile p, long actorId, LocalDateTime now) {
        dao.insertVersion(id, version, p, actorId, now);
    }
    public Optional<FilingVersion> version(long id, int version) { return dao.version(id, version); }
    public List<FilingVersion> history(long id) { return dao.history(id); }
    public int setReviewed(long id, int version, String result, long reviewer, String reason,
            LocalDateTime now) {
        return dao.setReviewed(id, version, result, reviewer, reason, now);
    }
    public void syncOrgName(long orgId, String name) { dao.syncOrgName(orgId, name); }
    public boolean optionPair(String category, String parent, String child) {
        return dao.optionPair(category, parent, child);
    }
    public boolean codeExists(String code, long excludingId) {
        return dao.codeExists(code, excludingId);
    }
    public List<OptionRow> options(String category) { return dao.options(category); }
    public Optional<String> optionName(String code) { return dao.optionName(code); }
    public List<Period> periods(long provinceOrgId) { return dao.periods(provinceOrgId); }
    public Optional<Period> period(String code, long provinceOrgId) {
        return dao.period(code, provinceOrgId);
    }
    public List<ListRow> list(long provinceId, List<String> regions, String status,
            LocalDateTime end, Integer limit, Integer offset) {
        return dao.list(provinceId, regions, status, end, limit, offset);
    }
    public long count(long provinceId, List<String> regions, String status, LocalDateTime end) {
        return dao.count(provinceId, regions, status, end);
    }
    public void audit(long actorId, long orgId, String action, String detail, LocalDateTime now) {
        dao.audit(actorId, orgId, action, detail, now);
    }
}
