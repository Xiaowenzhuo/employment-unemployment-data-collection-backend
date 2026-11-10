package com.example.employment.backend.midterm.progress.service;

import com.example.employment.backend.revision.dao.ProgressDao.Row;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CoverageService {
    public Coverage count(List<Row> rows) {
        long eligible=0,real=0,proxy=0,pending=0,returned=0;
        for (Row row:rows) {
            if (!row.eligible()) continue;
            eligible++;
            if (row.realSubmitted()) real++;
            else if (row.proxySourceReportId()!=null) proxy++;
            if ("SUBMITTED_CITY".equals(row.reportStatus()) ||
                    "SUBMITTED_PROVINCE".equals(row.reportStatus())) pending++;
            if ("RETURNED_CITY".equals(row.reportStatus()) ||
                    "RETURNED_PROVINCE".equals(row.reportStatus())) returned++;
        }
        return new Coverage(eligible,real,proxy,eligible-real-proxy,pending,returned);
    }
    public record Coverage(long eligibleEnterpriseCount,long realSubmittedCount,
            long proxyCount,long notCoveredCount,long pendingCount,long returnedCount) { }
}
