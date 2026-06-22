package com.waterwheel.chaintransmission.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ChainTypeComparisonDTO {

    private Integer deviceId;
    private String deviceName;
    private double inputSpeedRPM;
    private double inputTorque;
    private double scraperDepth;
    private double scraperWidth;
    private double scraperAngle;

    private List<ChainTypeResult> results;

    @Data
    public static class ChainTypeResult {
        private String chainTypeCode;
        private String chainTypeName;
        private String description;

        private BigDecimal transmissionEfficiency;
        private BigDecimal actualWaterFlowLh;
        private BigDecimal powerConsumptionW;
        private BigDecimal chainTensionN;
        private BigDecimal frictionLossW;
        private BigDecimal wearRate;
        private BigDecimal expectedLifespanHours;
        private BigDecimal maxAllowableSpeed;
        private boolean resonanceRisk;
        private List<BigDecimal> vibrationFrequencies;
        private Map<String, Object> detailedMetrics;
    }
}
