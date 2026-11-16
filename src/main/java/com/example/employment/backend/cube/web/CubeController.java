package com.example.employment.backend.cube.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.cube.service.CubeService;
import com.example.employment.backend.week1.identity.model.UserAccount;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/analysis/cube")
public class CubeController {
    private final CubeService cube;
    public CubeController(CubeService cube) { this.cube=cube; }
    @GetMapping("/metadata")
    public ApiResponse<?> metadata(Authentication auth,@RequestParam Map<String,String> params) {
        if(!params.isEmpty()) throw new FieldErrorException(Map.of(
                params.keySet().iterator().next(),"不支持该参数"));
        return ApiResponse.success(cube.metadata((UserAccount)auth.getPrincipal()));
    }
    @PostMapping("/query")
    public ApiResponse<?> query(Authentication auth,@RequestBody Map<String,Object> body) {
        return ApiResponse.success(cube.query((UserAccount)auth.getPrincipal(),body));
    }
    @PostMapping("/cell-detail")
    public ApiResponse<?> detail(Authentication auth,@RequestBody Map<String,Object> body) {
        return ApiResponse.success(cube.detail((UserAccount)auth.getPrincipal(),body));
    }
}
