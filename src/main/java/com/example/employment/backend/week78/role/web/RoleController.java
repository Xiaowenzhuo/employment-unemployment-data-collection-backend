package com.example.employment.backend.week78.role.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import com.example.employment.backend.week78.role.service.RoleService;
import com.example.employment.backend.week78.shared.AdminInputs;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
public class RoleController {
    private final RoleService roles;

    public RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping("/permissions")
    public ApiResponse<?> permissions(Authentication auth) {
        return ApiResponse.ok(roles.permissions(actor(auth)));
    }

    @GetMapping("/roles")
    public ApiResponse<?> list(Authentication auth, @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(roles.list(actor(auth), level, keyword, page, size));
    }

    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<?>> create(Authentication auth,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "name", "level", "permissionCodes");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(roles.create(actor(auth), body)));
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<?> detail(Authentication auth, @PathVariable String id) {
        return ApiResponse.ok(roles.detail(actor(auth), AdminInputs.id(id, "id")));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<?> update(Authentication auth, @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "name", "permissionCodes", "expectedVersion");
        return ApiResponse.ok(roles.update(actor(auth), AdminInputs.id(id, "id"), body));
    }

    @PostMapping("/roles/{id}/replace-and-delete")
    public ApiResponse<?> delete(Authentication auth, @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion", "replacementRoleId");
        return ApiResponse.ok(roles.delete(actor(auth), AdminInputs.id(id, "id"), body));
    }

    private UserAccount actor(Authentication auth) {
        return (UserAccount) auth.getPrincipal();
    }
}
