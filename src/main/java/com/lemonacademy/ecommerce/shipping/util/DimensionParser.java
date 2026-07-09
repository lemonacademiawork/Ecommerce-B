package com.lemonacademy.ecommerce.shipping.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DimensionParser {
    public static int[] parse(String dimensions, int defaultVal) {
        if (dimensions == null || dimensions.isEmpty()) {
            return new int[]{defaultVal, defaultVal, defaultVal};
        }
        try {
            String[] parts = dimensions.toLowerCase().split("[x\\*,\\s]+");
            if (parts.length >= 3) {
                return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
                };
            }
        } catch (Exception e) {
            log.error("Failed to parse dimensions: {}. Using default {}", dimensions, defaultVal);
        }
        return new int[]{defaultVal, defaultVal, defaultVal};
    }
}
