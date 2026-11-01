package com.example.employment.backend.week5.export.service;

import com.example.employment.backend.week5.data.service.ProvinceDataService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExcelExportService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ProvinceDataService data;
    public ExcelExportService(ProvinceDataService data) { this.data = data; }
    public byte[] reports(ProvinceDataService.DataSet set) {
        String[] headers = {"调查期","报表ID","提交版本","企业ID","企业名称","组织机构代码",
                "城市","地区","建档期人数","调查期人数","净变化","其他原因","减少类型",
                "主要原因","主要原因说明","次要原因","次要原因说明","第三原因",
                "第三原因说明","修订次数","最近修订时间"};
        List<Object[]> rows = set.rows().stream().map(r -> {
            Map<String,Object> x = data.reportRow(r);
            var form = (com.example.employment.backend.week3.report.model.ReportForm)x.get("effectiveForm");
            return new Object[] {x.get("periodCode"),x.get("reportId"),x.get("submissionVersion"),
                    x.get("orgId"),x.get("enterpriseName"),x.get("organizationCode"),
                    x.get("cityName"),x.get("regionName"),form.baselineEmploymentCount(),
                    form.periodEmploymentCount(),form.periodEmploymentCount()-form.baselineEmploymentCount(),
                    form.otherReason(),form.reductionTypeCode(),form.primaryReasonCode(),
                    form.primaryReasonDetail(),form.secondaryReasonCode(),form.secondaryReasonDetail(),
                    form.tertiaryReasonCode(),form.tertiaryReasonDetail(),x.get("correctionCount"),
                    x.get("lastCorrectedAt")};
        }).toList();
        return workbook(headers, rows);
    }
    public byte[] enterpriseInfo(ProvinceDataService.DataSet set) {
        String[] headers = {"调查期","报表ID","提交版本","企业ID","组织机构代码","企业名称",
                "地区","企业性质","所属行业","主要经营业务","联系人","联系地址","邮政编码",
                "联系电话","传真","邮箱","备案来源","备案ID","备案版本"};
        List<Object[]> rows = set.rows().stream().map(r -> {
            Map<String,Object> x = data.infoRow(r);
            return new Object[] {x.get("periodCode"),x.get("reportId"),x.get("submissionVersion"),
                    x.get("orgId"),x.get("organizationCode"),x.get("enterpriseName"),
                    x.get("regionName"),x.get("natureName"),x.get("industryName"),
                    x.get("mainBusiness"),x.get("contactName"),x.get("addressName"),
                    x.get("postalCode"),x.get("contactPhone"),x.get("fax"),x.get("email"),
                    x.get("filingSource"),x.get("filingId"),x.get("filingVersion")};
        }).toList();
        return workbook(headers, rows);
    }
    public byte[] accounts(List<Map<String,Object>> accounts) {
        String[] headers = {"账号ID","单位名称","登录账号","用户类型","启用状态","所属省",
                "所属市","所属区县","所属区域","备案状态","报表状态","企业性质","行业","创建时间"};
        List<Object[]> rows = accounts.stream().map(x -> new Object[] {x.get("userId"),
                x.get("unitName"),x.get("username"),x.get("userType"),x.get("enabled"),
                x.get("provinceName"),x.get("cityName"),x.get("countyName"),
                x.get("regionName"),x.get("dataStatus"),x.get("reportStatus"),
                x.get("natureName"),x.get("industryName"),x.get("createdAt")}).toList();
        return workbook(headers, rows);
    }
    private static byte[] workbook(String[] headers, List<Object[]> rows) {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("数据");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int index = 1;
            for (Object[] values : rows) {
                Row row = sheet.createRow(index++);
                for (int i = 0; i < values.length; i++) write(row.createCell(i), values[i]);
            }
            book.write(output);
            return output.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("Excel 导出失败", ex); }
    }
    private static void write(Cell cell, Object value) {
        if (value == null) return;
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else if (value instanceof Instant instant) cell.setCellValue(TIME.format(instant.atZone(SHANGHAI)));
        else if (value instanceof LocalDateTime dateTime)
            cell.setCellValue(TIME.format(dateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(SHANGHAI)));
        else cell.setCellValue(value.toString());
    }
}
