package com.waterwheel.chaintransmission.optimization;

import com.waterwheel.chaintransmission.dto.ParallelOptimizationDTO;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ParallelCoordinationOptimizer {

    @Autowired
    private WaterwheelDeviceRepository deviceRepository;

    @Autowired
    private WaterEfficiencyOptimizer efficiencyOptimizer;

    private static final double MIN_SPEED_MS = 0.5;
    private static final double MAX_SPEED_MS = 3.0;
    private static final double MAX_ITERATIONS = 200;
    private static final double TOLERANCE = 1e-5;
    private static final double HYDRAULIC_COUPLING_KAPPA = 0.08;

    public ParallelOptimizationDTO optimizeParallel(List<Integer> deviceIds,
                                                    Double targetTotalFlowLh,
                                                    Double maxTotalPowerKW,
                                                    String optimizationGoal) {
        long startTime = System.currentTimeMillis();
        String goal = optimizationGoal != null ? optimizationGoal : "BALANCED";
        double targetFlow = targetTotalFlowLh != null ? targetTotalFlowLh : 50000.0;
        double maxPower = maxTotalPowerKW != null ? maxTotalPowerKW : 20.0;

        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new IllegalArgumentException("至少需要1台设备");
        }

        List<WaterwheelDevice> devices = new ArrayList<>();
        for (Integer id : deviceIds) {
            deviceRepository.findById(id).ifPresent(devices::add);
        }
        int N = devices.size();
        if (N == 0) throw new IllegalArgumentException("设备ID全部无效");

        double couplingFactor = computeCouplingFactor(N);

        double[] speeds = new double[N];
        double[] depths = new double[N];
        double[] capacities = new double[N];
        double[] powerPerFlow = new double[N];

        for (int i = 0; i < N; i++) {
            WaterwheelDevice d = devices.get(i);
            double nominalRPM = d.getNominalSprocketSpeedRpmDouble();
            double rM = d.getSprocketRadiusCmDouble() / 100.0;
            speeds[i] = (2 * Math.PI * rM * nominalRPM) / 60.0;
            depths[i] = 0.12;
            capacities[i] = evaluateFlow(d, speeds[i], depths[i], 0.25, 40.0);
            powerPerFlow[i] = estimatePowerCoeff(d);
        }

        double totalCap = Arrays.stream(capacities).sum() * couplingFactor;
        if (targetFlow > totalCap * 1.2) {
            targetFlow = totalCap * 1.2;
        }

        List<ParallelOptimizationDTO.OptimizationTracePoint> trace = new ArrayList<>();
        double stepSize = 0.05;
        boolean converged = false;
        int iter;

        for (iter = 0; iter < MAX_ITERATIONS; iter++) {
            double[] flows = new double[N];
            double totalFlow = 0;
            double totalPower = 0;
            for (int i = 0; i < N; i++) {
                flows[i] = evaluateFlow(devices.get(i), speeds[i], depths[i], 0.25, 40.0);
                totalFlow += flows[i];
                totalPower += flows[i] * powerPerFlow[i] / 1000.0;
            }

            double coupledTotalFlow = totalFlow * couplingFactor;
            double couplingLoss = totalFlow * (1.0 - couplingFactor);

            double lambdaFlow = coupledTotalFlow > 0 ? targetFlow / coupledTotalFlow : 1.0;
            double objFlow = Math.pow(coupledTotalFlow - targetFlow, 2);
            double objPower = Math.pow(totalPower - maxPower, 2);
            double loadStd = computeStdDev(flows);
            double objective = computeObjective(objFlow, objPower, loadStd, goal);

            ParallelOptimizationDTO.OptimizationTracePoint tp = new ParallelOptimizationDTO.OptimizationTracePoint();
            tp.setIteration(iter + 1);
            tp.setTotalFlow(round(coupledTotalFlow));
            tp.setTotalPower(round(totalPower));
            tp.setObjectiveValue(round(objective));
            trace.add(tp);

            if (iter > 3) {
                double prev = trace.get(iter - 4).getObjectiveValue().doubleValue();
                if (Math.abs(objective - prev) < TOLERANCE) {
                    converged = true;
                    break;
                }
            }

            double flowError = targetFlow - coupledTotalFlow;
            double[] gradients = new double[N];
            for (int i = 0; i < N; i++) {
                double sens = flowSensitivity(devices.get(i), speeds[i], depths[i]);
                double grad = 0;
                if ("MAX_FLOW".equals(goal)) {
                    grad = -couplingFactor * flowError * sens;
                } else if ("MIN_POWER".equals(goal)) {
                    grad = couplingFactor * powerPerFlow[i] * sens / 1000.0;
                } else {
                    double flowPart = -0.5 * flowError * couplingFactor * sens;
                    double balancePart = 0.3 * (flows[i] - (totalFlow / N)) * couplingFactor * sens;
                    double powerPart = 0.2 * powerPerFlow[i] * couplingFactor * sens / 1000.0;
                    grad = flowPart + balancePart + powerPart;
                }
                gradients[i] = grad;
            }

            double gNorm = 0;
            for (double g : gradients) gNorm += g * g;
            gNorm = Math.sqrt(gNorm);
            if (gNorm < 1e-9) break;

            for (int i = 0; i < N; i++) {
                double update = -stepSize * gradients[i] / gNorm * 0.3;
                if (Math.abs(flowError) < 100 && totalPower > maxPower) {
                    speeds[i] *= 0.97;
                } else if (Math.abs(flowError) > 500) {
                    speeds[i] += update * 1.5;
                } else {
                    speeds[i] += update;
                }
                speeds[i] = clamp(speeds[i], MIN_SPEED_MS, MAX_SPEED_MS);

                if (totalPower > maxPower && flows[i] > (totalFlow / N) * 1.1) {
                    depths[i] = clamp(depths[i] - 0.005, 0.05, 0.20);
                } else if (flowError > 200) {
                    depths[i] = clamp(depths[i] + 0.003, 0.05, 0.20);
                }
            }

            if (coupledTotalFlow > 0 && coupledTotalFlow > targetFlow * 1.15) {
                double scale = targetFlow * 1.1 / coupledTotalFlow;
                for (int i = 0; i < N; i++) {
                    speeds[i] = clamp(speeds[i] * Math.sqrt(scale), MIN_SPEED_MS, MAX_SPEED_MS);
                }
            }
            stepSize *= 0.995;
        }

        ParallelOptimizationDTO dto = new ParallelOptimizationDTO();
        dto.setDeviceIds(deviceIds);
        dto.setTargetTotalFlowLh(targetFlow);
        dto.setMaxTotalPowerKW(maxPower);
        dto.setOptimizationGoal(goal);
        dto.setConverged(converged);
        dto.setIterations(iter + 1);

        List<ParallelOptimizationDTO.DeviceAssignment> assignments = new ArrayList<>();
        double[] finalFlows = new double[N];
        double totalPredictedFlow = 0;
        double totalPowerConsumed = 0;
        double totalEffSum = 0;

        for (int i = 0; i < N; i++) {
            WaterwheelDevice d = devices.get(i);
            double flow = evaluateFlow(d, speeds[i], depths[i], 0.25, 40.0);
            double power = flow * powerPerFlow[i] / 1000.0;
            double rM = d.getSprocketRadiusCmDouble() / 100.0;
            double rpm = (speeds[i] * 60.0) / (2 * Math.PI * rM);
            double tension = 2500.0 * (speeds[i] / 1.5) * (1.0 + depths[i] / 0.2);
            double eff = Math.min(0.98, 0.5 + 0.3 * (speeds[i] - MIN_SPEED_MS) / (MAX_SPEED_MS - MIN_SPEED_MS));

            finalFlows[i] = flow;
            totalPredictedFlow += flow;
            totalPowerConsumed += power;
            totalEffSum += eff;

            ParallelOptimizationDTO.DeviceAssignment da = new ParallelOptimizationDTO.DeviceAssignment();
            da.setDeviceId(d.getDeviceId());
            da.setDeviceName(d.getDeviceName());
            da.setAssignedFlowRatio(round(flow * couplingFactor / Math.max(totalPredictedFlow * couplingFactor, 1e-9)));
            da.setAssignedFlowLh(round(flow * couplingFactor));
            da.setOptimalChainSpeedMs(round(speeds[i]));
            da.setOptimalSprocketRPM(round(rpm));
            da.setScraperDepth(round(depths[i]));
            da.setPredictedTensionN(round(tension));
            da.setPowerConsumptionKW(round(power));
            da.setIndividualEfficiency(round(eff));
            da.setStatus(1);
            assignments.add(da);
        }

        double coupledTotalPredictedFlow = totalPredictedFlow * couplingFactor;
        double couplingLoss = totalPredictedFlow - coupledTotalPredictedFlow;

        double loadStd = computeStdDev(finalFlows);
        double meanFlow = coupledTotalPredictedFlow / N;
        double baselineSimple = targetFlow / N;
        double baselineInefficiency = 0;
        for (double f : finalFlows) baselineInefficiency += Math.pow(f - baselineSimple, 2);
        double coordinationGain = (baselineInefficiency - loadStd * loadStd * N) / Math.max(baselineInefficiency, 1e-9);

        dto.setAssignments(assignments);
        dto.setTotalPredictedFlowLh(round(coupledTotalPredictedFlow));
        dto.setTotalPowerConsumptionKW(round(totalPowerConsumed));
        dto.setAverageEfficiency(round(totalEffSum / N));
        dto.setLoadBalanceStdDev(round(meanFlow > 0 ? loadStd / meanFlow : 0));
        dto.setCoordinationGain(round(coordinationGain * 100));
        dto.setTrace(trace);
        dto.setComputationTimeMs(System.currentTimeMillis() - startTime);

        Map<String, Object> couplingInfo = new LinkedHashMap<>();
        couplingInfo.put("couplingFactor", round(couplingFactor));
        couplingInfo.put("couplingKappa", HYDRAULIC_COUPLING_KAPPA);
        couplingInfo.put("deviceCount", N);
        couplingInfo.put("couplingLossLh", round(couplingLoss));
        couplingInfo.put("uncoupledTotalFlowLh", round(totalPredictedFlow));
        couplingInfo.put("description", "κ=" + HYDRAULIC_COUPLING_KAPPA + " 水力耦合系数：背压+水位干扰+湍流损失");
        dto.setCouplingInfo(couplingInfo);

        return dto;
    }

    double computeCouplingFactor(int N) {
        if (N <= 1) return 1.0;
        return 1.0 - HYDRAULIC_COUPLING_KAPPA * (N - 1.0) / N;
    }

    private double computeObjective(double objFlow, double objPower, double loadStd, String goal) {
        switch (goal) {
            case "MAX_FLOW":
                return objFlow * 1.0 + objPower * 0.3 + loadStd * 0.2;
            case "MIN_POWER":
                return objFlow * 0.8 + objPower * 1.0 + loadStd * 0.2;
            case "BALANCED":
            default:
                return objFlow * 0.5 + objPower * 0.3 + loadStd * 0.4;
        }
    }

    private double evaluateFlow(WaterwheelDevice d, double speedMs, double depth, double width, double angle) {
        int scraperCount = d.getScraperCountInt();
        double sprocketRadius = d.getSprocketRadiusCmDouble() / 100.0;
        return efficiencyOptimizer.calculateWaterFlow(depth, width, angle, speedMs, scraperCount, sprocketRadius);
    }

    private double flowSensitivity(WaterwheelDevice d, double speedMs, double depth) {
        double h = 1e-4;
        double f1 = evaluateFlow(d, speedMs + h, depth, 0.25, 40.0);
        double f2 = evaluateFlow(d, speedMs - h, depth, 0.25, 40.0);
        return (f1 - f2) / (2 * h);
    }

    private double estimatePowerCoeff(WaterwheelDevice d) {
        return 0.15 + 0.05 * (d.getChainLengthCmDouble() / 500.0);
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double computeStdDev(double[] arr) {
        if (arr.length == 0) return 0;
        double mean = Arrays.stream(arr).sum() / arr.length;
        double sum = 0;
        for (double x : arr) sum += (x - mean) * (x - mean);
        return Math.sqrt(sum / arr.length);
    }

    private BigDecimal round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
