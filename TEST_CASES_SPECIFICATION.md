# 新增功能测试用例说明

## 一、测试体系概览

### 测试框架
- **核心框架**：JUnit 5 + Mockito 5 + Spring Boot Test 3.2
- **持久层测试**：H2内存数据库（PostgreSQL兼容模式）
- **Web层测试**：Spring MockMvc
- **覆盖率目标**：核心业务逻辑 > 85%

### 测试目录结构
```
src/test/
├── java/com/waterwheel/chaintransmission/
│   ├── test/
│   │   └── TestDataFactory.java          # 测试数据工厂（测试通用工具）
│   ├── comparison/
│   │   ├── ChainTypeComparisonServiceTest.java     # 功能1测试
│   │   └── EraComparisonServiceTest.java          # 功能2测试
│   ├── optimization/
│   │   └── ParallelCoordinationOptimizerTest.java  # 功能3测试
│   ├── virtualoperation/
│   │   └── VirtualOperationServiceTest.java        # 功能4测试
│   └── controller/
│       └── WaterwheelControllerNewFeaturesTest.java # API集成测试
└── resources/
    └── application-test.yml                # 测试配置（H2内存数据库）
```

### 测试用例总数
| 模块 | 正常场景 | 边界场景 | 异常场景 | 参数化测试 | 小计 |
|------|---------|---------|---------|-----------|------|
| 链型对比 | 4 | 4 | 4 | 0 | 12 |
| 跨时代对比 | 6 | 3 | 4 | 4 | 17 |
| 并联优化 | 5 | 5 | 6 | 5 | 21 |
| 虚拟操作 | 9 | 6 | 8 | 2 | 25 |
| API集成 | 4×3=12 | - | 4 | - | 16 |
| **合计** | **36** | **18** | **26** | **11** | **91** |

---

## 二、详细测试矩阵

