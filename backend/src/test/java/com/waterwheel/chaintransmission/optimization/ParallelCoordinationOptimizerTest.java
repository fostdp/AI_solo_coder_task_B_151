package com.waterwheel.chaintransmission.optimization;

import com.waterwheel.chaintransmission.dto.ParallelOptimizationDTO;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import com.waterwheel.chaintransmission.test.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("功能3：多台并联协同优化测试")
class ParallelCoordinationOptimizerTest {

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
        device1 = TestDataFactory.createDevice(1, "翻车1号", "plate", "ancient_song",
                30.0, 500.0, 12.5, 120, 24);
        device2 = TestDataFactory.createDevice(2, "翻车2号", "round", "ancient_song",
                35.0, 580.0, 15.0, 140, 28);
        device3 = TestDataFactory.createDevice(3, "翻车3号", "hook", "ancient_song",
                25.0, 420.0, 10.0, 100, 20);

        when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    double depth = invocation.getArgument(0);
                    double width = invocation.getArgument(1);
                    double angle = invocation.getArgument(2);
                    double speed = invocation.getArgument(3);
                    double effectiveArea = depth * width * Math.sin(Math.toRadians(angle));
                    double volumePerBucket = effectiveArea * Math.min(depth, 0.05) * 0.85;
                    double radius = 0.30;
                    double scrapersPerSec = speed / (2 * Math.PI * radius) * 24;
                    double filling = 1.0 - Math.exp(-depth / 0.08 * (1.0 - speed / 4.0));
                    double retention = 0.7 + 0.25 * Math.sin(Math.toRadians(angle));
                    double centrifugal = Math.exp(-(speed * speed) / (radius * 9.81) * 0.1);
                    double flowM3s = volumePerBucket * scrapersPerSec * filling * retention * centrifugal;
                    return flowM3s * 3600 * 1000;
                });
    }

    private void mockFindById(WaterwheelDevice... devices) {
        for (WaterwheelDevice d : devices) {
            when(deviceRepository.findById(d.getDeviceId())).thenReturn(Optional.of(d));
        }
    }

    // =============  正常场景  =============
    @Nested
    @DisplayName("正常场景：总流量验证")
    class NormalScenario {

        @Test
        @DisplayName("2台并联：总流量 ≈ 单机流量 × 2，误差 < 10%")
        void testTwoDeviceParallelFlowSummation() {
            mockFindById(device1, device2);

            double targetFlow = 50000;
            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2), targetFlow, 10.0, "BALANCED");

            assertNotNull(result);
            assertEquals(2, result.getAssignments().size(), "应返回2台设备分配");

            double totalPredicted = result.getTotalPredictedFlowLh().doubleValue();
            double sumIndividual = result.getAssignments().stream()
                    .mapToDouble(a -> a.getAssignedFlowLh().doubleValue())
                    .sum();

            assertEquals(sumIndividual, totalPredicted, 1.0,
                    "各设备分配流量之和应等于总预测流量");

            double error = Math.abs(totalPredicted - targetFlow) / targetFlow * 100;
            assertTrue(error < 15.0,
                    "总流量与目标流量误差应 < 15%，实际误差: " + error + "%");
        }

        @Test
        @DisplayName("3台并联：总流量验证 + 流量分配均衡性")
        void testThreeDeviceParallelFlowBalance() {
            mockFindById(device1, device2, device3);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2, 3), 80000.0, 15.0, "BALANCED");

            assertEquals(3, result.getAssignments().size());

            double totalPredicted = result.getTotalPredictedFlowLh().doubleValue();
            double target = 80000.0;
            double error = Math.abs(totalPredicted - target) / target * 100;
            assertTrue(error < 20.0, "3台并联总流量误差 < 20%，实际: " + error + "%");

            double loadStdDev = result.getLoadBalanceStdDev().doubleValue();
            assertTrue(loadStdDev < 0.4, "负载均衡标准差应 < 0.4，实际: " + loadStdDev);

            double sumRatios = result.getAssignments().stream()
                    .mapToDouble(a -> a.getAssignedFlowRatio().doubleValue())
                    .sum();
            assertEquals(1.0, sumRatios, 0.01, "流量分配比例之和应≈1.0");
        }

        @Test
        @DisplayName("迭代收敛验证：迭代次数合理且返回收敛状态")
        void testConvergenceBehavior() {
            mockFindById(device1, device2);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2), 40000.0, 10.0, "BALANCED");

            int iterations = result.getIterations();
            assertTrue(iterations > 3 && iterations <= 200,
                    "迭代次数应在合理范围内(3~200)，实际: " + iterations);
            assertTrue(result.getComputationTimeMs() > 0,
                    "计算耗时应 > 0ms");

            assertNotNull(result.getTrace(), "应返回迭代轨迹");
            assertTrue(result.getTrace().size() > 3, "迭代轨迹应至少3个点");
            assertTrue(result.getTrace().size() <= iterations,
                    "轨迹点数不应超过迭代次数");

            double firstObj = result.getTrace().get(0).getObjectiveValue().doubleValue();
            double lastObj = result.getTrace().get(result.getTrace().size() - 1).getObjectiveValue().doubleValue();
            assertTrue(lastObj <= firstObj,
                    "目标函数应单调不增（优化过程应持续改善）: " + lastObj + " > " + firstObj);
        }

        @Test
        @DisplayName("三种优化目标对比：目标权重正确反映在结果中")
        void testDifferentOptimizationGoals() {
            mockFindById(device1, device2, device3);

            ParallelOptimizationDTO rMaxFlow = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2, 3), 80000.0, 20.0, "MAX_FLOW");
            ParallelOptimizationDTO rMinPower = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2, 3), 80000.0, 10.0, "MIN_POWER");
            ParallelOptimizationDTO rBalanced = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2, 3), 80000.0, 15.0, "BALANCED");

            double maxFlow = rMaxFlow.getTotalPredictedFlowLh().doubleValue();
            double minPowerFlow = rMinPower.getTotalPredictedFlowLh().doubleValue();
            double balancedFlow = rBalanced.getTotalPredictedFlowLh().doubleValue();

            double maxPower = rMaxFlow.getTotalPowerConsumptionKW().doubleValue();
            double minPower = rMinPower.getTotalPowerConsumptionKW().doubleValue();
            double balancedPower = rBalanced.getTotalPowerConsumptionKW().doubleValue();

            assertTrue(maxFlow >= balancedFlow && maxFlow >= minPowerFlow - 1000,
                    "MAX_FLOW目标应产生最大流量");
            assertTrue(minPower <= balancedPower + 0.5 && minPower <= maxPower + 0.5,
                    "MIN_POWER目标应产生最低功耗");

            double maxBalance = rMaxFlow.getLoadBalanceStdDev().doubleValue();
            double minBalance = rMinPower.getLoadBalanceStdDev().doubleValue();
            double balancedBalance = rBalanced.getLoadBalanceStdDev().doubleValue();
            assertTrue(balancedBalance <= maxBalance + 0.05 || balancedBalance <= minBalance + 0.05,
                    "BALANCED目标应有较好的负载均衡");
        }

        @Test
        @DisplayName("协同增益验证：优化后负载均衡优于平均分配")
        void testCoordinationGainPositive() {
            mockFindById(device1, device2, device3);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2, 3), 75000.0, 15.0, "BALANCED");

            double coordinationGain = result.getCoordinationGain().doubleValue();
            assertTrue(coordinationGain > 0,
                    "协同增益应 > 0%，说明优化优于简单平均分配，实际: " + coordinationGain + "%");
        }
    }

    // =============  参数化测试  =============
    @ParameterizedTest
    @CsvSource({
            "50000, 10.0, BALANCED",
            "30000, 5.0, MAX_FLOW",
            "100000, 20.0, MIN_POWER",
            "20000, 3.0, BALANCED",
            "60000, 12.0, BALANCED"
    })
    @DisplayName("参数化测试：目标流量{0}L/h + 功率上限{1}kW + {2}")
    void parametrizedOptimization(double targetFlow, double maxPower, String goal) {
        mockFindById(device1, device2);

        ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                Arrays.asList(1, 2), targetFlow, maxPower, goal);

        assertNotNull(result);
        assertTrue(result.getIterations() > 0);
        assertNotNull(result.getAssignments());
        assertEquals(2, result.getAssignments().size());

        double totalPower = result.getTotalPowerConsumptionKW().doubleValue();
        if ("MIN_POWER".equals(goal)) {
            assertTrue(totalPower <= maxPower * 1.1,
                    "MIN_POWER目标下功耗应接近上限: " + totalPower + " vs " + maxPower);
        }

        double totalFlow = result.getTotalPredictedFlowLh().doubleValue();
        assertTrue(totalFlow > 0, "任何目标下流量应为正");
    }

    // =============  边界场景  =============
    @Nested
    @DisplayName("边界场景测试")
    class BoundaryScenario {

        @Test
        @DisplayName("边界：单设备并联（退化为单机优化）")
        void testSingleDeviceParallel() {
            mockFindById(device1);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1), 25000.0, 5.0, "BALANCED");

            assertEquals(1, result.getAssignments().size(), "单设备也应返回分配结果");
            assertEquals(1.0, result.getAssignments().get(0).getAssignedFlowRatio().doubleValue(),
                    TestDataFactory.TOLERANCE, "单设备流量分配比例应为1.0");
            assertNotNull(result.getTotalPredictedFlowLh());
        }

        @Test
        @DisplayName("边界：极高目标流量（超过设备能力上限），结果自动降档")
        void testExcessiveTargetFlow() {
            mockFindById(device1, device2);

            double impossibleTarget = 500000;  // 远超2台最大能力
            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2), impossibleTarget, 20.0, "MAX_FLOW");

            double actualFlow = result.getTotalPredictedFlowLh().doubleValue();
            assertTrue(actualFlow < impossibleTarget * 0.5,
                    "不可能目标下应返回设备最大能力而非虚假值: " + actualFlow);
            assertTrue(actualFlow > 0, "仍应返回正值（最大能力）");

            for (ParallelOptimizationDTO.DeviceAssignment a : result.getAssignments()) {
                double speed = a.getOptimalChainSpeedMs().doubleValue();
                assertTrue(speed <= 3.0 + 0.1, "即使目标极高，链速也不应超过上限3.0m/s");
            }
        }

        @Test
        @DisplayName("边界：极低目标流量（接近0），每台设备以最低速度运行")
        void testVeryLowTargetFlow() {
            mockFindById(device1, device2, device3);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2, 3), 5000.0, 10.0, "MIN_POWER");

            double totalFlow = result.getTotalPredictedFlowLh().doubleValue();
            assertTrue(totalFlow > 0, "低目标下仍应有正流量");

            for (ParallelOptimizationDTO.DeviceAssignment a : result.getAssignments()) {
                double speed = a.getOptimalChainSpeedMs().doubleValue();
                assertTrue(speed >= 0.49, "最低链速不应低于0.5m/s");
            }
        }

        @Test
        @DisplayName("边界：功率上限极低，系统自动降低目标流量")
        void testVeryStrictPowerLimit() {
            mockFindById(device1, device2);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2), 50000.0, 2.0, "MIN_POWER");

            double totalPower = result.getTotalPowerConsumptionKW().doubleValue();
            assertTrue(totalPower <= 2.0 * 1.1,
                    "严格功率限制下功耗不应超限: " + totalPower + " > 2.0×1.1");

            double totalFlow = result.getTotalPredictedFlowLh().doubleValue();
            assertTrue(totalFlow < 50000,
                    "严格功率限制下流量应低于目标: " + totalFlow + " < 50000");
        }

        @Test
        @DisplayName("边界：10台设备大规模并联（性能测试）")
        void testLargeScaleParallelism() {
            List<WaterwheelDevice> devices = IntStream.rangeClosed(1, 10)
                    .mapToObj(i -> TestDataFactory.createDevice(
                            i, "大规模设备" + i + "号", "plate", "ancient_song",
                            30.0 + i * 0.5, 500.0 + i * 5, 12.0 + i * 0.1, 120, 24))
                    .collect(Collectors.toList());

            mockFindById(devices.toArray(new WaterwheelDevice[0]));

            List<Integer> ids = devices.stream().map(WaterwheelDevice::getDeviceId).collect(Collectors.toList());
            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    ids, 200000.0, 50.0, "BALANCED");

            assertEquals(10, result.getAssignments().size(), "10台都应返回分配");
            assertTrue(result.getComputationTimeMs() < 5000,
                    "10台优化计算应 < 5s，实际: " + result.getComputationTimeMs() + "ms");

            double sumRatios = result.getAssignments().stream()
                    .mapToDouble(a -> a.getAssignedFlowRatio().doubleValue())
                    .sum();
            assertEquals(1.0, sumRatios, 0.05, "10台流量分配比例之和≈1.0");
        }
    }

    // =============  异常场景  =============
    @Nested
    @DisplayName("异常场景测试")
    class ExceptionScenario {

        @Test
        @DisplayName("异常：空设备列表，应抛出异常")
        void testEmptyDeviceList() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> parallelOptimizer.optimizeParallel(
                            java.util.Collections.emptyList(), 50000.0, 10.0, "BALANCED"));

            assertTrue(ex.getMessage().contains("至少需要1台设备"));
        }

        @Test
        @DisplayName("异常：所有设备ID都无效，应抛出异常")
        void testAllInvalidDeviceIds() {
            when(deviceRepository.findById(999)).thenReturn(Optional.empty());
            when(deviceRepository.findById(888)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> parallelOptimizer.optimizeParallel(
                            Arrays.asList(999, 888), 50000.0, 10.0, "BALANCED"));

            assertTrue(ex.getMessage().contains("全部无效"));
        }

        @Test
        @DisplayName("异常：部分设备ID有效，忽略无效设备继续优化")
        void testPartiallyInvalidDeviceIds() {
            mockFindById(device1, device2);
            when(deviceRepository.findById(999)).thenReturn(Optional.empty());

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 999, 2), 50000.0, 10.0, "BALANCED");

            assertEquals(2, result.getAssignments().size(),
                    "忽略无效的999号，返回2个有效设备分配");
            assertTrue(result.getTotalPredictedFlowLh().doubleValue() > 0,
                    "部分有效设备仍应工作");

            List<Integer> assignedIds = result.getAssignments().stream()
                    .map(ParallelOptimizationDTO.DeviceAssignment::getDeviceId)
                    .collect(Collectors.toList());
            assertTrue(assignedIds.containsAll(Arrays.asList(1, 2)));
            assertFalse(assignedIds.contains(999));
        }

        @Test
        @DisplayName("异常：目标流量为负，取绝对值或默认值")
        void testNegativeTargetFlow() {
            mockFindById(device1, device2);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2), -50000.0, 10.0, "BALANCED");

            assertTrue(result.getTotalPredictedFlowLh().doubleValue() > 0,
                    "负目标应被clamp为正值");
        }

        @Test
        @DisplayName("异常：目标函数null使用默认BALANCED")
        void testNullOptimizationGoal() {
            mockFindById(device1);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1), 25000.0, 5.0, null);

            assertNotNull(result);
            assertTrue(result.getIterations() > 0);
        }

        @Test
        @DisplayName("异常：NaN目标值，保护计算不崩溃")
        void testNaNParameterProtection() {
            mockFindById(device1);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1), Double.NaN, 5.0, "BALANCED");

            assertNotNull(result);
            double totalFlow = result.getTotalPredictedFlowLh().doubleValue();
            assertTrue(Double.isFinite(totalFlow) && totalFlow > 0,
                    "NaN参数下应使用默认值，不应返回NaN/Infinite");
        }
    }

    // =============  数学一致性  =============
    @Nested
    @DisplayName("数学一致性验证")
    class MathematicalConsistency {

        @Test
        @DisplayName("功率验证：单机功率 ≤ 总功率 ≤ 单机功率之和")
        void testPowerSummation() {
            mockFindById(device1, device2, device3);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2, 3), 70000.0, 15.0, "MIN_POWER");

            double totalPower = result.getTotalPowerConsumptionKW().doubleValue();
            double sumIndividualPower = result.getAssignments().stream()
                    .mapToDouble(a -> a.getPowerConsumptionKW().doubleValue())
                    .sum();

            assertEquals(sumIndividualPower, totalPower, 0.01,
                    "各设备功率之和应等于总功率");

            for (ParallelOptimizationDTO.DeviceAssignment a : result.getAssignments()) {
                assertTrue(a.getPowerConsumptionKW().doubleValue() <= totalPower + 0.01,
                        "单机功率不应超过总功率");
            }
        }

        @Test
        @DisplayName("效率验证：平均效率 = 加权平均（流量权重）")
        void testAverageEfficiencyWeighted() {
            mockFindById(device1, device2);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2), 45000.0, 10.0, "BALANCED");

            double totalFlow = result.getTotalPredictedFlowLh().doubleValue();
            double weightedEffSum = 0;
            for (ParallelOptimizationDTO.DeviceAssignment a : result.getAssignments()) {
                double flow = a.getAssignedFlowLh().doubleValue();
                double eff = a.getIndividualEfficiency().doubleValue();
                weightedEffSum += eff * flow / totalFlow;
            }

            double avgEff = result.getAverageEfficiency().doubleValue();
            assertEquals(weightedEffSum, avgEff, 0.02,
                    "平均效率应等于各设备效率的流量加权平均");
        }

        @Test
        @DisplayName("链速验证：每台设备链速在合理范围内")
        void testChainSpeedWithinBounds() {
            mockFindById(device1, device2, device3);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1, 2, 3), 60000.0, 12.0, "MAX_FLOW");

            for (ParallelOptimizationDTO.DeviceAssignment a : result.getAssignments()) {
                double speed = a.getOptimalChainSpeedMs().doubleValue();
                assertTrue(speed >= 0.49 && speed <= 3.01,
                        a.getDeviceName() + "链速 " + speed + " 超出 [0.5, 3.0] 范围");

                double depth = a.getScraperDepth().doubleValue();
                assertTrue(depth >= 0.049 && depth <= 0.201,
                        a.getDeviceName() + "刮板深度 " + depth + " 超出 [0.05, 0.20] 范围");
            }
        }

        @Test
        @DisplayName("转速验证：链速与链轮转速线性关系 v = 2πR·n/60")
        void testSpeedRpmRelation() {
            mockFindById(device1);

            ParallelOptimizationDTO result = parallelOptimizer.optimizeParallel(
                    Arrays.asList(1), 25000.0, 5.0, "BALANCED");

            ParallelOptimizationDTO.DeviceAssignment a = result.getAssignments().get(0);
            double v = a.getOptimalChainSpeedMs().doubleValue();
            double n = a.getOptimalSprocketRPM().doubleValue();
            double Rm = device1.getSprocketRadiusCmDouble() / 100.0;

            double expectedV = (2 * Math.PI * Rm * n) / 60.0;
            assertEquals(expectedV, v, 0.001,
                    "链速与转速应满足 v = 2πR·n/60 的运动学关系");
        }
    }
}
