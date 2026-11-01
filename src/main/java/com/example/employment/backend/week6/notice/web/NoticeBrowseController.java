package com.example.employment.backend.week6.notice.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week6.notice.service.NoticeService;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeBrowseController {
    private final NoticeService notices;
    public NoticeBrowseController(NoticeService notices) { this.notices = notices; }
    @GetMapping
    public ApiResponse<?> list(Authentication auth, @RequestParam Map<String,String> params) {
        for (String key : params.keySet()) if (!Set.of("page","size").contains(key))
            throw new FieldErrorException(Map.of(key,"不支持该筛选字段"));
        return ApiResponse.ok(notices.list(user(auth),number(params,"page",0),
                number(params,"size",20)));
    }
    @GetMapping("/{noticeId}")
    public ApiResponse<?> detail(Authentication auth, @PathVariable String noticeId) {
        return ApiResponse.ok(notices.detail(user(auth),noticeId));
    }
    private static int number(Map<String,String> params,String key,int fallback) {
        if (!params.containsKey(key)) return fallback;
        try { return Integer.parseInt(params.get(key)); }
        catch (NumberFormatException ex) { throw new FieldErrorException(Map.of(key,"应为整数")); }
    }
    private static UserAccount user(Authentication auth) { return (UserAccount)auth.getPrincipal(); }
}
