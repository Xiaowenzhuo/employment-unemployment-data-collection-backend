package com.example.employment.backend.week6.notice.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import com.example.employment.backend.week6.notice.service.NoticeService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/notices")
public class ProvinceNoticeController {
    private final NoticeService notices;
    public ProvinceNoticeController(NoticeService notices) { this.notices = notices; }
    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(Authentication auth,
            @RequestBody Map<String,Object> body) {
        StrictBody.only(body,"title","content");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(notices.create(
                user(auth),StrictBody.string(body,"title"),StrictBody.string(body,"content"))));
    }
    @PutMapping("/{noticeId}")
    public ApiResponse<?> update(Authentication auth,@PathVariable String noticeId,
            @RequestBody Map<String,Object> body) {
        StrictBody.only(body,"expectedVersion","title","content");
        return ApiResponse.ok(notices.update(user(auth),noticeId,
                StrictBody.integer(body,"expectedVersion"),StrictBody.string(body,"title"),
                StrictBody.string(body,"content")));
    }
    @DeleteMapping("/{noticeId}")
    public ApiResponse<?> delete(Authentication auth,@PathVariable String noticeId,
            @RequestParam Map<String,String> params) {
        for (String key : params.keySet()) if (!"expectedVersion".equals(key))
            throw new FieldErrorException(Map.of(key,"不支持该参数"));
        Integer version = null;
        if (params.containsKey("expectedVersion")) {
            try { version = Integer.parseInt(params.get("expectedVersion")); }
            catch (NumberFormatException ex) {
                throw new FieldErrorException(Map.of("expectedVersion","应为整数"));
            }
        }
        return ApiResponse.ok(notices.delete(user(auth),noticeId,version));
    }
    private static UserAccount user(Authentication auth) { return (UserAccount)auth.getPrincipal(); }
}
