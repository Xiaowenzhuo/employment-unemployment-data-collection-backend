package com.example.employment.backend.reminder.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.reminder.service.ReminderService;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/notices/report-reminders")
public class ReminderController {
    private final ReminderService reminders;
    public ReminderController(ReminderService reminders) { this.reminders=reminders; }
    @PostMapping("/preview")
    public ApiResponse<?> preview(Authentication auth,@RequestBody Map<String,Object> body) {
        StrictBody.only(body,"periodCode","audience");
        return ApiResponse.success(reminders.preview((UserAccount)auth.getPrincipal(),
                StrictBody.string(body,"periodCode"),StrictBody.string(body,"audience")));
    }
    @PostMapping
    public ApiResponse<?> send(Authentication auth,@RequestBody Map<String,Object> body) {
        StrictBody.only(body,"previewId","title","content");
        return ApiResponse.success(reminders.send((UserAccount)auth.getPrincipal(),
                StrictBody.string(body,"previewId"),StrictBody.string(body,"title"),
                StrictBody.string(body,"content")));
    }
}
