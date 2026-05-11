# 多方块结构系统统一重构计划书

更新时间：2026-05-09

## 总体状态

运行时系统（事件驱动、异步检查、分片检查、信道 API）**已稳定可用**。
剩余 **7 项**：渲染修复 2 项 + 功能收尾 3 项 + 迁移文档 2 项。

## 剩余问题

### P1 — 渲染修复

| # | 问题 | 涉及文件 |
|---|------|----------|
| 1 | 投影仪预览渲染全黑 | `MultiblockPreviewRenderer.java` |
| 2 | Forge of Gods 成型后无动画 | `MetaTileEntityForgeOfGods.java` |

### P2 — 功能收尾

| # | 问题 | 修复方案 | 涉及文件 |
|---|------|----------|----------|
| 3 | 预览层数与构建层数不一致 | 重新验证（可能已随其他修复解决） | `MultiblockState.calculateRepetitionsFromChannels` |
| 4 | NO_HATCH 放置空缺 | `skipHatches=true` 时用 casing candidate 替代跳过 | `MultiblockState.autoBuild()` |
| 5 | Indicator 注册不全（三钛线圈） | 补调 `registerIndicatorsFromGroup` | `GTCasingGroups` 初始化处 |

### P3 — 迁移收尾

| # | 问题 |
|---|------|
| 6 | `BlockPattern` 标记 `@ApiStatus.ScheduledForRemoval` + 版本号 |
| 7 | Addon 迁移指南（`BlockPattern` → `DeclarativePatternBuilder`） |

### P4 — 验证项

| # | 验证内容 |
|---|----------|
| 8 | 两个投影仪 per-ItemStack NBT 互不影响 |
| 9 | Forge of Gods 各朝向 + 局部 dirty piece 重检 |

## 执行计划

```text
Phase 1 — 渲染修复（最高优先级，可并行）
  T1. 投影仪预览渲染全黑 (#1)
  T2. Forge of Gods 成型动画 (#2)

Phase 2 — 功能收尾（可并行）
  T3. 验证预览层数一致性 (#3)
  T4. NO_HATCH 放置逻辑 (#4)
  T5. Indicator 注册补全 (#5)

Phase 3 — 验证 + 收尾
  T6. 投影仪 per-ItemStack 验证 (#8)
  T7. Forge of Gods 各朝向验收 (#9)
  T8. BlockPattern 废弃路径 (#6)
  T9. Addon 迁移指南 (#7)
```

T1-T5 无相互依赖；T6 依赖 T1；T8/T9 在 Phase 1+2 后执行。

## 完成标准

| # | 条件 | 当前 |
|---|------|------|
| 1 | 投影仪预览正确渲染 | ❌ |
| 2 | Forge of Gods 分片检查 + 成型动画 | ❌ |
| 3 | 预览层数与构建层数一致 | ⚠️ |
| 4 | NO_HATCH 放置纯外壳 | ❌ |
| 5 | Indicator 覆盖所有 casing group | ❌ |
| 6 | `BlockPattern` 有 `@ScheduledForRemoval` | ⬜ |
| 7 | Addon 迁移指南完成 | ⬜ |

已满足（不再跟踪）：编译、事件驱动、异步安全、AABB 优化、debounce、首次成形、信道 API、JEI、投影仪信道传递、legacy alias。