package com.example.employment.backend.week1.region.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.BadRequestException;
import com.example.employment.backend.week1.region.model.Region;
import com.example.employment.backend.week1.region.service.RegionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta/regions")
public class RegionController {
    private final RegionService regions;

    public RegionController(RegionService regions) {
        this.regions = regions;
    }

    @GetMapping
    public ApiResponse<List<Region>> list(@RequestParam(required = false) String parentCode) {
        if (parentCode != null && parentCode.length() > 12) {
            throw new BadRequestException();
        }
        return ApiResponse.ok(regions.children(parentCode));
    }
}