### 功能1：链传动形式对比测试
**测试类**：[ChainTypeComparisonServiceTest.java](file:///d:/SOLO-2/AI_solo_coder_task_B_151/backend/src/test/java/com/waterwheel/chaintransmission/comparison/ChainTypeComparisonServiceTest.java)

| 类别 | 用例名称 | 验证内容 |
|------|---------|---------|
| **正常** | 三种链型效率排序 | 环链(92%) > 板链(88%) > 钩链(78%)，提水量与效率正相关 |
| **正常** | 传动效率参数正确性 | 每种链型的效率值与枚举定义完全一致（±1e-4） |
| **正常** | 链条张力顺序 | 钩链 > 板链 > 环链，与摩擦系数正相关 |
| **正常** | 链型元数据接口 | 返回3种链型的完整10项属性 |
| **边界** | 环链许用上限18RPM | 不触发共振风险 |
| **边界** | 钩链超许用转速 | 磨损率显著升高，寿命下降 |
| **边界** | 最小刮板深度0.05m | 正流量但低于正常深度 |
| **边界** | 最大刮板角度60° | 正确处理边界输入 |
| **异常** | 无效设备ID | 抛出IllegalArgumentException，包含ID信息 |
| **异常** | 负转速输入 | 提水量被clamp为0，不崩溃 |
| **异常** | 零扭矩输入 | 张力计算保护，不出现NaN |
| **异常** | null可选参数 | 使用设备默认值回退 |
| **数学** | 功率守恒 | 输入功率 × 效率 ≈ 有效功率 + 摩擦损耗 |
| **数学** | 保水率Trade-off | 钩链保水率最高但提水量仍最低（传动效率瓶颈） |

---

### 功能2：跨时代对比测试
**测试类**：[EraComparisonServiceTest.java](file:///d:/SOLO-2/AI_solo_coder_task_B_151/backend/src/test/java/com/waterwheel/chaintransmission/comparison/EraComparisonServiceTest.java)

| 类别 | 用例名称 | 验证内容 |
|------|---------|---------|
| **正常** | 总效率提升验证 | 现代/古代 ≈ 1.92倍（0.686 / 0.358） |
| **正常** | 提水量提升验证 | 现代提水量应为古代的2.5~3.5倍 |
| **正常** | 6项核心指标 | 提水量/总效率/能量成本/转速/维护工时/寿命全部对比 |
| **正常** | 三级效率叠乘 | η_total = η_机械 × η_传动 × η_控制 |
| **正常** | 历史语境 | 宋代包含dynasty/inventor，现代包含controlSystem/standard |
| **正常** | 转速比验证 | 22RPM / 3.5RPM = 6.3倍 |
| **参数化** | 多缩放比例 | 4组(速度比×尺寸比)参数全部可正常计算 |
| **边界** | 最小缩放 0.5×0.8 | 仍可对比，现代仍领先 |
| **边界** | 最大缩放 2.0×1.5 | 验证极限性能（>80m³/h） |
| **边界** | 宋代能量成本 | 水力驱动能量成本为0，现代有电费 |
| **异常** | 无效设备ID | 正确抛出异常 |
| **异常** | null缩放比例 | 默认值1.0回退 |
| **异常** | 超大功率 | 功率封顶保护 |
| **异常** | 零流量 | 能量成本计算不除零 |
| **元数据** | 时代元数据接口 | 返回2个时代完整13项属性 |

---

### 功能3：多台并联协同优化测试
**测试类**：[ParallelCoordinationOptimizerTest.java](file:///d:/SOLO-2/AI_solo_coder_task_B_151/backend/src/test/java/com/waterwheel/chaintransmission/optimization/ParallelCoordinationOptimizerTest.java)

| 类别 | 用例名称 | 验证内容 |
|------|---------|---------|
| **正常** | 2台并联流量求和 | Σ单机流量 = 总流量，目标误差<15% |
| **正常** | 3台并联负载均衡 | 负载均衡标准差 < 0.4，流量比例之和≈1.0 |
| **正常** | 迭代收敛 | 目标函数单调不增，迭代轨迹完整 |
| **正常** | 三种优化目标 | MAX_FLOW→流量最大，MIN_POWER→功耗最低，BALANCED→均衡 |
| **正常** | 协同增益 | 优化后优于平均分配，增益>0% |
| **参数化** | 5组目标配置 | 不同目标/功耗/组合全部可求解 |
| **边界** | 单设备并联 | 退化为单机优化，分配比例=1.0 |
| **边界** | 超高目标流量 | 自动降档到设备最大能力，不虚假承诺 |
| **边界** | 极低目标流量 | 所有设备以最低速0.5m/s运行 |
| **边界** | 极低功率上限 | 自动降低目标流量满足约束 |
| **边界** | 10台大规模并联 | 计算耗时<5s，分配比例和=1.0±5% |
| **异常** | 空设备列表 | 抛异常"至少需要1台设备" |
| **异常** | 全无效ID | 抛异常"设备ID全部无效" |
| **异常** | 部分有效ID | 忽略无效ID，有效设备继续优化 |
| **异常** | 负目标流量 | 取绝对值回退 |
| **异常** | null优化目标 | 默认BALANCED |
| **异常** | NaN参数 | 自动用默认值，不崩溃 |
| **数学** | 功率求和 | Σ单机功率 = 总功率 |
| **数学** | 加权平均效率 | 平均效率 = Σ(η_i × Q_i) / ΣQ_i |
| **数学** | 链速范围 | 所有设备链速∈[0.5, 3.0]m/s |
| **数学** | 运动学关系 | v = 2πR·n/60 严格成立 |

---

### 功能4：虚拟操作体验测试
**测试类**：[VirtualOperationServiceTest.java](file:///d:/SOLO-2/AI_solo_coder_task_B_151/backend/src/test/java/com/waterwheel/chaintransmission/virtualoperation/VirtualOperationServiceTest.java)

| 类别 | 用例名称 | 验证内容 |
|------|---------|---------|
| **正常** | 转速-流量正相关 | 5档转速流量严格递增（单调性） |
| **正常** | 水位影响链 | 水位↑ → 流量↑ → 张力↑ → 载荷↑ |
| **正常** | 累计提水量 | 运行10秒累计 = 瞬时流量 × 时间（±10%） |
| **正常** | 会话隔离 | 2台设备会话独立，累计时间不串扰 |
| **正常** | 中等参数状态 | 1.5m/s+1.0水位 → NORMAL，无告警 |
| **正常** | 效率曲线 | 中速(1.0-2.0)效率最高，高低速下降 |
| **正常** | 链节位置数据 | 返回≥15个浮点数，为3的倍数(x,y,z) |
| **正常** | 位置相位累积 | 连续调用链节位置前进（时间推进） |
| **正常** | 转速全覆盖 | 0.3-4.0m/s共9档，全部输出有效 |
| **边界** | 最高速4.0m/s | 触发"高速运行，离心损失"警告 |
| **边界** | 最低速0.3m/s | 触发"转速过低，填充效率下降"警告 |
| **边界** | 共振区2.1m/s | 振幅显著升高（>1.5mm） |
| **边界** | 最大张力区 | 3.5m/s+1.3水位 → CRITICAL |
| **边界** | 1小时长运行 | 累计水量误差<15% |
| **边界** | 会话重置 | 运行时长+累计水量归零 |
| **边界** | 状态判定矩阵 | 5组(速度×水位)参数化测试 |
| **异常** | 无效设备ID | 抛异常含ID信息 |
| **异常** | 负转速 | 自动clamp到0.3m/s |
| **异常** | 超高速10m/s | 自动clamp到4.0m/s |
| **异常** | 零水位 | 自动clamp到0.3 |
| **异常** | NaN转速 | 保护机制，范围限制 |
| **异常** | Infinite水位 | 保护机制，范围限制 |
| **异常** | 重置所有会话 | 清除计数正确 |
| **异常** | null可选参数 | 默认值回退 |
| **张力** | 分布正定性 | 50个采样点全为正，CV>0.1 |
| **张力** | 高速波动更大 | 3.5m/s张力波动 > 0.5m/s |
| **性能** | 单次响应 | <50ms（交互流畅） |
| **性能** | 100次连续 | 平均<10ms（吞吐量） |

---

### API集成测试
**测试类**：[WaterwheelControllerNewFeaturesTest.java](file:///d:/SOLO-2/AI_solo_coder_task_B_151/backend/src/test/java/com/waterwheel/chaintransmission/controller/WaterwheelControllerNewFeaturesTest.java)

| 端点 | 测试场景 | HTTP状态 | 验证 |
|------|---------|---------|------|
| **POST /comparison/chain-types/{id}** | 带完整参数 | 200 | 输入输出映射正确 |
| **POST /comparison/chain-types/{id}** | 无可选参数 | 200 | 使用默认值 |
| **GET /comparison/chain-types/meta** | 元数据 | 200 | 3条记录 |
| **POST /comparison/eras/{id}** | 正常对比 | 200 | 2个时代结果 |
| **POST /comparison/eras/{id}** | 自定义缩放 | 200 | 参数正确传递 |
| **GET /comparison/eras/meta** | 元数据 | 200 | 2条记录 |
| **POST /optimization/parallel** | 3台BALANCED | 200 | 收敛+3个分配 |
| **POST /optimization/parallel** | MAX_FLOW目标 | 200 | 流量优先 |
| **POST /optimization/parallel** | 单设备 | 200 | 1个分配 |
| **POST /optimization/parallel** | 默认目标 | 200 | BALANCED |
| **POST /virtual-operation/{id}/step** | 正常操作 | 200 | NORMAL状态 |
| **POST /virtual-operation/{id}/step** | 高速警告 | 200 | WARNING+2条警告 |
| **POST /virtual-operation/{id}/step** | 重置会话 | 200 | 时间归零 |
| **GET /virtual-operation/{id}/state** | 获取状态 | 200 | 正确设备ID |
| **POST /virtual-operation/reset-all** | 全重置 | 200 | 清除计数 |
| **GET /health** | 健康检查 | 200 | v1.1.0 + 4个feature标识 |
| **OPTIONS** | CORS预检 | 200 | Access-Control-Allow-Origin头 |
| **异常** | 不存在设备 | 500 | 服务层异常透传 |
| **异常** | PUT不存在端点 | 405 | Method Not Allowed |
| **异常** | 空设备列表 | 500 | 服务层异常透传 |
| **异常** | 缺少必填参数 | 400 | Bad Request |

---

## 三、运行测试

### 命令
```bash
# 运行全部测试
mvn test

# 运行特定功能测试
mvn test -Dtest=ChainTypeComparisonServiceTest
mvn test -Dtest=EraComparisonServiceTest
mvn test -Dtest=ParallelCoordinationOptimizerTest
mvn test -Dtest=VirtualOperationServiceTest
mvn test -Dtest=WaterwheelControllerNewFeaturesTest

# 运行测试并生成覆盖率报告
mvn test jacoco:report
```

### 测试配置
- 使用 H2 内存数据库，模式为 PostgreSQL 兼容模式
- 所有数据库交互通过 Mockito 模拟，测试不依赖外部容器
- 测试隔离：每个 @Nested 类之间会话/状态自动重置
- Mock 回滚：每次测试后 Mockito 自动重置 mock 对象

### 关键测试约定
1. **数值容差**：物理量比较使用 `TestDataFactory.TOLERANCE = 1e-4`
2. **命名约定**：`should_预期行为_when_给定条件`
3. **三维验证**：每个功能必须覆盖 **正常→边界→异常** 三个维度
4. **数学一致性**：所有物理公式必须有数学验证用例
5. **性能基线**：交互类操作 < 50ms，优化类操作 < 5s
