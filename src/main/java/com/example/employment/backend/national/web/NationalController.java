package com.example.employment.backend.national.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.national.service.NationalService;
import com.example.employment.backend.midterm.national.service.NationalV2Service;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/national")
public class NationalController {
    private final NationalService service;
    private final NationalV2Service v2;
    public NationalController(NationalService service,NationalV2Service v2) {
        this.service = service;this.v2=v2;
    }

    @GetMapping("/periods/{periodCode}/readiness")
    public ApiResponse<?> readiness(Authentication auth, @PathVariable String periodCode) {
        return ApiResponse.ok(v2.readiness(user(auth), periodCode));
    }

    @PostMapping("/periods/{periodCode}/proxy-fill")
    public ApiResponse<?> fill(Authentication auth,@PathVariable String periodCode,
            @RequestBody(required=false) Map<String,Object> body) {
        if(body==null) body=Map.of();
        StrictBody.only(body,"expectedVersion");
        return ApiResponse.ok(v2.fill(user(auth),periodCode,version(body)));
    }

    @DeleteMapping("/periods/{periodCode}/proxy-fill")
    public ApiResponse<?> delete(Authentication auth,@PathVariable String periodCode,
            @RequestParam(required=false) Integer expectedVersion) {
        return ApiResponse.ok(v2.delete(user(auth),periodCode,expectedVersion));
    }

    @PostMapping("/batches")
    public ResponseEntity<ApiResponse<?>> create(Authentication auth,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "periodCode", "schemaVersion");
        if (!body.containsKey("periodCode")) throw field("periodCode", "必填");
        if (!body.containsKey("schemaVersion")) throw field("schemaVersion", "必填");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                v2.create(user(auth), StrictBody.string(body, "periodCode"),
                        StrictBody.string(body,"schemaVersion"))));
    }

    @GetMapping("/batches")
    public ApiResponse<?> list(Authentication auth, @RequestParam Map<String, String> params) {
        for (String key : params.keySet()) if (!java.util.Set.of("periodCode", "status", "page", "size")
                .contains(key)) throw field(key, "不支持该筛选字段");
        return ApiResponse.ok(v2.list(user(auth), params.get("periodCode"),
                params.get("status"), number(params, "page", 0), number(params, "size", 20)));
    }

    @GetMapping("/batches/{batchId}")
    public ApiResponse<?> detail(Authentication auth, @PathVariable String batchId) {
        boolean isV2=v2.isV2(user(auth),batchId);
        return ApiResponse.ok(v2.detail(user(auth),batchId,
                isV2?null:service.detail(user(auth),batchId).payload()));
    }

    @PostMapping("/batches/{batchId}/retry")
    public ResponseEntity<ApiResponse<?>> retry(Authentication auth, @PathVariable String batchId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.ok(v2.retry(user(auth), batchId)));
    }

    private static int number(Map<String, String> params, String key, int fallback) {
        if (!params.containsKey(key)) return fallback;
        try { return Integer.parseInt(params.get(key)); }
        catch (NumberFormatException ex) { throw field(key, "应为整数"); }
    }
    private static Integer version(Map<String,Object> body) {
        if(!body.containsKey("expectedVersion")) return null;
        if(!(body.get("expectedVersion") instanceof Integer value) || value<0)
            throw field("expectedVersion","应为非负整数");
        return value;
    }
    private static FieldErrorException field(String key, String message) {
        return new FieldErrorException(Map.of(key, message));
    }
    private static UserAccount user(Authentication auth) { return (UserAccount) auth.getPrincipal(); }
}
