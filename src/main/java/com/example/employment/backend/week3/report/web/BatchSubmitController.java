package com.example.employment.backend.week3.report.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import com.example.employment.backend.week3.report.service.BatchSubmitService;
import com.example.employment.backend.week3.report.service.BatchSubmitService.BatchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enterprise/reports")
public class BatchSubmitController {
    private final BatchSubmitService batches;
    public BatchSubmitController(BatchSubmitService batches) { this.batches=batches; }

    @PostMapping("/batch-submit")
    public ApiResponse<?> submit(Authentication auth,@RequestBody Map<String,Object> body) {
        StrictBody.only(body,"items");
        Object raw=body.get("items");
        if (!(raw instanceof List<?> list) || list.isEmpty())
            throw new FieldErrorException(Map.of("items","至少选择一份报表"));
        List<BatchRequest> items=new ArrayList<>();
        for (Object entry:list) {
            if (!(entry instanceof Map<?,?> values))
                throw new FieldErrorException(Map.of("items","每项应为对象"));
            Map<String,Object> item=new java.util.HashMap<>();
            for (var pair:values.entrySet()) {
                if (!(pair.getKey() instanceof String key))
                    throw new FieldErrorException(Map.of("items","字段名不合法"));
                item.put(key,pair.getValue());
            }
            StrictBody.only(item,"periodCode","expectedVersion");
            items.add(new BatchRequest(StrictBody.string(item,"periodCode"),
                    StrictBody.integer(item,"expectedVersion")));
        }
        return ApiResponse.ok(batches.submit((UserAccount)auth.getPrincipal(),items));
    }
}
