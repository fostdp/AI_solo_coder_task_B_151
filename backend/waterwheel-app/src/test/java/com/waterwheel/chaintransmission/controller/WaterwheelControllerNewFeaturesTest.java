package com.waterwheel.chaintransmission.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waterwheel.chaintransmission.comparison.service.ChainTypeComparisonService;
import com.waterwheel.chaintransmission.comparison.service.EraComparisonService;
import com.waterwheel.chaintransmission.dto.ChainTypeComparisonDTO;
import com.waterwheel.chaintransmission.dto.EraComparisonDTO;
import com.waterwheel.chaintransmission.dto.ParallelOptimizationDTO;
import com.waterwheel.chaintransmission.dto.VirtualOperationDTO;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.optimization.ParallelCoordinationOptimizer;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import com.waterwheel.chaintransmission.test.TestDataFactory;
import com.waterwheel.chaintransmission.virtualoperation.service.VirtualOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WaterwheelController.class)
@ActiveProfiles("test")
@DisplayName("Controller API集成测试：所有新功能端点")
class WaterwheelControllerNewFeaturesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.waterwheel.chaintransmission.service.WaterwheelService waterwheelService;

    @MockBean
    private com.waterwheel.chaintransmission.dtu_receiver.service.DtuReceiverService dtuReceiverService;

    @MockBean
    private ChainTypeComparisonService chainTypeComparisonService;

    @MockBean
    private EraComparisonService eraComparisonService;

    @MockBean
    private ParallelCoordinationOptimizer parallelOptimizer;

    @MockBean
    private VirtualOperationService virtualOperationService;

    @MockBean
    private WaterwheelDeviceRepository deviceRepository;

    private WaterwheelDevice testDevice;

    @BeforeEach
    void setUp() {
        testDevice = TestDataFactory.createDefaultDevice();
    }

    // =============  链型对比 API  =============
    @Nested
    @DisplayName("API1：链传动形式对比端点")
    class ChainTypeComparisonApi {

        @Test
        @DisplayName("POST /comparison/chain-types/{id} - 正常调用，200 OK")
        void testCompareChainTypesSuccess() throws Exception {
            ChainTypeComparisonDTO mockResult = new ChainTypeComparisonDTO();
            mockResult.setDeviceId(1);
            mockResult.setDeviceName("测试翻车");
            mockResult.setInputSpeedRPM(15.0);

            when(chainTypeComparisonService.compareChainTypes(
                    eq(1), eq(15.0), eq(80.0), eq(0.12), eq(0.25), eq(40.0)))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/comparison/chain-types/1")
                            .param("inputSpeedRPM", "15")
                            .param("inputTorque", "80")
                            .param("scraperDepth", "0.12")
                            .param("scraperWidth", "0.25")
                            .param("scraperAngle", "40")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deviceId").value(1))
                    .andExpect(jsonPath("$.deviceName").value("测试翻车"))
                    .andExpect(jsonPath("$.inputSpeedRPM").value(15.0));

            verify(chainTypeComparisonService, times(1)).compareChainTypes(
                    eq(1), eq(15.0), eq(80.0), eq(0.12), eq(0.25), eq(40.0));
        }

        @Test
        @DisplayName("POST /comparison/chain-types/{id} - 使用默认参数（不传递可选参数）")
        void testCompareChainTypesDefaultParams() throws Exception {
            ChainTypeComparisonDTO mockResult = new ChainTypeComparisonDTO();
            mockResult.setDeviceId(1);
            mockResult.setInputSpeedRPM(12.5);
            mockResult.setScraperDepth(0.12);

            when(chainTypeComparisonService.compareChainTypes(
                    eq(1), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/comparison/chain-types/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deviceId").value(1))
                    .andExpect(jsonPath("$.inputSpeedRPM").value(12.5))
                    .andExpect(jsonPath("$.scraperDepth").value(0.12));
        }

        @Test
        @DisplayName("GET /comparison/chain-types/meta - 返回3种链型元数据")
        void testGetChainTypeMeta() throws Exception {
            var mockMeta = Arrays.asList(
                    java.util.Map.of("code", "plate", "name", "板链"),
                    java.util.Map.of("code", "round", "name", "环链"),
                    java.util.Map.of("code", "hook", "name", "钩链")
            );

            when(chainTypeComparisonService.getAllChainTypeMeta()).thenReturn(mockMeta);

            String json = mockMvc.perform(get("/api/v1/comparison/chain-types/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].code").value("plate"))
                    .andReturn().getResponse().getContentAsString();

            assertTrue(json.contains("板链"));
            assertTrue(json.contains("环链"));
        }
    }

    // =============  跨时代对比 API  =============
    @Nested
    @DisplayName("API2：跨时代对比端点")
    class EraComparisonApi {

        @Test
        @DisplayName("POST /comparison/eras/{id} - 正常调用，返回2个时代结果")
        void testCompareErasSuccess() throws Exception {
            EraComparisonDTO mockResult = new EraComparisonDTO();
            mockResult.setDeviceId(1);
            mockResult.setReferenceDeviceName("测试翻车");
            mockResult.setResults(Arrays.asList(
                    createMockEraResult("ancient_song", "宋代水转翻车"),
                    createMockEraResult("modern_electric", "现代电动链式泵")
            ));

            when(eraComparisonService.compareEras(eq(1), eq(1.0), eq(1.0)))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/comparison/eras/1")
                            .param("chainSpeedRatio", "1.0")
                            .param("scraperSizeScale", "1.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deviceId").value(1))
                    .andExpect(jsonPath("$.results.length()").value(2))
                    .andExpect(jsonPath("$.results[0].eraCode").value("ancient_song"))
                    .andExpect(jsonPath("$.results[1].eraCode").value("modern_electric"));
        }

        @Test
        @DisplayName("GET /comparison/eras/meta - 返回2个时代元数据")
        void testGetEraMeta() throws Exception {
            var mockMeta = Arrays.asList(
                    java.util.Map.of("code", "ancient_song", "name", "宋代水转翻车"),
                    java.util.Map.of("code", "modern_electric", "name", "现代电动链式泵")
            );

            when(eraComparisonService.getAllEraMeta()).thenReturn(mockMeta);

            mockMvc.perform(get("/api/v1/comparison/eras/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].code").value("ancient_song"))
                    .andExpect(jsonPath("$[1].code").value("modern_electric"));
        }

        @Test
        @DisplayName("POST /comparison/eras/{id} - 自定义缩放比例")
        void testCompareErasCustomScale() throws Exception {
            EraComparisonDTO mockResult = new EraComparisonDTO();
            mockResult.setChainSpeedRatio(1.5);
            mockResult.setScraperSizeScale(1.2);

            when(eraComparisonService.compareEras(eq(1), eq(1.5), eq(1.2)))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/comparison/eras/1")
                            .param("chainSpeedRatio", "1.5")
                            .param("scraperSizeScale", "1.2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chainSpeedRatio").value(1.5))
                    .andExpect(jsonPath("$.scraperSizeScale").value(1.2));

            verify(eraComparisonService, times(1)).compareEras(eq(1), eq(1.5), eq(1.2));
        }

        private EraComparisonDTO.EraResult createMockEraResult(String code, String name) {
            EraComparisonDTO.EraResult r = new EraComparisonDTO.EraResult();
            r.setEraCode(code);
            r.setEraName(name);
            return r;
        }
    }

    // =============  并联优化 API  =============
    @Nested
    @DisplayName("API3：多台并联协同优化端点")
    class ParallelOptimizationApi {

        @Test
        @DisplayName("POST /optimization/parallel - 3台设备，BALANCED目标")
        void testParallelOptimizationBalanced() throws Exception {
            ParallelOptimizationDTO mockResult = new ParallelOptimizationDTO();
            mockResult.setConverged(true);
            mockResult.setIterations(25);
            mockResult.setTotalPredictedFlowLh(java.math.BigDecimal.valueOf(75000));
            mockResult.setTotalPowerConsumptionKW(java.math.BigDecimal.valueOf(12.5));
            mockResult.setAssignments(Arrays.asList(
                    createMockAssignment(1, "翻车1号"),
                    createMockAssignment(2, "翻车2号"),
                    createMockAssignment(3, "翻车3号")
            ));

            when(parallelOptimizer.optimizeParallel(
                    eq(Arrays.asList(1, 2, 3)), eq(80000.0), eq(15.0), eq("BALANCED")))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/optimization/parallel")
                            .param("deviceIds", "1", "2", "3")
                            .param("targetTotalFlowLh", "80000")
                            .param("maxTotalPowerKW", "15")
                            .param("optimizationGoal", "BALANCED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.converged").value(true))
                    .andExpect(jsonPath("$.iterations").value(25))
                    .andExpect(jsonPath("$.totalPredictedFlowLh").value(75000))
                    .andExpect(jsonPath("$.assignments.length()").value(3))
                    .andExpect(jsonPath("$.assignments[0].deviceId").value(1))
                    .andExpect(jsonPath("$.assignments[2].deviceId").value(3));
        }

        @Test
        @DisplayName("POST /optimization/parallel - MAX_FLOW目标")
        void testParallelOptimizationMaxFlow() throws Exception {
            ParallelOptimizationDTO mockResult = new ParallelOptimizationDTO();
            mockResult.setOptimizationGoal("MAX_FLOW");
            mockResult.setTotalPredictedFlowLh(java.math.BigDecimal.valueOf(90000));

            when(parallelOptimizer.optimizeParallel(
                    anyList(), anyDouble(), anyDouble(), eq("MAX_FLOW")))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/optimization/parallel")
                            .param("deviceIds", "1", "2")
                            .param("targetTotalFlowLh", "100000")
                            .param("optimizationGoal", "MAX_FLOW"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.optimizationGoal").value("MAX_FLOW"))
                    .andExpect(jsonPath("$.totalPredictedFlowLh").value(90000));
        }

        @Test
        @DisplayName("POST /optimization/parallel - 单设备并联")
        void testParallelOptimizationSingleDevice() throws Exception {
            ParallelOptimizationDTO mockResult = new ParallelOptimizationDTO();
            mockResult.setDeviceIds(Arrays.asList(1));
            mockResult.setAssignments(Arrays.asList(createMockAssignment(1, "单机")));

            when(parallelOptimizer.optimizeParallel(
                    eq(Arrays.asList(1)), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/optimization/parallel")
                            .param("deviceIds", "1")
                            .param("targetTotalFlowLh", "25000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deviceIds[0]").value(1))
                    .andExpect(jsonPath("$.assignments.length()").value(1));
        }

        @Test
        @DisplayName("POST /optimization/parallel - 默认目标BALANCED")
        void testParallelOptimizationDefaultGoal() throws Exception {
            ParallelOptimizationDTO mockResult = new ParallelOptimizationDTO();
            mockResult.setOptimizationGoal("BALANCED");

            when(parallelOptimizer.optimizeParallel(
                    anyList(), anyDouble(), anyDouble(), eq("BALANCED")))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/optimization/parallel")
                            .param("deviceIds", "1", "2")
                            .param("targetTotalFlowLh", "50000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.optimizationGoal").value("BALANCED"));
        }

        private ParallelOptimizationDTO.DeviceAssignment createMockAssignment(
                Integer id, String name) {
            ParallelOptimizationDTO.DeviceAssignment a = new ParallelOptimizationDTO.DeviceAssignment();
            a.setDeviceId(id);
            a.setDeviceName(name);
            a.setAssignedFlowLh(java.math.BigDecimal.valueOf(25000));
            a.setOptimalChainSpeedMs(java.math.BigDecimal.valueOf(1.5));
            return a;
        }
    }

    // =============  虚拟操作 API  =============
    @Nested
    @DisplayName("API4：公众虚拟操作端点")
    class VirtualOperationApi {

        @Test
        @DisplayName("POST /virtual-operation/{id}/step - 正常操作，状态NORMAL")
        void testVirtualOperationStepNormal() throws Exception {
            VirtualOperationDTO mockResult = new VirtualOperationDTO();
            mockResult.setDeviceId(1);
            mockResult.setChainSpeedMs(1.5);
            mockResult.setCurrentWaterFlowLh(java.math.BigDecimal.valueOf(15000));
            mockResult.setOperationStatus("NORMAL");
            mockResult.setOperationSeconds(1);
            mockResult.setWarnings(Arrays.asList());

            when(virtualOperationService.performOperation(
                    eq(1), eq(1.5), eq(1.0), eq(1), eq(false)))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/virtual-operation/1/step")
                            .param("chainSpeedMs", "1.5")
                            .param("waterLevelFactor", "1.0")
                            .param("operationSeconds", "1")
                            .param("resetSession", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chainSpeedMs").value(1.5))
                    .andExpect(jsonPath("$.currentWaterFlowLh").value(15000))
                    .andExpect(jsonPath("$.operationStatus").value("NORMAL"))
                    .andExpect(jsonPath("$.operationSeconds").value(1))
                    .andExpect(jsonPath("$.warnings").isArray())
                    .andExpect(jsonPath("$.warnings.length()").value(0));
        }

        @Test
        @DisplayName("POST /virtual-operation/{id}/step - 高速操作，WARNING状态带警告")
        void testVirtualOperationStepWarning() throws Exception {
            VirtualOperationDTO mockResult = new VirtualOperationDTO();
            mockResult.setOperationStatus("WARNING");
            mockResult.setWarnings(Arrays.asList(
                    "链条张力超过安全阈值 6000N，建议降低转速",
                    "高速运行，离心损失和磨损显著增加"
            ));

            when(virtualOperationService.performOperation(
                    eq(1), eq(3.5), eq(1.0), eq(1), eq(false)))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/virtual-operation/1/step")
                            .param("chainSpeedMs", "3.5")
                            .param("operationSeconds", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.operationStatus").value("WARNING"))
                    .andExpect(jsonPath("$.warnings.length()").value(2))
                    .andExpect(jsonPath("$.warnings[0]").value(
                            org.hamcrest.Matchers.containsString("张力")));
        }

        @Test
        @DisplayName("POST /virtual-operation/{id}/step - 重置会话")
        void testVirtualOperationStepReset() throws Exception {
            VirtualOperationDTO mockResult = new VirtualOperationDTO();
            mockResult.setOperationSeconds(0);
            mockResult.setTotalWaterLiters(java.math.BigDecimal.ZERO);

            when(virtualOperationService.performOperation(
                    eq(1), eq(1.5), eq(1.0), eq(0), eq(true)))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/virtual-operation/1/step")
                            .param("chainSpeedMs", "1.5")
                            .param("resetSession", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.operationSeconds").value(0))
                    .andExpect(jsonPath("$.totalWaterLiters").value(0));
        }

        @Test
        @DisplayName("GET /virtual-operation/{id}/state - 获取当前状态")
        void testGetVirtualOperationState() throws Exception {
            VirtualOperationDTO mockResult = new VirtualOperationDTO();
            mockResult.setDeviceId(1);
            mockResult.setChainSpeedMs(1.5);
            mockResult.setWaterLevelFactor(1.0);

            when(virtualOperationService.performOperation(
                    eq(1), eq(1.5), eq(1.0), eq(0), eq(false)))
                    .thenReturn(mockResult);

            mockMvc.perform(get("/api/v1/virtual-operation/1/state"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deviceId").value(1))
                    .andExpect(jsonPath("$.chainSpeedMs").value(1.5));
        }

        @Test
        @DisplayName("POST /virtual-operation/reset-all - 重置所有会话")
        void testResetAllVirtualSessions() throws Exception {
            var mockResult = java.util.Map.of("cleared", 3);

            when(virtualOperationService.resetAllSessions()).thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/virtual-operation/reset-all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cleared").value(3));
        }

        @Test
        @DisplayName("POST /virtual-operation/{id}/step - 默认水位系数1.0")
        void testVirtualOperationDefaultWaterLevel() throws Exception {
            VirtualOperationDTO mockResult = new VirtualOperationDTO();
            mockResult.setWaterLevelFactor(1.0);

            when(virtualOperationService.performOperation(
                    eq(1), eq(1.5), eq(1.0), eq(1), eq(false)))
                    .thenReturn(mockResult);

            mockMvc.perform(post("/api/v1/virtual-operation/1/step")
                            .param("chainSpeedMs", "1.5")
                            .param("operationSeconds", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.waterLevelFactor").value(1.0));
        }
    }

    // =============  健康检查 & CORS  =============
    @Nested
    @DisplayName("公共端点验证")
    class CommonEndpoints {

        @Test
        @DisplayName("GET /health - 返回v1.1.0和新功能标识")
        void testHealthCheckIncludesNewFeatures() throws Exception {
            String json = mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.version").value("1.1.0"))
                    .andReturn().getResponse().getContentAsString();

            assertTrue(json.contains("chain-type-comparison"),
                    "健康检查应包含chain-type-comparison特征");
            assertTrue(json.contains("virtual-operation"),
                    "健康检查应包含virtual-operation特征");
        }

        @Test
        @DisplayName("CORS跨域验证：允许所有来源")
        void testCorsConfiguration() throws Exception {
            mockMvc.perform(options("/api/v1/comparison/chain-types/meta")
                            .header("Origin", "http://test.example.com")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Access-Control-Allow-Origin"));
        }
    }

    // =============  异常路径  =============
    @Nested
    @DisplayName("异常路径验证")
    class ExceptionPaths {

        @Test
        @DisplayName("不存在的设备ID - 服务层抛出异常")
        void testInvalidDeviceIdThrows() throws Exception {
            when(chainTypeComparisonService.compareChainTypes(
                    eq(999), any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("设备不存在: 999"));

            mockMvc.perform(post("/api/v1/comparison/chain-types/999"))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("无效的HTTP方法 - 405 Method Not Allowed")
        void testWrongHttpMethod() throws Exception {
            mockMvc.perform(put("/api/v1/comparison/chain-types/1"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("并联优化：空设备列表 - 服务层抛异常")
        void testEmptyDeviceListInParallel() throws Exception {
            when(parallelOptimizer.optimizeParallel(
                    eq(Arrays.asList()), anyDouble(), anyDouble(), anyString()))
                    .thenThrow(new IllegalArgumentException("至少需要1台设备"));

            mockMvc.perform(post("/api/v1/optimization/parallel")
                            .param("deviceIds", "")
                            .param("targetTotalFlowLh", "50000"))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("链速参数缺失 - 400 Bad Request")
        void testMissingRequiredParam() throws Exception {
            mockMvc.perform(post("/api/v1/virtual-operation/1/step"))
                    .andExpect(status().isBadRequest());
        }
    }
}
