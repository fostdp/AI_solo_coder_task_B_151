package com.waterwheel.chaintransmission;

import com.waterwheel.chaintransmission.dto.ParallelOptimizationDTO;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.optimization.ParallelCoordinationOptimizer;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import com.waterwheel.chaintransmission.test.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParallelOptimizer 模块独立测试")
class ParallelOptimizerModuleTest {

    @Mock
    private WaterwheelDeviceRepository deviceRepository;

    @Mock
    private WaterEfficiencyOptimizer optimizer;

    @InjectMocks
    private ParallelCoordinationOptimizer parallelOptimizer;

    private WaterwheelDevice device1;
    private WaterwheelDevice device2;
    private WaterwheelDevice device3;

    @BeforeEach
    void setUp() {
        device1 = TestDataFactory.createDevice(1, "设备1号", "plate", "ancient_song",
                30.0, 500.0, 12.5, 120, 24);
        device2 = TestDataFactory.createDevice(2, "设备2号", "round", "ancient_song",
                35.0, 580.0, 15.0, 140, 28);
        device3 = TestDataFactory.createDevice(3, "设备3号", "hook", "ancient_song",
                25.0, 420.0, 10.0, 100, 20);

        when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    double depth = invocation.getArgument(0);
                    double width = invocation.getArgument(1);
                    double angle = invocation.getArgument(2);
                    double speed = invocation.getArgument(3);
                    double effectiveArea = depth * width * Math.sin(Math.toRadians(angle));
                    double flow = effectiveArea * speed * 3600000 * 0.85;
                    return Math.max(0, flow);
                });
    }

    private void mockFindById(WaterwheelDevice... devices) {
        for (WaterwheelDevice d : devices) {
            when(deviceRepository.findById(d.getDeviceId())).thenReturn(Optional.of(d));
        }
    }

    private double callComputeCouplingFactor(int N) throws Exception {
        Method method = ParallelCoordinationOptimizer.class.getDeclaredMethod("computeCouplingFactor", int.class);
        method.setAccessible(true);
        return (double) method.invoke(parallelOptimizer, N);
    }

    @Test
    @DisplayName("testCouplingFactorAtOneIsOne - 单设备耦合系数=1.0")
    void testCouplingFactorAtOneIsOne() throws Exception {
        double factor = callComputeCouplingFactor(1);
        assertEquals(1.0, factor, 1e-9, "单设备(N=1)耦合系数应为1.0");

        double factorZero = callComputeCouplingFactor(0);
        assertEquals(1.0, factorZero, 1e-9, "N<=1时耦合系数应为1.0");

        double factorNeg = callComputeCouplingFactor(-5);
        assertEquals(1.0, factorNeg, 1e-9, "N为负时耦合系数应为1.0");
    }

    @Test
    @DisplayName("testCouplingFactorDecreasesWithN - N增大耦合系数减小（单调不增）")
    void testCouplingFactorDecreasesWithN() throws Exception {
        double prev = Double.MAX_VALUE;
        for (int N = 1; N <= 20; N++) {
            double current = callComputeCouplingFactor(N);
            assertTrue(current <= prev + 1e-12,
                    "N=" + N + " 耦合系数 " + current + " 不应大于 N=" + (N - 1) + " 的 " + prev);
            prev = current;
        }

        double f2 = callComputeCouplingFactor(2);
        double f5 = callComputeCouplingFactor(5);
        double f10 = callComputeCouplingFactor(10);
        assertTrue(f2 > f5, "N=2耦合系数应 > N=5");
        assertTrue(f5 > f10, "N=5耦合系数应 > N=10");
    }

    @Test
    @DisplayName("testCouplingFactorRangeAlwaysPositive - 任意N耦合系数>0")
    void testCouplingFactorRangeAlwaysPositive() throws Exception {
        for (int N = 1; N <= 1000; N++) {
            double factor = callComputeCouplingFactor(N);
            assertTrue(factor > 0,
                    "N=" + N + " 耦合系数应为正，实际: " + factor);
            assertTrue(factor <= 1.0,
                    "N=" + N + " 耦合系数应 <= 1.0，实际: " + factor);
        }

        double f1000 = callComputeCouplingFactor(1000);
        assertTrue(f1000 > 0.8,
                "即使N=1000，耦合系数仍应显著大于0 (kappa=0.08时约为0.92)，实际: " + f1000);
    }

    @Test
    @DisplayName("testCouplingFactorFormulaMatchesFormula - 公式正确：1-k*(N-1)/N，k=0.08")
    void testCouplingFactorFormulaMatchesFormula() throws Exception {
        double kappa = 0.08;
        int[] testNs = {1, 2, 3, 4, 5, 8, 10, 20, 50, 100};

        for (int N : testNs) {
            double expected;
            if (N <= 1) {
                expected = 1.0;
            } else {
                expected = 1.0 - kappa * (N - 1.0) / N;
            }
            double actual = callComputeCouplingFactor(N);
            assertEquals(expected, actual, 1e-9,
                    "N=" + N + " 耦合系数公式验证: 预期=" + expected + " 实际=" + actual);
        }

        double f2 = callComputeCouplingFactor(2);
        double expectedF2 = 1.0 - 0.08 * (2 - 1.0) / 2;
        assertEquals(expectedF2, f2, 1e-9, "N=2: 1-0.08*1/2 = 0.96");

        double f10 = callComputeCouplingFactor(10);
        double expectedF10 = 1.0 - 0.08 * 9.0 / 10;
        assertEquals(expectedF10, f10, 1e-9, "N=10: 1-0.08*9/10 = 0.928");
    }

    @Test
    @DisplayName("testOptimizeParallelWithCouplingInfoInDto - 返回couplingInfo字段完整")
    void testOptimizeParallelWithCouplingInfoInDto() {
        mockFindById(device1, device2, device3);

        ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                Arrays.asList(1, 2, 3), 75000.0, 15.0, "BALANCED");

        assertNotNull(result, "optimizeParallel 应返回非空结果");

        Map<String, Object> couplingInfo = result.getCouplingInfo();
        assertNotNull(couplingInfo, "couplingInfo 字段不应为 null");

        assertTrue(couplingInfo.containsKey("couplingFactor"),
                "couplingInfo 应包含 couplingFactor");
        assertTrue(couplingInfo.containsKey("couplingKappa"),
                "couplingInfo 应包含 couplingKappa");
        assertTrue(couplingInfo.containsKey("deviceCount"),
                "couplingInfo 应包含 deviceCount");
        assertTrue(couplingInfo.containsKey("couplingLossLh"),
                "couplingInfo 应包含 couplingLossLh");
        assertTrue(couplingInfo.containsKey("uncoupledTotalFlowLh"),
                "couplingInfo 应包含 uncoupledTotalFlowLh");
        assertTrue(couplingInfo.containsKey("description"),
                "couplingInfo 应包含 description");

        double kappa = ((Number) couplingInfo.get("couplingKappa")).doubleValue();
        assertEquals(0.08, kappa, 1e-9, "couplingKappa 应为 0.08");

        int deviceCount = ((Number) couplingInfo.get("deviceCount")).intValue();
        assertEquals(3, deviceCount, "deviceCount 应为 3");

        double couplingFactor = ((Number) couplingInfo.get("couplingFactor")).doubleValue();
        double expectedFactor = 1.0 - 0.08 * (3 - 1.0) / 3;
        assertEquals(expectedFactor, couplingFactor, 0.001,
                "couplingFactor 应与公式一致: N=3时≈0.9467");

        assertTrue(couplingFactor > 0.9 && couplingFactor < 0.96,
                "N=3时耦合系数应约为0.9467，实际: " + couplingFactor);

        String description = couplingInfo.get("description").toString();
        assertTrue(description.contains("κ") || description.contains("kappa")
                        || description.contains("耦合"),
                "description 应说明耦合系数含义");
        assertTrue(description.contains("0.08"),
                "description 应包含 kappa 值 0.08");

        double uncoupledFlow = ((Number) couplingInfo.get("uncoupledTotalFlowLh")).doubleValue();
        double coupledFlow = result.getTotalPredictedFlowLh().doubleValue();
        double couplingLoss = ((Number) couplingInfo.get("couplingLossLh")).doubleValue();
        assertTrue(couplingLoss >= 0, "耦合损失应 >= 0");
        assertEquals(uncoupledFlow * couplingFactor, coupledFlow, 1.0,
                "耦合后流量 ≈ 未耦合流量 × 耦合系数");
    }
}
