package com.example.employment.backend.midterm.national.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class QueueDao {
    private final JdbcTemplate jdbc;
    public QueueDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void lockProvince(long province) {
        jdbc.query("SELECT id FROM org_unit WHERE id=? FOR UPDATE",(rs,i)->rs.getLong(1),province);
    }
    public int version(long province,String period) {
        return jdbc.query("SELECT current_version FROM national_proxy_set WHERE province_org_id=? AND period_code=?",
                (rs,i)->rs.getInt(1),province,period).stream().findFirst().orElse(0);
    }
    public int nextVersion(long province,String period) {
        int updated=jdbc.update("UPDATE national_proxy_set SET current_version=current_version+1,updated_at=? WHERE province_org_id=? AND period_code=?",
                utc(Instant.now()),province,period);
        if(updated==0) {
            jdbc.update("INSERT INTO national_proxy_set(province_org_id,period_code,current_version,updated_at) VALUES(?,?,1,?)",
                    province,period,utc(Instant.now()));
            return 1;
        }
        return version(province,period);
    }
    public List<Source> previous(long org,Instant targetStart) {
        return jdbc.query("""
                SELECT r.id,r.period_code,r.submission_version,r.current_version,
                    r.period_employment_count
                FROM enterprise_report r JOIN survey_period p ON p.code=r.period_code
                WHERE r.org_id=? AND p.start_at<? AND p.province_org_id IS NOT NULL
                  AND r.status='APPROVED_PROVINCE' AND r.voided=false
                  AND r.submission_version>0 AND r.period_employment_count IS NOT NULL
                ORDER BY p.start_at DESC,r.id DESC LIMIT 1
                """,(rs,i)->new Source(rs.getLong(1),rs.getString(2),rs.getInt(3),
                rs.getInt(4),rs.getInt(5)),org,utc(targetStart));
    }
    public List<Proxy> proxies(long province,String period) {
        return jdbc.query("SELECT * FROM national_proxy_item WHERE province_org_id=? AND period_code=? ORDER BY region_code,organization_code",
                this::proxy,province,period);
    }
    public int clear(long province,String period) {
        return jdbc.update("DELETE FROM national_proxy_item WHERE province_org_id=? AND period_code=? AND batch_id IS NULL",province,period);
    }
    public void insertProxy(Proxy p) {
        jdbc.update("""
                INSERT INTO national_proxy_item(enterprise_org_id,period_code,province_org_id,
                    organization_code,enterprise_name,region_code,nature_code,industry_code,
                    source_period_code,source_report_id,source_submission_version,
                    source_current_version,employment_count,generated_by,generated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,p.orgId(),p.period(),p.province(),p.orgCode(),p.name(),p.region(),
                p.nature(),p.industry(),p.sourcePeriod(),p.sourceReport(),p.sourceSubmission(),
                p.sourceVersion(),p.employment(),p.generatedBy(),utc(p.generatedAt()));
    }
    public void freezeProxies(long province,String period,String batch) {
        jdbc.update("UPDATE national_proxy_item SET batch_id=? WHERE province_org_id=? AND period_code=? AND batch_id IS NULL",
                batch,province,period);
    }
    public void insertBatch(String id,long province,String period,String xml,String hash,int count,
            int real,int proxy,long baseline,long current,int chunks,Instant now) {
        jdbc.update("""
                INSERT INTO national_batch(batch_id,province_org_id,period_code,mode,status,
                    payload_xml,payload_format,schema_version,payload_sha256,enterprise_count,
                    real_count,proxy_count,baseline_total,period_total,net_change,chunk_count,created_at)
                VALUES(?,?,?,'MOCK_QUEUE','QUEUED',?,'XML','demo-2',?,?,?,?,?,?,?,?,?)
                """,id,province,period,xml,hash,count,real,proxy,baseline,current,current-baseline,chunks,utc(now));
    }
    public void insertChunk(String batch,int index,String hash,int size) {
        jdbc.update("INSERT INTO national_chunk_out(batch_id,chunk_index,chunk_sha256,byte_count) VALUES(?,?,?,?)",
                batch,index,hash,size);
    }
    public List<Batch> queued() {
        return jdbc.query("SELECT * FROM national_batch WHERE schema_version='demo-2' AND status='QUEUED'",
                this::mapBatch);
    }
    public Batch batch(String id) {
        return jdbc.query("SELECT * FROM national_batch WHERE batch_id=?",this::mapBatch,id)
                .stream().findFirst().orElse(null);
    }
    public boolean start(String id,int attempt,Instant now) {
        if(jdbc.update("UPDATE national_batch SET status='SENDING',last_sent_at=?,last_error=NULL WHERE batch_id=? AND status='QUEUED'",
                utc(now),id)!=1) return false;
        jdbc.update("INSERT INTO national_send_attempt(batch_id,attempt_no,started_at,result) VALUES(?,?,?,'SENDING')",
                id,attempt,utc(now));
        return true;
    }
    public void confirmed(String id,int index,Instant now) {
        jdbc.update("UPDATE national_chunk_out SET broker_confirmed=true,sent_at=? WHERE batch_id=? AND chunk_index=?",
                utc(now),id,index);
    }
    public void assembling(String id) {
        jdbc.update("UPDATE national_batch SET status='ASSEMBLING' WHERE batch_id=? AND status='SENDING'",id);
    }
    public void fail(String id,String error,Instant now) {
        jdbc.update("UPDATE national_batch SET status='SEND_FAILED',last_error=? WHERE batch_id=? AND status IN ('SENDING','ASSEMBLING')",
                error,id);
        jdbc.update("UPDATE national_send_attempt SET result='FAILED',finished_at=?,error_summary=? WHERE batch_id=? AND result='SENDING'",
                utc(now),error,id);
    }
    public void recover(Instant now) {
        jdbc.update("UPDATE national_batch SET status='SEND_FAILED',last_error='服务重启，队列发送未完成' WHERE schema_version='demo-2' AND status IN ('SENDING','ASSEMBLING')");
        jdbc.update("UPDATE national_send_attempt SET result='FAILED',finished_at=?,error_summary='服务重启，队列发送未完成' WHERE result='SENDING' AND batch_id IN (SELECT batch_id FROM national_batch WHERE schema_version='demo-2')",utc(now));
    }
    public void store(String batch,int index,int count,String hash,String chunkHash,byte[] bytes,Instant now) {
        jdbc.update("""
                INSERT INTO national_chunk_in(batch_id,chunk_index,chunk_count,payload_sha256,
                    chunk_sha256,payload_bytes,received_at) VALUES(?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE batch_id=batch_id
                """,batch,index,count,hash,chunkHash,bytes,utc(now));
    }
    public List<Chunk> chunks(String batch) {
        return jdbc.query("SELECT * FROM national_chunk_in WHERE batch_id=? ORDER BY chunk_index",
                (rs,i)->new Chunk(rs.getInt("chunk_index"),rs.getInt("chunk_count"),
                        rs.getString("payload_sha256"),rs.getString("chunk_sha256"),
                        rs.getBytes("payload_bytes")),batch);
    }
    public void receipt(String batch,String receipt,int enterprise,int real,int proxy,int count,
            String hash,Instant now) {
        jdbc.update("""
                INSERT INTO national_mock_receipt(batch_id,receipt_id,received_at,enterprise_count,
                    real_count,proxy_count,chunk_count,payload_sha256) VALUES(?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE batch_id=batch_id
                """,batch,receipt,utc(now),enterprise,real,proxy,count,hash);
        jdbc.update("UPDATE national_batch SET status='MOCK_ACCEPTED',receipt_id=?,receipt_at=?,last_error=NULL WHERE batch_id=? AND status IN ('SENDING','ASSEMBLING','SEND_FAILED')",
                receipt,utc(now),batch);
        jdbc.update("UPDATE national_send_attempt SET result='ACCEPTED',finished_at=?,mock_receipt_id=? WHERE batch_id=? AND result='SENDING'",
                utc(now),receipt,batch);
    }
    public int stored(String batch) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM national_chunk_in WHERE batch_id=?",Integer.class,batch);
        return n==null?0:n;
    }
    public int attemptCount(String batch) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM national_send_attempt WHERE batch_id=?",Integer.class,batch);
        return n==null?0:n;
    }
    public int confirmed(String batch) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM national_chunk_out WHERE batch_id=? AND broker_confirmed=true",Integer.class,batch);
        return n==null?0:n;
    }
    public Receipt receiptByBatch(String batch) {
        return jdbc.query("SELECT * FROM national_mock_receipt WHERE batch_id=?",
                (rs,i)->new Receipt(rs.getString("receipt_id"),instant(rs,"received_at"),
                        rs.getInt("enterprise_count"),rs.getInt("real_count"),
                        rs.getInt("proxy_count"),rs.getInt("chunk_count"),
                        rs.getString("payload_sha256")),batch).stream().findFirst().orElse(null);
    }
    private Proxy proxy(ResultSet rs,int i) throws SQLException {
        return new Proxy(rs.getLong("enterprise_org_id"),rs.getString("period_code"),
                rs.getLong("province_org_id"),rs.getString("organization_code"),
                rs.getString("enterprise_name"),rs.getString("region_code"),
                rs.getString("nature_code"),rs.getString("industry_code"),
                rs.getString("source_period_code"),rs.getLong("source_report_id"),
                rs.getInt("source_submission_version"),rs.getInt("source_current_version"),
                rs.getInt("employment_count"),rs.getLong("generated_by"),
                instant(rs,"generated_at"));
    }
    private Batch mapBatch(ResultSet rs,int i) throws SQLException {
        return new Batch(rs.getString("batch_id"),rs.getString("period_code"),
                rs.getString("status"),rs.getString("schema_version"),rs.getString("mode"),
                rs.getString("payload_format"),rs.getString("payload_xml"),
                rs.getString("payload_sha256"),rs.getInt("enterprise_count"),
                rs.getInt("real_count"),rs.getInt("proxy_count"),rs.getInt("chunk_count"),
                rs.getLong("baseline_total"),rs.getLong("period_total"));
    }
    private static Instant instant(ResultSet rs,String column) throws SQLException {
        LocalDateTime t=rs.getObject(column,LocalDateTime.class);
        return t==null?null:t.toInstant(ZoneOffset.UTC);
    }
    private static LocalDateTime utc(Instant t) { return LocalDateTime.ofInstant(t,ZoneOffset.UTC); }
    public record Source(long reportId,String period,int submission,int version,int employment) { }
    public record Proxy(long orgId,String period,long province,String orgCode,String name,
            String region,String nature,String industry,String sourcePeriod,long sourceReport,
            int sourceSubmission,int sourceVersion,int employment,long generatedBy,Instant generatedAt) { }
    public record Batch(String id,String period,String status,String schema,String mode,
            String format,String xml,String hash,int enterprise,int real,int proxy,int chunks,
            long baseline,long current) { }
    public record Chunk(int index,int count,String hash,String chunkHash,byte[] bytes) { }
    public record Receipt(String id,Instant at,int enterprise,int real,int proxy,int chunks,String hash) { }
}
