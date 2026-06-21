package com.waterwheel.chaintransmission.entity;

import lombok.Getter;

@Getter
public enum EraType {

    ANCIENT_SONG("宋代水转翻车", "ancient_song",
            "wood_frame",
            "wrought_iron",
            "water_wheel",
            0.65,
            0.55,
            0.0,
            "宋代（960-1279）水利技术巅峰，以水轮驱动木质车架锻铁链条",
            "水力",
            3.5,
            15.0),

    MODERN_ELECTRIC("现代电动链式泵", "modern_electric",
            "steel_frame",
            "alloy_steel",
            "electric_motor",
            0.95,
            0.88,
            0.82,
            "现代工业链式水泵，合金钢链条+变频电机+自动控制",
            "电力",
            22.0,
            380.0);

    private final String displayName;
    private final String code;
    private final String frameMaterial;
    private final String chainMaterial;
    private final String driveType;
    private final double mechanicalEfficiency;
    private final double transmissionEfficiency;
    private final double controlEfficiency;
    private final String description;
    private final String powerSource;
    private final double typicalSpeedRPM;
    private final double typicalPowerKW;

    EraType(String displayName, String code,
            String frameMaterial, String chainMaterial, String driveType,
            double mechanicalEfficiency, double transmissionEfficiency, double controlEfficiency,
            String description, String powerSource,
            double typicalSpeedRPM, double typicalPowerKW) {
        this.displayName = displayName;
        this.code = code;
        this.frameMaterial = frameMaterial;
        this.chainMaterial = chainMaterial;
        this.driveType = driveType;
        this.mechanicalEfficiency = mechanicalEfficiency;
        this.transmissionEfficiency = transmissionEfficiency;
        this.controlEfficiency = controlEfficiency;
        this.description = description;
        this.powerSource = powerSource;
        this.typicalSpeedRPM = typicalSpeedRPM;
        this.typicalPowerKW = typicalPowerKW;
    }

    public double getTotalEfficiency() {
        return mechanicalEfficiency * transmissionEfficiency * (controlEfficiency == 0 ? 1.0 : controlEfficiency);
    }

    public static EraType fromCode(String code) {
        for (EraType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return ANCIENT_SONG;
    }
}
