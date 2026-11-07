package com.example.employment.backend.revision.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.revision.service.ProgressService;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/enterprise-progress")
public class ProgressController {
    private final ProgressService progress;
    public ProgressController(ProgressService progress) { this.progress=progress; }
    @GetMapping
    public ApiResponse<?> list(Authentication auth,@RequestParam Map<String,String> params) {
        for (String key:params.keySet())
            if (!Set.of("periodCode","cityCode","filingStatus","reportStatus","page","size")
                    .contains(key)) throw new FieldErrorException(Map.of(key,"不支持该筛选字段"));
        return ApiResponse.ok(progress.page((UserAccount)auth.getPrincipal(),
                params.get("periodCode"),params.get("cityCode"),params.get("filingStatus"),
                params.get("reportStatus"),number(params,"page",0),number(params,"size",20)));
    }
    private static int number(Map<String,String> params,String name,int fallback) {
        if (!params.containsKey(name)) return fallback;
        try { return Integer.parseInt(params.get(name)); }
        catch (NumberFormatException ex) { throw new FieldErrorException(Map.of(name,"应为整数")); }
    }
}
