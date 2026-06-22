package com.waterwheel.chaintransmission;

import com.waterwheel.chaintransmission.comparison.service.ChainTypeComparisonService;
import com.waterwheel.chaintransmission.dto.ChainTypeComparisonDTO;
import com.waterwheel.chaintransmission.entity.ChainType;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import com.waterwheel.chaintransmission.test.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChainTypeComparator 模块独立测试")
class ChainTypeComparatorModuleTest {

    @Mock
    private WaterwheelDeviceRepository deviceRepository;

    @Mock
    private WaterEfficiencyOptimizer optimizer;

    @InjectMocks
    private ChainTypeComparisonService comparisonService;

    @Test
    @DisplayName("testModuleCanBeInstantiated - 模块API独立性验证：创建mock测试ChainTypeComparisonService在单独使用Mockito完整流程")
    void testModuleCanBeInstantiated() {
        WaterwheelDevice device = TestDataFactory.createDefaultDevice();
        when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(device));
        when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    double speed = invocation.getArgument(3);
                    return 5000.0 * speed * 3.6;
                });

        ChainTypeComparisonDTO result = comparisonService.compareChainTypes(1, 15.0, 80.0, 0.12, 0.25, 40.0);

        assertNotNull(result, "compareChainTypes 应返回非空结果");
        assertEquals(1, result.getDeviceId());
        assertEquals(3, result.getResults().size(), "应返回3种链型结果");

        verify(deviceRepository, times(1)).findById(1);
        verify(optimizer, times(3)).calculateWaterFlow(
                eq(0.12), eq(0.25), eq(40.0), anyDouble());

        var metaList = comparisonService.getAllChainTypeMeta();
        assertNotNull(metaList, "getAllChainTypeMeta 应返回非空列表");
        assertEquals(3, metaList.size());
    }

    @Test
    @DisplayName("testChainTypeEnumValues - 枚举值完整性验证：ChainType.values().length==3且包含PLATE_CHAIN/ROUND_LINK_CHAIN/HOOK_CHAIN")
    void testChainTypeEnumValues() {
        ChainType[] values = ChainType.values();
        assertEquals(3, values.length, "ChainType 应有3个枚举值");

        boolean hasPlate = false;
        boolean hasRound = false;
        boolean hasHook = false;
        for (ChainType ct : values) {
            if (ct == ChainType.PLATE_CHAIN) hasPlate = true;
            if (ct == ChainType.ROUND_LINK_CHAIN) hasRound = true;
            if (ct == ChainType.HOOK_CHAIN) hasHook = true;
        }
        assertTrue(hasPlate, "应包含 PLATE_CHAIN");
        assertTrue(hasRound, "应包含 ROUND_LINK_CHAIN");
        assertTrue(hasHook, "应包含 HOOK_CHAIN");

        assertNotNull(ChainType.valueOf("PLATE_CHAIN"));
        assertNotNull(ChainType.valueOf("ROUND_LINK_CHAIN"));
        assertNotNull(ChainType.valueOf("HOOK_CHAIN"));
    }

    @Test
    @DisplayName("testMeasurementSourceExistence - 所有ChainType均有measurementSource且不为空")
    void testMeasurementSourceExistence() {
        for (ChainType ct : ChainType.values()) {
            assertNotNull(ct.getMeasurementSource(),
                    ct.name() + " 的 measurementSource 不应为 null");
            assertFalse(ct.getMeasurementSource().trim().isEmpty(),
                    ct.name() + " 的 measurementSource 不应为空字符串");
            assertTrue(ct.getMeasurementSource().length() > 10,
                    ct.name() + " 的 measurementSource 应有足够长度");
        }

        assertTrue(ChainType.PLATE_CHAIN.getMeasurementSource().contains("《农书》")
                        || ChainType.PLATE_CHAIN.getMeasurementSource().contains("《天工开物》"),
                "PLATE_CHAIN 应引用古代文献");
        assertTrue(ChainType.ROUND_LINK_CHAIN.getMeasurementSource().contains("《王祯农书》")
                        || ChainType.ROUND_LINK_CHAIN.getMeasurementSource().contains("博物馆"),
                "ROUND_LINK_CHAIN 应有权威来源");
    }

    @Test
    @DisplayName("testMaxSpeedUnitsMatch - maxSpeedRPM和maxSpeedMs物理一致性：2πR·RPM/60 ≈ maxSpeedMs（误差20%以内）")
    void testMaxSpeedUnitsMatch() {
        double typicalSprocketRadiusM = 0.30;

        for (ChainType ct : ChainType.values()) {
            double rpm = ct.getMaxSpeedRPM();
            double msFromRpm = (2 * Math.PI * typicalSprocketRadiusM * rpm) / 60.0;
            double actualMs = ct.getMaxSpeedMs();

            double errorPercent = Math.abs(msFromRpm - actualMs) / actualMs * 100.0;

            assertTrue(errorPercent < 20.0,
                    ct.name() + " 速度单位不匹配: RPM换算=" + String.format("%.3f", msFromRpm)
                            + "m/s, 实际maxSpeedMs=" + actualMs
                            + "m/s, 误差=" + String.format("%.1f", errorPercent) + "%");
        }
    }

    @Test
    @DisplayName("testDeprecatedGetMaxSpeedBackwardCompatibility - 弃用API返回RPM兼容性")
    void testDeprecatedGetMaxSpeedBackwardCompatibility() {
        for (ChainType ct : ChainType.values()) {
            double deprecatedSpeed = ct.getMaxSpeed();
            double rpmSpeed = ct.getMaxSpeedRPM();

            assertEquals(rpmSpeed, deprecatedSpeed, TestDataFactory.TOLERANCE,
                    ct.name() + " 弃用的 getMaxSpeed() 应等于 getMaxSpeedRPM()");
        }

        assertEquals(ChainType.PLATE_CHAIN.getMaxSpeedRPM(), ChainType.PLATE_CHAIN.getMaxSpeed());
        assertEquals(ChainType.ROUND_LINK_CHAIN.getMaxSpeedRPM(), ChainType.ROUND_LINK_CHAIN.getMaxSpeed());
        assertEquals(ChainType.HOOK_CHAIN.getMaxSpeedRPM(), ChainType.HOOK_CHAIN.getMaxSpeed());
    }

    @Test
    @DisplayName("testFromCodeInvalidReturnsDefault - fromCode(null)和fromCode(无效) 回退到PLATE_CHAIN")
    void testFromCodeInvalidReturnsDefault() {
        ChainType nullResult = ChainType.fromCode(null);
        assertEquals(ChainType.PLATE_CHAIN, nullResult,
                "fromCode(null) 应回退到 PLATE_CHAIN");

        ChainType emptyResult = ChainType.fromCode("");
        assertEquals(ChainType.PLATE_CHAIN, emptyResult,
                "fromCode(\"\") 应回退到 PLATE_CHAIN");

        ChainType invalidResult = ChainType.fromCode("INVALID_CODE_12345");
        assertEquals(ChainType.PLATE_CHAIN, invalidResult,
                "fromCode(无效代码) 应回退到 PLATE_CHAIN");

        ChainType garbageResult = ChainType.fromCode("@#$%^&*()");
        assertEquals(ChainType.PLATE_CHAIN, garbageResult,
                "fromCode(乱码) 应回退到 PLATE_CHAIN");

        ChainType plateResult = ChainType.fromCode("plate");
        assertEquals(ChainType.PLATE_CHAIN, plateResult, "fromCode(plate) 应返回 PLATE_CHAIN");

        ChainType roundResult = ChainType.fromCode("ROUND");
        assertEquals(ChainType.ROUND_LINK_CHAIN, roundResult, "fromCode(ROUND) 大小写不敏感");

        ChainType hookResult = ChainType.fromCode("Hook");
        assertEquals(ChainType.HOOK_CHAIN, hookResult, "fromCode(Hook) 大小写不敏感");
    }
}
