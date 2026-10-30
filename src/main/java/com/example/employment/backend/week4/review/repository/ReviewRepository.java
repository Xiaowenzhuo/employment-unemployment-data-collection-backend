package com.example.employment.backend.week4.review.repository;

import com.example.employment.backend.week4.review.dao.ReviewDao;
import com.example.employment.backend.week4.review.dao.ReviewDao.ReviewRow;
import com.example.employment.backend.week4.review.model.ReviewViews.LatestReturn;
import com.example.employment.backend.week4.review.model.ReviewViews.ReviewEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRepository {
    private final ReviewDao dao;

    public ReviewRepository(ReviewDao dao) { this.dao = dao; }

    public List<ReviewRow> list(String level, long orgId, String periodCode, String status,
            int size, int offset) {
        return dao.list(level, orgId, periodCode, status, size, offset);
    }
    public long count(String level, long orgId, String periodCode, String status) {
        return dao.count(level, orgId, periodCode, status);
    }
    public Optional<ReviewRow> scoped(long reportId, String level, long orgId) {
        return dao.scoped(reportId, level, orgId);
    }
    public Optional<LatestReturn> latestReturn(long reportId) { return dao.latestReturn(reportId); }
    public List<ReviewEvent> events(long reportId) { return dao.events(reportId); }
    public long nextId() { return dao.nextId(); }
    public int changeStatus(long reportId, String fromStatus, String toStatus,
            int expectedVersion, int submissionVersion, LocalDateTime now) {
        return dao.changeStatus(reportId, fromStatus, toStatus, expectedVersion, submissionVersion, now);
    }
    public void insertEvent(long id, long reportId, int submissionVersion, String level,
            String action, String fromStatus, String toStatus, long userId, long orgId,
            String displayName, LocalDateTime now, String comment) {
        dao.insertEvent(id, reportId, submissionVersion, level, action, fromStatus, toStatus,
                userId, orgId, displayName, now, comment);
    }
}
