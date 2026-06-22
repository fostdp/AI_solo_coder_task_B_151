package com.waterwheel.chaintransmission.comparison.service;

import com.waterwheel.chaintransmission.dto.EraComparisonDTO;
import com.waterwheel.chaintransmission.entity.EraType;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class EraComparisonService {

    @Autowired
    private WaterwheelDeviceRepository deviceRepository;

    @Autowired
    private WaterEfficiencyOptimizer optimizer;

    public EraComparisonDTO compareEras(Integer deviceId,
                                         Double chainSpeedRatio,
                                         Double scraperSizeScale) {
        WaterwheelDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));

        double speedRatio = chainSpeedRatio != null ? chainSpeedRatio : 1.0;
        double sizeScale = scraperSizeScale != null ? scraperSizeScale : 1.0;

        EraComparisonDTO dto = new EraComparisonDTO();
        dto.setDeviceId(deviceId);
        dto.setReferenceDeviceName(device.getDeviceName());
        dto.setChainSpeedRatio(speedRatio);
        dto.setScraperSizeScale(sizeScale);

        List<EraComparisonDTO.EraResult> results = new ArrayList<>();
        for (EraType era : EraType.values()) {
            results.add(evaluateEra(era, device, speedRatio, sizeScale));
        }
        dto.setResults(results);

        dto.setKeyMetrics(computeKeyMetrics(results));
        return dto;
    }

    private EraComparisonDTO.EraResult evaluateEra(EraType era,
                                                   WaterwheelDevice device,
                                                   double speedRatio,
                                                   double sizeScale) {
        EraComparisonDTO.EraResult r = new EraComparisonDTO.EraResult();
        r.setEraCode(era.getCode());
        r.setEraName(era.getDisplayName());
        r.setDescription(era.getDescription());
        r.setFrameMaterial(era.getFrameMaterial());
        r.setChainMaterial(era.getChainMaterial());
        r.setDriveType(era.getDriveType());
        r.setPowerSource(era.getPowerSource());

        r.setMechanicalEfficiency(round(era.getMechanicalEfficiency()));
        r.setTransmissionEfficiency(round(era.getTransmissionEfficiency()));
        r.setControlEfficiency(round(era.getControlEfficiency()));
        r.setTotalEfficiency(round(era.getTotalEfficiency()));
        r.setTypicalSpeedRPM(round(era.getTypicalSpeedRPM()));
        r.setTypicalPowerKW(round(era.getTypicalPowerKW()));

        double nominalRPM = device.getNominalSprocketSpeedRpmDouble();
        double eraSpeedRPM;
        if (era == EraType.MODERN_ELECTRIC) {
            eraSpeedRPM = nominalRPM * speedRatio * 1.8;
        } else {
            eraSpeedRPM = nominalRPM * speedRatio * 0.85;
        }
        double sprocketRadiusM = device.getSprocketRadiusCmDouble() / 100.0;
        double chainSpeedMs = (2 * Math.PI * sprocketRadiusM * eraSpeedRPM) / 60.0;

        double baseDepth = 0.12 * sizeScale;
        double baseWidth = 0.25 * sizeScale;
        double angle = 40.0;
        if (era == EraType.ANCIENT_SONG) {
            baseDepth = Math.min(baseDepth, 0.15);
            baseWidth = Math.min(baseWidth, 0.30);
            angle = 35.0;
        }
        double baseFlow = optimizer.calculateWaterFlow(baseDepth, baseWidth, angle, chainSpeedMs,
                device.getScraperCountInt(), device.getSprocketRadiusCmDouble() / 100.0);
        double adjustedFlow = baseFlow * era.getTotalEfficiency();
        r.setWaterFlowLh(round(adjustedFlow));

        double eraPowerKW;
        if (era == EraType.MODERN_ELECTRIC) {
            eraPowerKW = era.getTypicalPowerKW() * (eraSpeedRPM / 22.0);
        } else {
            double waterPowerKW = (adjustedFlow / 3600000.0) * 9.81 * 3.0;
            eraPowerKW = waterPowerKW / Math.max(era.getTotalEfficiency(), 0.01);
        }
        r.setPowerInputKW(round(eraPowerKW));

        double waterPowerKW = (adjustedFlow / 3600000.0) * 9.81 * 3.0;
        r.setPowerOutputKW(round(waterPowerKW));

        double flowPerHourM3 = adjustedFlow / 1000.0;
        double energyPerM3KWh = flowPerHourM3 > 0 ? (eraPowerKW / flowPerHourM3) : 0;
        double electricityRate = 0.65;
        double waterCost = era == EraType.ANCIENT_SONG ? 0.0 : energyPerM3KWh * electricityRate;
        r.setEnergyCostPerCubicYuan(round(waterCost));

        double baseTension = 3500.0;
        double tensionFactor = era == EraType.ANCIENT_SONG ? 1.2 : 0.7;
        double tension = baseTension * (eraSpeedRPM / nominalRPM) * tensionFactor;
        r.setChainTensionN(round(tension));

        if (era == EraType.ANCIENT_SONG) {
            r.setNoiseLevelDB(round(68.0));
            r.setMaintenanceHoursPerYear(round(120.0));
            r.setLifespanYears(round(8.0));
            r.setCostFactor(round(1.0));
        } else {
            r.setNoiseLevelDB(round(70.0));
            r.setMaintenanceHoursPerYear(round(20.0));
            r.setLifespanYears(round(20.0));
            r.setCostFactor(round(3.5));
        }

        Map<String, Object> ctx = new LinkedHashMap<>();
        if (era == EraType.ANCIENT_SONG) {
            ctx.put("dynasty", "北宋");
            ctx.put("inventor", "王祯（记载于《农书》）");
            ctx.put("yearRange", "公元960-1279");
            ctx.put("typicalLocation", "江南水乡、四川盆地");
            ctx.put("dailyWorkHours", 8);
            ctx.put("waterSource", "自然河流水力");
            ctx.put("constructionCostRatio", 1.0);
            ctx.put("laborIntensity", "高（需2-3人值守）");
            ctx.put("typicalIrrigatedArea_mu", 50);
            ctx.put("measurementSource", era.getStandardCompliance());
        } else {
            ctx.put("era", "21世纪工业");
            ctx.put("technology", "变频电机+合金钢+PLC自动控制");
            ctx.put("dailyWorkHours", 24);
            ctx.put("waterSource", "市政/工业供水系统");
            ctx.put("constructionCostRatio", 3.5);
            ctx.put("laborIntensity", "低（无人值守）");
            ctx.put("typicalIrrigatedArea_mu", 500);
            ctx.put("controlSystem", "PLC+HMI+远程监控");
            ctx.put("standardCompliance", era.getStandardCompliance());
            ctx.put("efficiencyStandard", "GB/T 3216-2016回转动力泵水力性能验收试验");
            ctx.put("energyEfficiencyStandard", "GB 19761-2020通风机系统节能改造及GB 18613-2020电动机能效限定值");
        }
        r.setHistoricalContext(ctx);

        return r;
    }

    private List<EraComparisonDTO.ComparisonMetric> computeKeyMetrics(List<EraComparisonDTO.EraResult> results) {
        if (results.size() < 2) return Collections.emptyList();

        EraComparisonDTO.EraResult ancient = results.get(0);
        EraComparisonDTO.EraResult modern = results.get(1);

        List<EraComparisonDTO.ComparisonMetric> metrics = new ArrayList<>();
        addMetric(metrics, "提水量", "L/h",
                ancient.getWaterFlowLh(), modern.getWaterFlowLh(),
                "现代链式泵提水能力提升 %.1f 倍");
        addMetric(metrics, "总效率", "%",
                ancient.getTotalEfficiency().multiply(BigDecimal.valueOf(100)),
                modern.getTotalEfficiency().multiply(BigDecimal.valueOf(100)),
                "能量利用效率提升 %.1f 个百分点");
        addMetric(metrics, "能量成本", "元/m³",
                ancient.getEnergyCostPerCubicYuan(), modern.getEnergyCostPerCubicYuan(),
                "每方水运行成本变化：古代%.3f 元 vs 现代%.3f 元");
        addMetric(metrics, "典型转速", "RPM",
                ancient.getTypicalSpeedRPM(), modern.getTypicalSpeedRPM(),
                "机械运转速度提升 %.1f 倍");
        addMetric(metrics, "年维护工时", "小时",
                ancient.getMaintenanceHoursPerYear(), modern.getMaintenanceHoursPerYear(),
                "维护工作量减少 %.1f%%");
        addMetric(metrics, "设备寿命", "年",
                ancient.getLifespanYears(), modern.getLifespanYears(),
                "使用寿命延长 %.1f 年");
        return metrics;
    }

    private void addMetric(List<EraComparisonDTO.ComparisonMetric> list,
                           String name, String unit,
                           BigDecimal a, BigDecimal b, String descTpl) {
        EraComparisonDTO.ComparisonMetric m = new EraComparisonDTO.ComparisonMetric();
        m.setMetricName(name);
        m.setUnit(unit);
        m.setAncientValue(a);
        m.setModernValue(b);
        if (a.compareTo(BigDecimal.ZERO) > 0) {
            m.setImprovementRatio(b.divide(a, 4, RoundingMode.HALF_UP));
        }
        m.setImprovementDescription(String.format(descTpl,
                b.doubleValue() / Math.max(a.doubleValue(), 1e-9),
                a.doubleValue(), b.doubleValue()));
        list.add(m);
    }

    private BigDecimal round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    public List<Map<String, Object>> getAllEraMeta() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (EraType e : EraType.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", e.getCode());
            m.put("name", e.getDisplayName());
            m.put("description", e.getDescription());
            m.put("frameMaterial", e.getFrameMaterial());
            m.put("chainMaterial", e.getChainMaterial());
            m.put("driveType", e.getDriveType());
            m.put("powerSource", e.getPowerSource());
            m.put("mechanicalEfficiency", e.getMechanicalEfficiency());
            m.put("transmissionEfficiency", e.getTransmissionEfficiency());
            m.put("controlEfficiency", e.getControlEfficiency());
            m.put("totalEfficiency", e.getTotalEfficiency());
            m.put("typicalSpeedRPM", e.getTypicalSpeedRPM());
            m.put("typicalPowerKW", e.getTypicalPowerKW());
            m.put("standardCompliance", e.getStandardCompliance());
            list.add(m);
        }
        return list;
    }
}
