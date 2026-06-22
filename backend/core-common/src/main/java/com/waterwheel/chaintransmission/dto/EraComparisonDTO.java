package com.waterwheel.chaintransmission.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class EraComparisonDTO {

    private Integer deviceId;
    private String referenceDeviceName;
    private double chainSpeedRatio;
    private double scraperSizeScale;

    private List<EraResult> results;
    private List<ComparisonMetric> keyMetrics;

    @Data
    public static class EraResult {
        private String eraCode;
        private String eraName;
        private String description;
        private String frameMaterial;
        private String chainMaterial;
        private String driveType;
        private String powerSource;

        private BigDecimal mechanicalEfficiency;
        private BigDecimal transmissionEfficiency;
        private BigDecimal controlEfficiency;
        private BigDecimal totalEfficiency;

        private BigDecimal waterFlowLh;
        private BigDecimal powerInputKW;
        private BigDecimal powerOutputKW;
        private BigDecimal energyCostPerCubicYuan;
        private BigDecimal chainTensionN;
        private BigDecimal typicalSpeedRPM;
        private BigDecimal typicalPowerKW;
        private BigDecimal noiseLevelDB;
        private BigDecimal maintenanceHoursPerYear;
        private BigDecimal lifespanYears;
        private BigDecimal costFactor;
        private Map<String, Object> historicalContext;
    }

    @Data
    public static class ComparisonMetric {
        private String metricName;
        private String unit;
        private BigDecimal ancientValue;
        private BigDecimal modernValue;
        private BigDecimal improvementRatio;
        private String improvementDescription;
    }
}
