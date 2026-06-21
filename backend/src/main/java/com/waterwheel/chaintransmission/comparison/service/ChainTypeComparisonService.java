package com.waterwheel.chaintransmission.comparison.service;

import com.waterwheel.chaintransmission.dto.ChainTypeComparisonDTO;
import com.waterwheel.chaintransmission.entity.ChainType;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ChainTypeComparisonService {

    @Autowired
    private WaterwheelDeviceRepository deviceRepository;

    @Autowired
    private WaterEfficiencyOptimizer optimizer;

    public ChainTypeComparisonDTO compareChainTypes(Integer deviceId,
                                                     Double inputSpeedRPM,
                                                     Double inputTorque,
                                                     Double scraperDepth,
                                                     Double scraperWidth,
                                                     Double scraperAngle) {
        WaterwheelDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));

        double speed = inputSpeedRPM != null ? inputSpeedRPM : device.getNominalSprocketSpeedRpmDouble();
        double torque = inputTorque != null ? inputTorque : 80.0;
        double depth = scraperDepth != null ? scraperDepth : 0.12;
        double width = scraperWidth != null ? scraperWidth : 0.25;
        double angle = scraperAngle != null ? scraperAngle : 40.0;

        ChainTypeComparisonDTO dto = new ChainTypeComparisonDTO();
        dto.setDeviceId(deviceId);
        dto.setDeviceName(device.getDeviceName());
        dto.setInputSpeedRPM(speed);
        dto.setInputTorque(torque);
        dto.setScraperDepth(depth);
        dto.setScraperWidth(width);
        dto.setScraperAngle(angle);

        List<ChainTypeComparisonDTO.ChainTypeResult> results = new ArrayList<>();
        for (ChainType ct : ChainType.values()) {
            results.add(evaluateChainType(ct, device, speed, torque, depth, width, angle));
        }
        results.sort((a, b) -> b.getActualWaterFlowLh().compareTo(a.getActualWaterFlowLh()));
        dto.setResults(results);

        return dto;
    }

    private ChainTypeComparisonDTO.ChainTypeResult evaluateChainType(ChainType ct,
                                                                      WaterwheelDevice device,
                                                                      double speedRPM,
                                                                      double torque,
                                                                      double depth,
                                                                      double width,
                                                                      double angle) {
        ChainTypeComparisonDTO.ChainTypeResult result = new ChainTypeComparisonDTO.ChainTypeResult();
        result.setChainTypeCode(ct.getCode());
        result.setChainTypeName(ct.getDisplayName());
        result.setDescription(ct.getDescription());
        result.setTransmissionEfficiency(round(ct.getTransmissionEfficiency()));
        result.setMaxAllowableSpeed(round(ct.getMaxSpeed()));

        double sprocketRadiusM = device.getSprocketRadiusCmDouble() / 100.0;
        double chainSpeedMs = (2 * Math.PI * sprocketRadiusM * speedRPM) / 60.0;

        double baseFlow = optimizer.calculateWaterFlow(depth, width, angle, chainSpeedMs);
        double adjustedFlow = baseFlow
                * ct.getTransmissionEfficiency()
                * ct.getWaterRetentionRate();
        result.setActualWaterFlowLh(round(adjustedFlow));

        double angularVelocity = speedRPM * Math.PI / 30.0;
        double powerInputW = torque * angularVelocity;
        double effectivePowerW = powerInputW * ct.getTransmissionEfficiency();
        result.setPowerConsumptionW(round(powerInputW));

        double frictionLossW = powerInputW * ct.getFrictionCoefficient() * 0.3;
        result.setFrictionLossW(round(frictionLossW));

        double nominalTension = (torque / sprocketRadiusM) * ct.getTensionCoefficient();
        result.setChainTensionN(round(nominalTension));

        double speedRatio = chainSpeedMs / ct.getMaxSpeed();
        double wearBase = ct.getWearCoefficient() * Math.pow(speedRatio, 1.5) * 1e-6;
        result.setWearRate(round(wearBase * 1000000));

        double allowableWear = 0.03;
        double lifespanHours = (wearBase > 0) ? (allowableWear / wearBase) : 50000;
        lifespanHours = Math.min(lifespanHours, 80000);
        result.setExpectedLifespanHours(round(lifespanHours));

        double tensionVariation = nominalTension * 0.12 * (1 + ct.getFrictionCoefficient());
        double cv = (tensionVariation / nominalTension);
        result.setResonanceRisk(cv > 0.45);

        double chainLengthM = device.getChainLengthCmDouble() / 100.0;
        double linearMassDensity = 2.5;
        double avgTension = nominalTension * 0.85;
        List<BigDecimal> frequencies = new ArrayList<>();
        for (int n = 1; n <= 4; n++) {
            double f = (n / (2 * chainLengthM)) * Math.sqrt(avgTension / linearMassDensity);
            frequencies.add(round(f));
        }
        result.setVibrationFrequencies(frequencies);

        Map<String, Object> detailed = new LinkedHashMap<>();
        detailed.put("chainSpeed_m_s", round(chainSpeedMs));
        detailed.put("angularVelocity_rad_s", round(angularVelocity));
        detailed.put("effectivePower_W", round(effectivePowerW));
        detailed.put("tensionVariation_N", round(tensionVariation));
        detailed.put("coefficientOfVariation", round(cv));
        detailed.put("powerToFlowRatio_W_per_Lh",
                round(adjustedFlow > 0 ? (powerInputW / adjustedFlow) : 0));
        detailed.put("specificEnergy_Wh_per_m3",
                round(adjustedFlow > 0 ? ((powerInputW / adjustedFlow) * 1000) : 0));
        result.setDetailedMetrics(detailed);

        return result;
    }

    private BigDecimal round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    public List<Map<String, Object>> getAllChainTypeMeta() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChainType ct : ChainType.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", ct.getCode());
            m.put("name", ct.getDisplayName());
            m.put("description", ct.getDescription());
            m.put("transmissionEfficiency", ct.getTransmissionEfficiency());
            m.put("frictionCoefficient", ct.getFrictionCoefficient());
            m.put("tensionCoefficient", ct.getTensionCoefficient());
            m.put("waterRetentionRate", ct.getWaterRetentionRate());
            m.put("wearCoefficient", ct.getWearCoefficient());
            m.put("maxSpeed_rpm", ct.getMaxSpeed());
            list.add(m);
        }
        return list;
    }
}
