package com.example.employment.backend.week2.shared.model;

import java.util.List;

public record RegionInfo(String code, String name, List<Node> path) {
    public record Node(String code, String name, int level) {
    }
}
