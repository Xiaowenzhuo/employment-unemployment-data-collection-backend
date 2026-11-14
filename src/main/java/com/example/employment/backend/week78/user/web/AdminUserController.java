package com.example.employment.backend.week78.user.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import com.example.employment.backend.week78.shared.AdminInputs;
import com.example.employment.backend.week78.user.service.AdminUserService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/admin")
public class AdminUserController {
    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping("/organizations")
    public ApiResponse<?> organizations(Authentication auth,
            @RequestParam(required = false) String orgType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(users.organizations(actor(auth),orgType,keyword,page,size));
    }

    @GetMapping("/users")
    public ApiResponse<?> list(Authentication auth,
            @RequestParam(required = false) String orgId,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String functionalRoleId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(users.list(actor(auth),orgId,roleCode,enabled,keyword,
                functionalRoleId,page,size));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<?>> create(Authentication auth,
            @RequestBody Map<String,Object> body) {
        StrictBody.only(body,"orgId","username","displayName","initialPassword",
                "functionalRoleIds");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(users.create(actor(auth),body)));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<?> detail(Authentication auth,@PathVariable String id) {
        return ApiResponse.ok(users.detail(actor(auth),AdminInputs.id(id,"id")));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<?> update(Authentication auth,@PathVariable String id,
            @RequestBody Map<String,Object> body) {
        StrictBody.only(body,"displayName","functionalRoleIds","expectedVersion");
        return ApiResponse.ok(users.update(actor(auth),AdminInputs.id(id,"id"),body));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<?> delete(Authentication auth,@PathVariable String id,
            @RequestBody Map<String,Object> body) {
        StrictBody.only(body,"expectedVersion");
        return ApiResponse.ok(users.delete(actor(auth),AdminInputs.id(id,"id"),body));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<?> status(Authentication auth,@PathVariable String id,
            @RequestBody Map<String,Object> body) {
        StrictBody.only(body,"enabled","expectedVersion");
        return ApiResponse.ok(users.status(actor(auth),AdminInputs.id(id,"id"),body));
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<?> reset(Authentication auth,@PathVariable String id,
            @RequestBody Map<String,Object> body) {
        StrictBody.only(body,"newPassword","expectedVersion");
        return ApiResponse.ok(users.resetPassword(actor(auth),AdminInputs.id(id,"id"),body));
    }

    private UserAccount actor(Authentication auth) {
        return (UserAccount)auth.getPrincipal();
    }
}
