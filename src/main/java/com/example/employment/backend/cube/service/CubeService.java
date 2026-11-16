package com.example.employment.backend.cube.service;

import com.example.employment.backend.cube.dao.CubeDao.Fact;
import com.example.employment.backend.cube.repository.CubeRepository;
import com.example.employment.backend.midterm.warehouse.service.WarehouseSyncService;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CubeService {
    private static final int MAX_CELLS=1800,MAX_AXIS=24;
    private static final long MAX_SAFE=9007199254740991L;
    private static final ZoneId BEIJING=ZoneId.of("Asia/Shanghai");
    private static final List<String> LEVELS=List.of("province","city","county",
            "industryGroup","industry","nature","year","quarter","month");
    private final CubeRepository cube;
    private final ScopeService scopes;
    private final WarehouseSyncService sync;
    public CubeService(CubeRepository cube,ScopeService scopes,WarehouseSyncService sync) {
        this.cube=cube;this.scopes=scopes;this.sync=sync;
    }
    public Metadata metadata(UserAccount actor) {
        var province=scopes.province(actor);
        Map<String,LinkedHashMap<String,Member>> values=new LinkedHashMap<>();
        for(String level:LEVELS) values.put(level,new LinkedHashMap<>());
        for(Map<String,Object> row:cube.regions(province.regionCode())) {
            int level=((Number)row.get("level")).intValue();
            String key=switch(level) {case 1->"province";case 2->"city";case 3->"county";default->null;};
            if(key!=null) put(values,key,row.get("code").toString(),row.get("name").toString(),
                    row.get("parent_code")==null?null:row.get("parent_code").toString());
        }
        for(Member city:values.get("city").values())
            put(values,"county",city.id()+":UNCLASSIFIED","未细分区县",city.id());
        for(Map<String,Object> row:cube.options("INDUSTRY")) {
            String code=row.get("code").toString(),parent=(String)row.get("parent_code");
            put(values,parent==null?"industryGroup":"industry",code,row.get("name").toString(),parent);
        }
        for(Map<String,Object> row:cube.options("NATURE")) {
            if(row.get("parent_code")!=null)
                put(values,"nature",row.get("code").toString(),row.get("name").toString(),null);
        }
        put(values,"industryGroup","UNCLASSIFIED","未分类",null);
        put(values,"industry","UNCLASSIFIED","未分类","UNCLASSIFIED");
        put(values,"nature","UNCLASSIFIED","未分类",null);
        for(var time:cube.periods(province.id())) {
            YearMonth month=month(time);
            String y=Integer.toString(month.getYear()),q=y+"-Q"+((month.getMonthValue()-1)/3+1);
            put(values,"year",y,y+" 年",null);
            put(values,"quarter",q,y+" 年第 "+((month.getMonthValue()-1)/3+1)+" 季度",y);
            put(values,"month",month.toString(),month.getYear()+" 年 "+month.getMonthValue()+" 月",q);
        }
        Map<String,List<Member>> members=new LinkedHashMap<>();
        values.forEach((level,items)->members.put(level,new ArrayList<>(items.values())));
        return new Metadata(MAX_CELLS,MAX_AXIS,members);
    }
    private static void put(Map<String,LinkedHashMap<String,Member>> data,String level,
            String id,String label,String parent) {
        data.get(level).putIfAbsent(id,new Member(id,label,parent));
    }
    public QueryResult query(UserAccount actor,Map<String,Object> body) {
        Request request=parse(body,false,metadata(actor));
        List<Row> rows=filtered(actor,request);
        if(rows.isEmpty()) return new QueryResult(List.of(List.of(),List.of(),List.of()),List.of());
        List<List<Member>> axes=new ArrayList<>();
        long combinations=1;
        for(String axis:request.axes()) {
            Set<String> used=new HashSet<>();
            rows.forEach(row->used.add(row.ids().get(axis)));
            List<Member> members=request.metadata().members().get(axis).stream()
                    .filter(member->used.contains(member.id())).toList();
            if(members.size()>MAX_AXIS || combinations*members.size()>MAX_CELLS) throw limit();
            combinations*=members.size();
            axes.add(members);
        }
        Map<List<String>,Accumulator> groups=new LinkedHashMap<>();
        for(Row row:rows) {
            List<String> coordinates=request.axes().stream().map(row.ids()::get).toList();
            groups.computeIfAbsent(coordinates,key->new Accumulator()).add(row);
        }
        List<Cell> cells=groups.entrySet().stream()
                .map(entry->new Cell(entry.getKey(),entry.getValue().months.size(),
                        entry.getValue().metrics())).toList();
        return new QueryResult(axes,cells);
    }
    public Detail detail(UserAccount actor,Map<String,Object> body) {
        Request request=parse(body,true,metadata(actor));
        @SuppressWarnings("unchecked") List<String> coordinates=(List<String>)body.get("coordinates");
        List<Row> rows=filtered(actor,request).stream().filter(row->{
            for(int i=0;i<3;i++) if(!coordinates.get(i).equals(row.ids().get(request.axes().get(i))))
                return false;
            return true;
        }).toList();
        Map<String,List<Member>> members=new LinkedHashMap<>();
        for(String level:LEVELS) {
            Set<String> used=new HashSet<>();
            rows.forEach(row->used.add(row.ids().get(level)));
            members.put(level,request.metadata().members().get(level).stream()
                    .filter(member->used.contains(member.id())).toList());
        }
        Accumulator total=new Accumulator();
        rows.forEach(total::add);
        return new Detail(members,rows.isEmpty()?new Metrics(null,null,null,null,null,null)
                :total.metrics());
    }
    private List<Row> filtered(UserAccount actor,Request request) {
        long province=scopes.province(actor).id();
        sync.sync();
        List<Row> result=new ArrayList<>();
        for(Fact fact:cube.facts(province)) {
            Map<String,String> ids=ids(fact,request.metadata());
            boolean matches=true;
            for(var filter:request.filters().entrySet())
                if(!filter.getValue().isEmpty() && !filter.getValue().contains(ids.get(filter.getKey()))) {
                    matches=false;break;
                }
            if(matches) result.add(new Row(fact,ids));
        }
        return result;
    }
    private Map<String,String> ids(Fact fact,Metadata metadata) {
        Map<String,String> ids=new HashMap<>();
        String city=fact.cityCode();
        String county=city.equals(fact.regionCode())?city+":UNCLASSIFIED":fact.regionCode();
        ids.put("province",metadata.members().get("province").getFirst().id());
        ids.put("city",city);
        ids.put("county",county);
        String industry=fact.industryCode();
        Member industryMember=industry==null?null:metadata.members().get("industry").stream()
                .filter(member->member.id().equals(industry)).findFirst().orElse(null);
        ids.put("industryGroup",fact.industryParentCode()!=null?fact.industryParentCode()
                :industryMember!=null?industryMember.parentId():"UNCLASSIFIED");
        ids.put("industry",industryMember==null?"UNCLASSIFIED":industry);
        String nature=fact.natureCode();
        ids.put("nature",nature!=null && metadata.members().get("nature").stream()
                .anyMatch(member->member.id().equals(nature))?nature:"UNCLASSIFIED");
        YearMonth month=month(fact.startAt());
        ids.put("year",Integer.toString(month.getYear()));
        ids.put("quarter",month.getYear()+"-Q"+((month.getMonthValue()-1)/3+1));
        ids.put("month",month.toString());
        return ids;
    }
    private static YearMonth month(java.time.LocalDateTime utc) {
        return YearMonth.from(utc.toInstant(ZoneOffset.UTC).atZone(BEIJING));
    }
    private Request parse(Map<String,Object> body,boolean detail,Metadata metadata) {
        if(body==null || !body.keySet().equals(detail?Set.of("axes","filters","coordinates")
                :Set.of("axes","filters"))) throw invalid("INVALID_AXES","axes","请求字段不合法");
        if(!(body.get("axes") instanceof List<?> rawAxes) || rawAxes.size()!=3
                || rawAxes.stream().anyMatch(value->!(value instanceof String)
                || !LEVELS.contains(value))) throw invalid("INVALID_AXES","axes","须选择三个有效层级");
        List<String> axes=rawAxes.stream().map(Object::toString).toList();
        Set<String> families=new HashSet<>();
        for(String axis:axes) families.add(family(axis));
        if(families.size()!=3) throw invalid("INVALID_AXES","axes","三个轴须来自不同维度族");
        if(!(body.get("filters") instanceof Map<?,?> rawFilters))
            throw invalid("INVALID_FILTER","filters","须传筛选对象");
        Map<String,List<String>> filters=new LinkedHashMap<>();
        for(var entry:rawFilters.entrySet()) {
            if(!(entry.getKey() instanceof String level) || !LEVELS.contains(level)
                    || !(entry.getValue() instanceof List<?> values)
                    || values.stream().anyMatch(v->!(v instanceof String)))
                throw invalid("INVALID_FILTER","filters","筛选层级或值不合法");
            List<String> ids=values.stream().map(Object::toString).toList();
            for(String id:ids) if(metadata.members().get(level).stream().noneMatch(m->m.id().equals(id)))
                throw invalid("INVALID_FILTER","filters","筛选成员不存在或无权访问");
            filters.put(level,ids);
        }
        if(detail) {
            if(!(body.get("coordinates") instanceof List<?> coordinates)
                    || coordinates.size()!=3) throw invalid("INVALID_COORDINATES","coordinates","坐标不合法");
            for(int i=0;i<3;i++) {
                Object id=coordinates.get(i);
                String level=axes.get(i);
                if(!(id instanceof String value) || metadata.members().get(level).stream()
                        .noneMatch(m->m.id().equals(value)))
                    throw invalid("INVALID_COORDINATES","coordinates","坐标不存在或无权访问");
            }
        }
        return new Request(axes,filters,metadata);
    }
    private static String family(String level) {
        return switch(level) {
            case "province","city","county"->"region";
            case "industryGroup","industry"->"industry";
            case "year","quarter","month"->"period";
            default->"nature";
        };
    }
    private static CubeException invalid(String code,String field,String message) {
        return new CubeException(HttpStatus.BAD_REQUEST,code,message,Map.of(field,message));
    }
    private static CubeException limit() {
        return new CubeException(HttpStatus.UNPROCESSABLE_CONTENT,"CUBE_LIMIT_EXCEEDED",
                "维度组合过多，请缩小筛选范围或选择更高汇总层级",
                Map.of("axes","每轴最多 24 个成员，组合数不超过 1800"));
    }
    private static class Accumulator {
        long baseline,current,reduction;
        final Set<Long> companies=new HashSet<>();
        final Set<String> months=new HashSet<>();
        void add(Row row) {
            Fact f=row.fact();
            companies.add(f.enterpriseId());months.add(row.ids().get("month"));
            baseline=Math.addExact(baseline,f.baseline());
            current=Math.addExact(current,f.current());
            reduction=Math.addExact(reduction,Math.max(f.baseline()-f.current(),0));
        }
        Metrics metrics() {
            long net=current-baseline;
            if(baseline>MAX_SAFE || current>MAX_SAFE || reduction>MAX_SAFE || companies.size()>MAX_SAFE)
                throw limit();
            BigDecimal rate=baseline==0?null:BigDecimal.valueOf(net).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(baseline),2,RoundingMode.HALF_UP);
            return new Metrics((long)companies.size(),baseline,current,net,reduction,rate);
        }
    }
    private record Request(List<String> axes,Map<String,List<String>> filters,Metadata metadata) { }
    private record Row(Fact fact,Map<String,String> ids) { }
    public record Member(String id,String label,String parentId) { }
    public record Metadata(int maxCells,int maxAxisMembers,Map<String,List<Member>> members) { }
    public record Metrics(Long companies,Long baseline,Long current,Long net,Long reduction,BigDecimal rate) { }
    public record Cell(List<String> coordinates,int periodCount,Metrics metrics) { }
    public record QueryResult(List<List<Member>> members,List<Cell> cells) { }
    public record Detail(Map<String,List<Member>> members,Metrics metrics) { }
}
