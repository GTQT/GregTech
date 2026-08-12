# 无线算力网络系统 — 预研设计（云计算数据仓版）

> 状态：预研草案（尚未实现）
> 范围（已确认）：**只新增"云计算数据仓"系列（细分上行/下行）+ 无线算力服务系统。**
> 多方块（HPCA / DataBank / ResearchStation 等）侧**零修改**——结构定义、配方逻辑、
> 能力接口均不动。
> 相关文档：[wireless-network-channels.md](../wireless-network-channels.md)

---

## 0. 核心机制：为什么多方块能零改动

多块结构中的"算力舱槽位"是按 `MultiblockAbility` 类型声明的：

- `COMPUTATION_DATA_TRANSMISSION`（发射算力舱）—— HPCA 结构已有
  `hatch(MultiblockAbility.COMPUTATION_DATA_TRANSMISSION, 1, 1, ...)` 槽位（已证实）
- `COMPUTATION_DATA_RECEPTION`（接收算力舱）—— ResearchStation 等消费方结构已有

新做的云计算数据仓只要**复用这两个已有能力类型**（即实现 `IOpticalComputationHatch`
并注册 `MultiblockAbility.COMPUTATION_DATA_TRANSMISSION / _RECEPTION`），就能被
放进现有多方块的槽位——多方块代码一行不改。

因此全部新增代码收敛在两处：**仓（新 MTE 系列）** + **无线算力服务（服务端系统）**。

---

## 1. 类设计

```
gregtech.api.wireless（或独立 api 包）
  IWirelessComputationService
      void registerProvider(UUID actor, int channelId, String key,
                            IOpticalComputationProvider provider);   // 上行仓注册
      void unregisterProvider(UUID actor, int channelId, String key);
      void heartbeat(UUID actor, int channelId, String key, long gameTime); // 续期
      int requestCWUt(UUID actor, int channelId, int cwut, boolean simulate,
                      Collection<IOpticalComputationProvider> seen); // 下行仓请求
      WirelessComputationView getView(UUID actor, int channelId);
  WirelessComputationView        // 只读快照: maxCWUt / 已分配 / CWU/s / 节点数

gregtech.common.wireless（compute 子包）
  WirelessComputationServiceImpl  // 生命周期 + 单例（仿 WirelessEnergyServiceImpl）
  WirelessComputationSavedData    // 独立 WorldSavedData
  WirelessComputationChannel      // 每队伍每信道的注册池 + 速率统计
      Map<String, ComputationNode> nodes;
      int allocatedThisTick;                 // tick 内已承诺（防超卖兜底）
      BigInteger cwAllocatedPerSecond;       // 20t 滚动统计
  ComputationNode                 // key/type/pos/dimension/lastSeen + provider 活引用
  WirelessTeamResolver            // ★ 直接复用

机器侧（新 MTE 系列）
  MetaTileEntityCloudComputationHatch(tier, isUplink)
    - isUplink=true （上行仓，挂算力提供方，如 HPCA）
        能力: COMPUTATION_DATA_TRANSMISSION
        实现 IOpticalComputationHatch: isTransmitter()==true 语义
        成型时: controller instanceof IOpticalComputationProvider →
                service.registerProvider(owner, channelId, key, provider)
        每 20t: service.heartbeat(...) 续期
        失效/拆除: service.unregisterProvider(...)
        自身 requestCWUt → 透传 controller.requestCWUt（无线系统作为唯一入口调用，
        透传同一 seen 集合，防环）
    - isUplink=false（下行仓，挂算力消费方，如 ResearchStation）
        能力: COMPUTATION_DATA_RECEPTION
        实现 IOpticalComputationHatch: isTransmitter()==false 语义
        requestCWUt(cwut, simulate, seen) →
            service.requestCWUt(owner, channelId, cwut, simulate, seen)
            （cap CWT[tier]，仿光学接收舱 maxComputation()）
        NBT 持久化 channelId；信道缺失回退 0（仿 WirelessEnergyHatch）
```

## 2. 核心算法：聚合 requestCWUt

```java
// WirelessComputationServiceImpl 聚合（下行仓 → 无线服务 → 上行仓链 → HPCA）
int requestCWUt(UUID actor, int channelId, int cwut, boolean simulate,
                Collection<IOpticalComputationProvider> seen) {
    WirelessComputationChannel ch = resolveChannel(actor, channelId);
    if (ch == null) return 0;
    int remaining = cwut;
    for (ComputationNode node : ch.getNodesInAllocationOrder()) {   // 注册序/优先级序
        if (remaining <= 0) break;
        // 上行仓透传同一 seen → HPCA 自身 per-tick 记账防超卖，与光学体系同构
        remaining -= node.provider.requestCWUt(remaining, simulate, seen);
    }
    return cwut - remaining;
}
```

