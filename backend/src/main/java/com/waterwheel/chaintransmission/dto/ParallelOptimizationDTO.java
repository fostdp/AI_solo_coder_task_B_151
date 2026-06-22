package com.waterwheel.chaintransmission.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ParallelOptimizationDTO {

    private List<Integer> deviceIds;
    private double targetTotalFlowLh;
    private double maxTotalPowerKW;
    private String optimizationGoal;

    private boolean converged;
    private int iterations;
    private long computationTimeMs;

    private BigDecimal totalPredictedFlowLh;
    private BigDecimal totalPowerConsumptionKW;
    private BigDecimal averageEfficiency;
    private BigDecimal loadBalanceStdDev;
    private BigDecimal coordinationGain;

    private List<DeviceAssignment> assignments;
    private List<OptimizationTracePoint> trace;
    private Map<String, Object> couplingInfo;

    @Data
    public static class DeviceAssignment {
        private Integer deviceId;
        private String deviceName;

        private BigDecimal assignedFlowRatio;
        private BigDecimal assignedFlowLh;
        private BigDecimal optimalChainSpeedMs;
        private BigDecimal optimalSprocketRPM;
        private BigDecimal scraperDepth;
        private BigDecimal predictedTensionN;
        private BigDecimal powerConsumptionKW;
        private BigDecimal individualEfficiency;
        private Integer status;
    }

    @Data
    public static class OptimizationTracePoint {
        private int iteration;
        private BigDecimal totalFlow;
        private BigDecimal totalPower;
        private BigDecimal objectiveValue;
    }
}
