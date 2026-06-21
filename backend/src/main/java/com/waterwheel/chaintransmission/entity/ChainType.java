package com.waterwheel.chaintransmission.entity;

import lombok.Getter;

@Getter
public enum ChainType {

    PLATE_CHAIN("板链", "plate",
            0.88,
            0.18,
            1.15,
            0.90,
            1.00,
            12.5,
            "宋代最常见形制，由多块钢板铆接而成，承载能力强"),

    ROUND_LINK_CHAIN("环链", "round",
            0.92,
            0.12,
            0.95,
            0.85,
            0.85,
            18.0,
            "圆环相扣结构，摩擦小效率高，制造精度要求高"),

    HOOK_CHAIN("钩链", "hook",
            0.78,
            0.25,
            1.30,
            0.95,
            1.25,
            8.0,
            "钩状连接件，拆装方便但效率较低，易磨损");

    private final String displayName;
    private final String code;
    private final double transmissionEfficiency;
    private final double frictionCoefficient;
    private final double tensionCoefficient;
    private final double waterRetentionRate;
    private final double wearCoefficient;
    private final double maxSpeed;
    private final String description;

    ChainType(String displayName, String code,
              double transmissionEfficiency,
              double frictionCoefficient,
              double tensionCoefficient,
              double waterRetentionRate,
              double wearCoefficient,
              double maxSpeed,
              String description) {
        this.displayName = displayName;
        this.code = code;
        this.transmissionEfficiency = transmissionEfficiency;
        this.frictionCoefficient = frictionCoefficient;
        this.tensionCoefficient = tensionCoefficient;
        this.waterRetentionRate = waterRetentionRate;
        this.wearCoefficient = wearCoefficient;
        this.maxSpeed = maxSpeed;
        this.description = description;
    }

    public static ChainType fromCode(String code) {
        for (ChainType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return PLATE_CHAIN;
    }
}
