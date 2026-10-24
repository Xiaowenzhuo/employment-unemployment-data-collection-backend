package com.example.employment.backend.week2.filing.service;

import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.filing.dao.FilingDao.ListRow;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FilingExportService {
    private static final String[] HEADERS = {
            "组织机构代码", "企业名称", "所属地区", "企业性质", "所属行业", "主要经营业务",
            "联系人", "联系地址", "邮政编码", "联系电话", "传真", "EMAIL", "备案通过时间"
    };
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
    private final FilingService filings;
    private final RegionScopeRepository regions;

    public FilingExportService(FilingService filings, RegionScopeRepository regions) {
        this.filings = filings;
        this.regions = regions;
    }

    @Transactional
    public byte[] export(UserAccount actor, String regionCode, String periodCode) {
        List<ListRow> rows = filings.exportRows(actor, regionCode, periodCode);
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("已备案企业");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int index = 1;
            for (ListRow item : rows) {
                var p = item.profile();
                String region = String.join("/", regions.info(item.regionCode()).path().stream()
                        .map(node -> node.name()).toList());
                String[] values = {
                        p.organizationCode(), p.enterpriseName(), region,
                        filings.optionName(p.natureCode()), filings.optionName(p.industryCode()),
                        p.mainBusiness(), p.contactName(), filings.optionName(p.addressParentCode())
                                + "/" + filings.optionName(p.addressCode()),
                        p.postalCode(), p.contactPhone(), p.fax(), p.email(),
                        TIME.format(item.reviewedAt())
                };
                Row row = sheet.createRow(index++);
                for (int i = 0; i < values.length; i++) {
                    row.createCell(i).setCellValue(values[i] == null ? "" : values[i]);
                }
            }
            workbook.write(bytes);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Excel export failed", exception);
        }
    }
}
