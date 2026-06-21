package com.waterwheel.chaintransmission.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class VirtualOperationDTO {

    private Integer deviceId;
    private double chainSpeedMs;
    private double waterLevelFactor;
    private boolean manualMode;

    private BigDecimal currentWaterFlowLh;
    private BigDecimal instantaneousFlowLs;
    private BigDecimal chainTensionN;
    private BigDecimal sprocketRPM;
    private BigDecimal driveTorqueNm;
    private BigDecimal powerConsumptionW;
    private BigDecimal scraperLoadN;
    private BigDecimal vibrationAmplitudeMm;
    private BigDecimal efficiencyPercent;

    private BigDecimal totalWaterLiters;
    private int operationSeconds;

    private List<BigDecimal> tensionDistribution;
    private List<Float> chainLinkPositions;
    private String operationStatus;
    private List<String> warnings;
}
