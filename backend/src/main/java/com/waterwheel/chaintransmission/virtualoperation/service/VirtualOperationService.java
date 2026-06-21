package com.waterwheel.chaintransmission.virtualoperation.service;

import com.waterwheel.chaintransmission.dto.VirtualOperationDTO;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VirtualOperationService {

    @Autowired
    private WaterwheelDeviceRepository deviceRepository;

    @Autowired
    private WaterEfficiencyOptimizer efficiencyOptimizer;

    private final Map<Integer, OperationSession> sessions = new ConcurrentHashMap<>();

    private static final double MIN_SPEED = 0.3;
    private static final double MAX_SPEED = 4.0;
    private static final int TENSION_SAMPLES = 50;

    public VirtualOperationDTO performOperation(Integer deviceId,
                                                 Double chainSpeedMs,
                                                 Double waterLevelFactor,
                                                 Integer operationSeconds,
                                                 Boolean resetSession) {
        WaterwheelDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));

        double speed = chainSpeedMs != null ? chainSpeedMs : 1.5;
        double wlFactor = waterLevelFactor != null ? waterLevelFactor : 1.0;
        int seconds = operationSeconds != null ? operationSeconds : 0;
        boolean reset = resetSession != null && resetSession;

        speed = clamp(speed, MIN_SPEED, MAX_SPEED);
        wlFactor = clamp(wlFactor, 0.3, 1.3);

        OperationSession session;
        if (reset || !sessions.containsKey(deviceId)) {
            session = new OperationSession();
            sessions.put(deviceId, session);
        } else {
            session = sessions.get(deviceId);
        }
        session.accumulatedSeconds += seconds;

        double sprocketRadiusM = device.getSprocketRadiusCmDouble() / 100.0;
        double rpm = (speed * 60.0) / (2 * Math.PI * sprocketRadiusM);

        double depth = 0.12 * wlFactor;
        double width = 0.25;
        double angle = 40.0;

        double flowLh = efficiencyOptimizer.calculateWaterFlow(depth, width, angle, speed);
        double instantaneousLs = flowLh / 3600.0;

        double nominalFlowLh = 25000.0;
        double nominalTension = 3500.0;
        double tension = nominalTension
                * (speed / 1.75)
                * (1.0 + (depth / 0.15 - 1.0) * 0.3);
        tension += gaussian(0, tension * 0.05);

        double scraperLoad = 400.0
                * wlFactor
                * (1.0 + (speed / 1.75 - 1.0) * 0.4)
                + gaussian(0, 15);

        double angularVelocity = speed / sprocketRadiusM;
        double torque = (tension * sprocketRadiusM * 0.6) + 15.0;
        double power = torque * angularVelocity;

        double waterEnergy = (instantaneousLs * 9.81 * 3.0);
        double efficiency = power > 0 ? (waterEnergy / power) * 100.0 : 0;
        efficiency = clamp(efficiency, 0, 95);

        double vibrationBase = 0.3 + (speed / MAX_SPEED) * 1.5;
        double amplitude = vibrationBase
                * (1.0 + (Math.abs(speed - 2.1) < 0.3 ? 0.8 : 0))
                * (1.0 + (tension / 6000 - 1.0) * 0.3);

        List<BigDecimal> tensionDist = new ArrayList<>();
        double maxTension = tension * 1.25;
        for (int i = 0; i < TENSION_SAMPLES; i++) {
            double t = (double) i / (TENSION_SAMPLES - 1);
            double shape = 1.0 + 0.4 * Math.sin(t * Math.PI * 3)
                    + 0.25 * Math.exp(-Math.pow((t - 0.3), 2) * 30)
                    - 0.15 * Math.exp(-Math.pow((t - 0.85), 2) * 20);
            double ti = tension * shape * (1 + gaussian(0, 0.02));
            tensionDist.add(round(ti));
        }

        List<Float> linkPositions = computeChainLinkPositionsFast(device, speed, session);

        List<String> warnings = new ArrayList<>();
        String status = "NORMAL";
        if (tension > 8000) {
            warnings.add("链条张力超过安全阈值 8000N，建议降低转速");
            status = "CRITICAL";
        } else if (tension > 6000) {
            warnings.add("链条张力较高，建议降低转速或水深");
            status = "WARNING";
        }
        if (amplitude > 2.5) {
            warnings.add("检测到异常振动，疑似接近共振区间，调整链速");
            if ("NORMAL".equals(status)) status = "WARNING";
        }
        if (speed < 0.5 && wlFactor > 0.6) {
            warnings.add("转速过低，刮板填充效率下降");
        }
        if (speed > 3.0) {
            warnings.add("高速运行，离心损失和磨损显著增加");
        }

        VirtualOperationDTO dto = new VirtualOperationDTO();
        dto.setDeviceId(deviceId);
        dto.setChainSpeedMs(speed);
        dto.setWaterLevelFactor(wlFactor);
        dto.setManualMode(true);
        dto.setCurrentWaterFlowLh(round(flowLh));
        dto.setInstantaneousFlowLs(round(instantaneousLs));
        dto.setChainTensionN(round(tension));
        dto.setSprocketRPM(round(rpm));
        dto.setDriveTorqueNm(round(torque));
        dto.setPowerConsumptionW(round(power));
        dto.setScraperLoadN(round(scraperLoad));
        dto.setVibrationAmplitudeMm(round(amplitude));
        dto.setEfficiencyPercent(round(efficiency));

        double totalLiters = instantaneousLs * session.accumulatedSeconds;
        dto.setTotalWaterLiters(round(totalLiters));
        dto.setOperationSeconds(session.accumulatedSeconds);

        dto.setTensionDistribution(tensionDist);
        dto.setChainLinkPositions(linkPositions);
        dto.setOperationStatus(status);
        dto.setWarnings(warnings);
        return dto;
    }

    private List<Float> computeChainLinkPositionsFast(WaterwheelDevice d, double speed, OperationSession session) {
        int linkCount = 120;
        double radius = d.getSprocketRadiusCmDouble() / 100.0;
        double centerDist = d.getChainLengthCmDouble() / 100.0 / 2.0;
        double circumference = 2 * Math.PI * radius;
        double totalLen = circumference + 2 * centerDist;
        double linkLen = totalLen / linkCount;

        session.phase += (speed * 0.033) / linkLen;
        session.phase = session.phase % 1.0;

        List<Float> positions = new ArrayList<>();
        for (int i = 0; i < Math.min(50, linkCount); i++) {
            double progress = ((double) i / linkCount + session.phase) % 1.0;
            double[] xyz = pathPosition(progress, radius, centerDist);
            positions.add((float) xyz[0]);
            positions.add((float) xyz[1]);
            positions.add((float) xyz[2]);
        }
        return positions;
    }

    private double[] pathPosition(double progress, double R, double dist) {
        double halfUpper = dist / 2.0;
        double seg1 = halfUpper;
        double seg2 = Math.PI * R;
        double seg3 = halfUpper;

        if (progress < 0) progress += 1.0;
        double[] xyz;
        if (progress < 0.25) {
            double t = progress / 0.25;
            double x = -halfUpper + t * dist;
            xyz = new double[]{x, R, 0};
        } else if (progress < 0.5) {
            double t = (progress - 0.25) / 0.25;
            double theta = Math.PI / 2 - t * Math.PI;
            double cx = halfUpper;
            double x = cx + R * Math.cos(theta);
            double y = R * Math.sin(theta);
            xyz = new double[]{x, y, 0};
        } else if (progress < 0.75) {
            double t = (progress - 0.5) / 0.25;
            double x = halfUpper - t * dist;
            xyz = new double[]{x, -R, 0};
        } else {
            double t = (progress - 0.75) / 0.25;
            double theta = -Math.PI / 2 + t * Math.PI;
            double cx = -halfUpper;
            double x = cx + R * Math.cos(theta);
            double y = R * Math.sin(theta);
            xyz = new double[]{x, y, 0};
        }
        return xyz;
    }

    public Map<String, Object> resetAllSessions() {
        int count = sessions.size();
        sessions.clear();
        Map<String, Object> r = new HashMap<>();
        r.put("cleared", count);
        return r;
    }

    private double gaussian(double mean, double std) {
        Random rnd = ThreadLocalRandom.current();
        return mean + std * rnd.nextGaussian();
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private BigDecimal round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static class OperationSession {
        int accumulatedSeconds = 0;
        double phase = 0.0;
    }
}
