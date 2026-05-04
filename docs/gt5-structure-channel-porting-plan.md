# GT5 信道系统移植计划书（已归档）

归档日期：2026-05-04

## 归档说明

本文件原本是 GT5 结构信道系统的独立移植计划，现已并入统一执行计划：

- `docs/multiblock-refactor.md`

后续所有执行任务、优先级、验收标准和风险清单，都以 `multiblock-refactor.md` 为准。

## 为什么归档

GT5 信道移植与多方块结构系统重构存在大量交叉，继续维护两份计划会导致任务重复和优先级冲突。

主要交叉点：

| GT5 信道计划内容 | 统一计划中的位置 |
|------------------|------------------|
| `StructureChannel` / `GTStructureChannels` | M3：结构信道 registry 与值模型 |
| legacy key alias、indicator item | M3：结构信道 registry 与值模型 |
| `DeclarativePatternBuilder.withChannel` | M4：多方块结构定义消费信道 |
| JEI 信道调节与材料列表刷新 | M5：JEI 信道 parity |
| 投影仪 channel values 持久化 | M6：投影仪 parity |
| 预览、compare、autoBuild 共用同一份 channel request | M6：投影仪 parity |
| GT5 样例机器迁移 | M4 / M5 / M8 |

## 保留用途

本归档文件只用于说明历史来源。GT5 源码调研结论已经整理进统一计划中的以下章节：

- “结构信道统一模型”
- “GT5 重点信道映射”
- “M3：结构信道 registry 与值模型”
- “M4：多方块结构定义消费信道”
- “M5：JEI 信道 parity”
- “M6：投影仪 parity”
- “信道专项测试”

## 后续规则

- 不再向本文件追加执行计划。
- 如需更新 GT5 信道迁移任务，直接修改 `docs/multiblock-refactor.md`。
- 如需保留更详细的源码摘录，应新建独立参考文档，而不是恢复本文件为执行计划。
