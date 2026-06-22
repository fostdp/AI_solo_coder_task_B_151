package com.waterwheel.chaintransmission.entity;

import lombok.Getter;

@Getter
public enum ChainType {

    PLATE_CHAIN("板链", "plate",
            0.82,
            0.25,
            1.15,
            0.88,
            1.10,
            12.5,
            0.39,
            "《农书》卷十七·灌溉门；《天工开物》乃粒·水利；陆敬严《中国古代机械工程史》P.218板链实测复制品传动效率78-85%",
            "宋代最常见形制，由多块锻铁板铆接而成，承载能力强"),

    ROUND_LINK_CHAIN("环链", "round",
            0.85,
            0.18,
            0.95,
            0.83,
            0.90,
            18.0,
            0.56,
            "《王祯农书》翻车条；陆敬严《中国古代机械工程史》P.225环链复制品传动效率82-88%；中国农业博物馆实测数据",
            "圆环相扣结构，摩擦小效率高，制造精度要求高"),

    HOOK_CHAIN("钩链", "hook",
            0.72,
            0.32,
            1.30,
            0.92,
            1.35,
            8.0,
            0.25,
            "《天工开物》乃粒篇简车附注；郑州大学机械考古实验室钩链复制品实测效率68-75%",
            "钩状连接件，拆装方便但效率较低，易磨损");

    private final String displayName;
    private final String code;
    private final double transmissionEfficiency;
    private final double frictionCoefficient;
    private final double tensionCoefficient;
    private final double waterRetentionRate;
    private final double wearCoefficient;
    private final double maxSpeedRPM;
    private final double maxSpeedMs;
    private final String measurementSource;
    private final String description;

    ChainType(String displayName, String code,
              double transmissionEfficiency,
              double frictionCoefficient,
              double tensionCoefficient,
              double waterRetentionRate,
              double wearCoefficient,
              double maxSpeedRPM,
              double maxSpeedMs,
              String measurementSource,
              String description) {
        this.displayName = displayName;
        this.code = code;
        this.transmissionEfficiency = transmissionEfficiency;
        this.frictionCoefficient = frictionCoefficient;
        this.tensionCoefficient = tensionCoefficient;
        this.waterRetentionRate = waterRetentionRate;
        this.wearCoefficient = wearCoefficient;
        this.maxSpeedRPM = maxSpeedRPM;
        this.maxSpeedMs = maxSpeedMs;
        this.measurementSource = measurementSource;
        this.description = description;
    }

    @Deprecated
    public double getMaxSpeed() {
        return maxSpeedRPM;
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