- **simulate 一致性**：不加自定义簿记，完全依赖底层 HPCA `allocateCWUt(simulate)`
  的 tick 级记账 + 上行仓透传同一 `seen` 集合（无线服务不参与 seen，节点链本身
  已防环：每个 HPCA 只会被其上行仓访问一次）。
- **多下行仓竞争**：同一信道多接收者同 tick 请求 → 上行仓按注册序依次拿，
  由 HPCA 上限兜底；不足的接收者表现为"算力不足"（配方逻辑原样处理）。
- **节点可达性**：上行仓所在区块卸载/结构失效 → `requestCWUt` 返回 0 或透传 0，
  行为与光学断线一致。

## 3. 复用映射

| 能源系统 | 算力系统映射 | 改动 |
|---|---|---|
| `WirelessTeamResolver` | 直接复用 | 零 |
| `WirelessEnergyServiceImpl` 生命周期 | `WirelessComputationServiceImpl` | 仿写 |
| `WirelessEnergySavedData` | `WirelessComputationSavedData` | 仿写 |
| `WirelessEnergyChannel`（余额+统计） | `WirelessComputationChannel`（节点池+分配统计） | 仿写 |
| `WirelessEndpointRecord` | `ComputationNode` | 仿写 |
| `MetaTileEntityWirelessEnergyHatch`（信道持久化/回退/同步） | `MetaTileEntityCloudComputationHatch` | 仿写 |
| `MetaTileEntityComputationHatch`（光学舱：能力/限流/透传） | 上行/下行仓结构 | 仿写 |
| Flux GUI 组件 / 状态数据包模式 / HUD | 复用（按需） | 零 |
| `/gt wireless` | `/gt wireless compute`（info/cleanup） | 仿写 |

## 4. 决策点（已拍板）

| # | 决策 | 结论 |
|---|---|---|
| 1 | tier 范围 | **全 16 档**（ULV~MAX），与光学算力舱同模式 |
| 2 | 上行仓信道选择 | 上下行仓 GUI **照抄无线能量舱**（6 页签结构），信道选择/NBT 持久化/缺失回退 0 |
| 3 | 纹理 | **沿用现有**（`Textures.OPTICAL_DATA_ACCESS_HATCH`，光学算力舱同款） |
| 4 | 信道容量上限 | **= Σ 已注册上行仓 `CWT[tier]`**（注册池求和，无单独公式） |
| 5 | 节点持久化 | **不持久化**——世界重启后上行仓 `update()` 自动重注册，SavedData 仅存信道统计 |
| 6 | 距离/损耗 | **无限、无损耗**（与能源一致，不做任何惩罚机制） |
| 7 | GUI/客户端 | 舱 GUI 照抄无线能量舱；HUD/命令留 M3 按需 |

## 5. 里程碑

- **M1 服务端核心 ✅**：`WirelessComputationSavedData` + `WirelessComputationChannel`
  注册池 + 聚合 `requestCWUt` + 注册/心跳/注销/过期清理生命周期；
  `WirelessComputationServiceImpl` 已注册进 CoreModule 事件总线。
- **M2 仓体 ✅**：`MetaTileEntityCloudComputationHatch` 上行/下行各 15 档
  （ULV~MAX，ID 3146+/3162+），复用 `COMPUTATION_DATA_TRANSMISSION/RECEPTION`
  能力槽位（多方块零改动）；信道持久化/缺失回退；GUI 照抄无线能量舱 6 页签
  （"无线充电"页替换为信道信息页，无 transfer 按钮——CWU 不可传输）；
  lang 中英文案齐全。
- **M3 客户端 ✅**：算力 HUD（`ClientWirelessComputationHUD` +
  `WirelessComputationHudRenderer`，照抄能源 HUD：文字块/利用率渐变条/5m·1h 差值/
  折线图）+ 数据包 `CPacketRequestComputationInfo`/`SPacketWirelessComputationInfo`
  + `ConfigHolder.WirelessComputationHud`（位置/开关/显示项，与能源 HUD 同结构）
  + `/gt wireless compute info/cleanup` 命令。
- **M4 平衡（按需）**：信道上限已定为 Σ 上行 `CWT[tier]`（自动生效），无需公式。

### 实现时新增/修改的文件

新增：`IWirelessComputationService`、`WirelessComputationView`、`ChannelInfo`（api）；
`ComputationNode`、`WirelessComputationChannel`、`WirelessComputationNetwork`、
`WirelessComputationSavedData`、`WirelessComputationServiceImpl`、
`WirelessComputationChannelUi`（common）；`MetaTileEntityCloudComputationHatch`（MTE）。
修改：`CoreModule`（事件总线注册）、`MetaTileEntities`（数组声明）、
`MultiblockPartRegistration`（注册）、`FluxChannelListWidget`（改为消费
`ChannelInfo` 接口，协变通配符——能源侧调用零改动）、zh_cn/en_us lang。
