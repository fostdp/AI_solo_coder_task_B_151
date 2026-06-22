package com.waterwheel.chaintransmission.test;

import com.waterwheel.chaintransmission.entity.ChainType;
import com.waterwheel.chaintransmission.entity.EraType;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TestDataFactory {

    public static WaterwheelDevice createDefaultDevice() {
        return createDevice(1, "宋代测试翻车", "plate", "ancient_song",
                30.0, 500.0, 12.5, 120, 24);
    }

    public static WaterwheelDevice createDevice(Integer deviceId, String name,
                                                String chainTypeCode, String eraCode,
                                                double sprocketRadiusCm, double chainLengthCm,
                                                double nominalSpeedRpm, int numLinks, int scraperCount) {
        WaterwheelDevice d = new WaterwheelDevice();
        d.setDeviceId(deviceId);
        d.setDeviceName(name);
        d.setLocation("测试地点");
        d.setLocationCode("TEST-" + deviceId);
        d.setSprocketRadiusCm(BigDecimal.valueOf(sprocketRadiusCm));
        d.setChainLengthCm(BigDecimal.valueOf(chainLengthCm));
        d.setNominalSprocketSpeedRpm(BigDecimal.valueOf(nominalSpeedRpm));
        d.setNumLinks(numLinks);
        d.setScraperCount(scraperCount);
        d.setScraperShape("弧形刮板");
        d.setChainTypeCode(chainTypeCode);
        d.setEraCode(eraCode);
        d.setParallelGroupId(1);
        d.setInstallationDate(LocalDateTime.now());
        d.setStatus("ACTIVE");
        return d;
    }

    public static WaterwheelDevice createModernDevice() {
        return createDevice(4, "现代测试链式泵", "round", "modern_electric",
                35.0, 650.0, 22.0, 120, 24);
    }

    public static double[] getChainTypeExpectedEfficiencies() {
        return new double[]{
                ChainType.ROUND_LINK_CHAIN.getTransmissionEfficiency(),
                ChainType.PLATE_CHAIN.getTransmissionEfficiency(),
                ChainType.HOOK_CHAIN.getTransmissionEfficiency()
        };
    }

    public static double getEraEfficiencyImprovementRatio() {
        double ancientEff = EraType.ANCIENT_SONG.getTotalEfficiency();
        double modernEff = EraType.MODERN_ELECTRIC.getTotalEfficiency();
        return modernEff / ancientEff;
    }

    public static final double TOLERANCE = 0.0001;
    public static final double FLOW_TOLERANCE = 0.5;
}
