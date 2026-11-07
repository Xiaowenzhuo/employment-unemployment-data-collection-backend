package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Backfill only the current effective report; historical submission snapshots stay intact. */
public class V10__backfill_rolling_baseline extends BaseJavaMigration {
    private record Source(long id, String periodCode, Integer employment,
            LocalDateTime firstSubmittedAt, LocalDateTime voidedAt) { }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Map<Long, List<Source>> previous = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT r.id, r.org_id, r.period_code, r.submission_version,
                       r.period_employment_count, r.voided_at,
                       COALESCE((SELECT MIN(s.submitted_at) FROM report_snapshot s
                                 WHERE s.report_id=r.id), r.submitted_at) AS first_submitted_at
                FROM enterprise_report r JOIN survey_period p ON p.code=r.period_code
                ORDER BY r.org_id, p.start_at, r.period_code
                """);
             PreparedStatement update = connection.prepareStatement("""
                UPDATE enterprise_report SET baseline_employment_count=?,
                    baseline_source_period_code=?, baseline_source_report_id=?,
                    baseline_source_kind='LEGACY_MIGRATED' WHERE id=?
                """);
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                long id = rows.getLong("id");
                long orgId = rows.getLong("org_id");
                String periodCode = rows.getString("period_code");
                Integer current = (Integer) rows.getObject("period_employment_count");
                LocalDateTime firstSubmitted=rows.getObject("first_submitted_at",LocalDateTime.class);
                LocalDateTime referenceTime=firstSubmitted==null
                        ? LocalDateTime.now(ZoneOffset.UTC):firstSubmitted;
                List<Source> candidates=previous.computeIfAbsent(orgId,key->new ArrayList<>());
                Source source=null;
                for (int i=candidates.size()-1;i>=0;i--) {
                    Source candidate=candidates.get(i);
                    if (!candidate.firstSubmittedAt().isAfter(referenceTime)
                            && (candidate.voidedAt()==null
                                || candidate.voidedAt().isAfter(referenceTime))) {
                        source=candidate;
                        break;
                    }
                }
                update.setObject(1, source == null ? current : source.employment());
                update.setString(2, source == null ? periodCode : source.periodCode());
                update.setLong(3, source == null ? id : source.id());
                update.setLong(4, id);
                update.executeUpdate();
                if (rows.getInt("submission_version") > 0 && firstSubmitted!=null) {
                    candidates.add(new Source(id, periodCode, current,firstSubmitted,
                            rows.getObject("voided_at",LocalDateTime.class)));
                }
            }
        }
    }
}
