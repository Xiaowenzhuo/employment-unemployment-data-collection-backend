package com.example.employment.backend.week1.region.service;

import com.example.employment.backend.week1.region.model.Region;
import com.example.employment.backend.week1.region.repository.RegionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RegionService {
    private final RegionRepository regions;

    public RegionService(RegionRepository regions) {
        this.regions = regions;
    }

    public List<Region> children(String parentCode) {
        return regions.findChildren(parentCode);
    }
}
