package com.example.employment.backend.midterm.national.service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.springframework.stereotype.Component;

@Component
public class XmlReport {
    public String create(String batch,String period,Instant start,Instant end,Instant now,
            List<Entry> entries) {
        try {
            StringWriter out=new StringWriter();
            XMLStreamWriter xml=XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8","1.0");
            xml.writeStartElement("ProvinceReport");xml.writeAttribute("schemaVersion","demo-2");
            xml.writeStartElement("Header");
            element(xml,"BatchId",batch);element(xml,"Mode","MOCK_QUEUE");
            element(xml,"ProvinceCode","53");element(xml,"PeriodCode",period);
            element(xml,"PeriodStartAt",start.toString());element(xml,"PeriodEndExclusiveAt",end.toString());
            element(xml,"GeneratedAt",now.toString());xml.writeEndElement();
            long baseline=entries.stream().mapToLong(Entry::baseline).sum();
            long current=entries.stream().mapToLong(Entry::current).sum();
            long real=entries.stream().filter(e->"REAL".equals(e.mode())).count();
            xml.writeStartElement("Summary");
            element(xml,"EnterpriseCount",entries.size());element(xml,"RealCount",real);
            element(xml,"ProxyCount",entries.size()-real);
            element(xml,"BaselineEmploymentTotal",baseline);
            element(xml,"PeriodEmploymentTotal",current);
            element(xml,"NetChange",current-baseline);xml.writeEndElement();
            xml.writeStartElement("Enterprises");
            for(Entry e:entries) {
                xml.writeStartElement("Enterprise");xml.writeAttribute("reportMode",e.mode());
                element(xml,"OrganizationCode",e.orgCode());element(xml,"EnterpriseName",e.name());
                element(xml,"RegionCode",e.region());element(xml,"NatureCode",e.nature());
                element(xml,"IndustryCode",e.industry());
                if("REAL".equals(e.mode())) {
                    element(xml,"ReportId",e.reportId());element(xml,"SubmissionVersion",e.submission());
                    element(xml,"CurrentVersion",e.version());
                    element(xml,"BaselineSourcePeriodCode",e.baselineSource());
                } else {
                    element(xml,"ProxySourcePeriodCode",e.sourcePeriod());
                    element(xml,"SourceReportId",e.reportId());
                    element(xml,"SourceSubmissionVersion",e.submission());
                    element(xml,"SourceCurrentVersion",e.version());
                }
                element(xml,"BaselineEmploymentCount",e.baseline());
                element(xml,"PeriodEmploymentCount",e.current());
                element(xml,"NetChange",e.current()-e.baseline());
                if(e.current()<e.baseline()) {
                    element(xml,"ReductionTypeCode",e.reductionType());
                    element(xml,"PrimaryReasonCode",e.primaryReason());
                    element(xml,"PrimaryReasonDetail",e.primaryDetail());
                    element(xml,"OtherReason",e.otherReason());
                }
                xml.writeEndElement();
            }
            xml.writeEndElement();xml.writeEndElement();xml.writeEndDocument();xml.close();
            String value=out.toString();validate(value);return value;
        } catch(Exception ex) { throw new IllegalStateException("XML 冻结报文不合法",ex); }
    }
    public void validate(String value) throws Exception {
        SchemaFactory factory=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        var schema=factory.newSchema(new StreamSource(
                getClass().getResourceAsStream("/national-v2.xsd")));
        var validator=schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        validator.validate(new StreamSource(new java.io.StringReader(value)));
    }
    private static void element(XMLStreamWriter xml,String name,Object value) throws Exception {
        if(value==null) throw new IllegalArgumentException(name+" 缺失");
        xml.writeStartElement(name);xml.writeCharacters(value.toString());xml.writeEndElement();
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    public static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    public record Entry(String mode,String orgCode,String name,String region,String nature,String industry,
            long reportId,int submission,int version,String baselineSource,String sourcePeriod,
            int baseline,int current,String reductionType,String primaryReason,
            String primaryDetail,String otherReason) { }
}
