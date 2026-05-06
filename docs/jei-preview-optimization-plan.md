# JEI 多方块预览性能优化计划

> **最后更新:** 2026-05-06
> **状态:** Phase 1-3.1 已实施，Phase 4-6 待实施

---

## 实施进度总览

| Phase | 描述 | 状态 | 关键文件 |
|-------|------|------|----------|
| 1.1 | TESR 白名单过滤 | ✅ 已完成 | `WorldSceneRenderer.java` L166-193 |
| 1.2 | TESR 距离剔除 | ✅ 已完成 | `WorldSceneRenderer.java` L178-181, L425-429 |
| 2 | 命中检测降频 | ✅ 已完成 | `WorldSceneRenderer.java` L204-207, L241-255 |
| 3.1 | 内部方块剔除 | ✅ 已完成 | `WorldSceneRenderer.java` L114-123, L143-151 |
| 3.2 | 远距离简化渲染 | ❌ 未实施 | — |
| 4.1 | 分帧VBO构建 | ❌ 未实施 | — |
| 4.2 | 预览缓存 | ❌ 未实施 | — |
| 5 | FBO离屏渲染 | ❌ 未实施 | `FBOWorldSceneRenderer.java` 存在但未启用 |
| 6 (NEW) | 架构隐患修复 | ❌ 未实施 | `VBOWorldSceneRenderer.java`, `WorldSceneRenderer.java` |

---

## 当前架构分析

### 渲染管线（已优化后）

```
MultiblockInfoRecipeWrapper.drawInfo()
  └─ WorldSceneRenderer.render(x, y, w, h, mouseX, mouseY)
       ├─ setupCamera()
       ├─ drawWorld()  [VBOWorldSceneRenderer override]
       │    ├─ if (isDirty) uploadVBO()     — 同步重建 VBO（仅 dirty 时）
       │    ├─ draw VBO x4 layers           — 从缓存的 VBO 绘制
       │    ├─ renderTileEntities()         — ✅ 有数量限制 + 距离剔除 + 自定义过滤
       │    └─ afterRender()                — 高亮覆盖层
       ├─ if (frameCount % hitTestInterval == 0)  — ✅ 降频命中检测
       │    ├─ unProject(mouseX, mouseY)    — glReadPixels (仅每N帧执行)
       │    └─ rayTrace(hitPos)
       └─ resetCamera()
```

### 当前配置策略（`initializePattern()` 中）

| 结构大小 | TESR 策略 | 命中检测间隔 | 内部剔除 |
|----------|-----------|-------------|---------|
| ≤ 50 方块 | 无限制（全部渲染） | 每帧 | 关闭 |
| 51-100 方块 | 最多8个TESR, 距离≤16 | 每3帧 | 开启 |
| > 100 方块 | 仅控制器TESR | 每5帧 | 开启 |

### 已发现的架构隐患

#### 1. 静态 VBO 数组共享问题（严重）

```java
// VBOWorldSceneRenderer.java Line 28
protected static final VertexBuffer[] VBOS = new VertexBuffer[BlockRenderLayer.values().length];
```

**问题:** 所有 `VBOWorldSceneRenderer` 实例共享同一组 VBO。如果同时存在多个渲染器实例（如多个多方块预览缓存在 `MBPattern[]` 中），后初始化的会覆盖前面的 VBO 数据。当前因为每次只渲染一个活跃的 pattern 且切换时会 setDirty 重建，所以不会立即表现为渲染错误，但这阻碍了未来的 VBO 缓存优化（Phase 4.2）。

#### 2. 静态 TILE_ENTITIES Map 共享问题（中等）

```java
// WorldSceneRenderer.java Line 72
protected static final Map<BlockPos, TileEntity> TILE_ENTITIES = new Object2ObjectArrayMap<>();
```

**问题:** 与 VBO 相同，所有渲染器实例共享同一个 TE map。`addRenderedBlocks()` 会清空再重建，多实例切换时数据被覆盖。

#### 3. setNextLayer 触发不必要的 VBO 重建

```java
// MultiblockInfoRecipeWrapper.java Line 300-309
renderer.renderedBlocks.clear();
// ...filter by layer...
renderer.addRenderedBlocks(renderBlocks);  // → isDirty = true → VBO rebuild
```

**问题:** 切换层时每次都重建 VBO。对大结构来说，这会导致切换层时卡顿。
**更优方案:** 预构建所有层的 VBO，或通过 GL 裁剪平面实现层过滤（不需要重建 VBO）。

#### 4. FBOWorldSceneRenderer 缺少脏标记

当前 `FBOWorldSceneRenderer.render()` 每次调用都重渲染 FBO 内容，没有判断是否需要更新。如果要启用 FBO 方案，必须添加脏标记机制。

---

## 下一步优化方案（按优先级排序）

### Phase 6: 架构隐患修复（P0 - 阻塞后续优化）

**目标:** 消除静态共享问题，为缓存和 FBO 方案铺路

#### 6.1 VBO 实例化

将 `static final VertexBuffer[] VBOS` 改为实例字段：

```java
public class VBOWorldSceneRenderer extends ImmediateWorldSceneRenderer {
    private final VertexBuffer[] vbos = new VertexBuffer[BlockRenderLayer.values().length];
    // ...
}
```

**影响:** 每个渲染器拥有独立的 VBO，允许多实例共存和缓存。

#### 6.2 TILE_ENTITIES 实例化

将 `static final Map<BlockPos, TileEntity> TILE_ENTITIES` 改为实例字段：

```java
public abstract class WorldSceneRenderer {
    protected final Map<BlockPos, TileEntity> tileEntities = new Object2ObjectArrayMap<>();
    // ...
}
```

**影响:** 每个渲染器独立管理自己的 TE 集合。

#### 6.3 VBO 释放管理

添加 `dispose()` 方法用于释放 VBO 资源：

```java
public void dispose() {
    for (int i = 0; i < vbos.length; i++) {
        if (vbos[i] != null) {
            vbos[i].deleteGlBuffers();
            vbos[i] = null;
        }
    }
}
```

**预期改动量:** ~30 行
**风险:** 低（行为等价替换，只是从 static 变 instance）

---

### Phase 4: 初始化优化（P1）

#### 4.1 层切换优化（替代原有分帧方案）

**新方案: GL 裁剪平面**

利用 OpenGL 裁剪平面（glClipPlane）在不重建 VBO 的情况下实现层过滤：

```java
private void setNextLayer(int newLayer) {
    this.layerIndex = newLayer;
    if (newLayer == -1) {
        // Show all: disable clip plane
        renderer.setClipPlane(null);
    } else {
        // Clip to single layer using GL clip planes
        int minY = (int) world.getMinPos().getY();
        float y = minY + newLayer;
        renderer.setClipPlane(y, y + 1.0f);  // Only show blocks in [y, y+1)
    }
}
```

**优势:** 
- 切换层不重建 VBO（零延迟切换）
- VBO 始终包含完整结构数据
- 实现简单（~40行）

**注意:** 裁剪平面会裁剪 TESR 和方块高亮，需要额外处理。
**替代方案:** 如果裁剪平面处理复杂，可改用 per-layer VBO 预构建。

#### 4.2 预览缓存（依赖 Phase 6.1）

修复 VBO 静态共享问题后，可以缓存已构建的渲染器：

```java
// MBPattern already stores WorldSceneRenderer, just need to avoid isDirty on revisit
// The cache already exists via MBPattern[] patterns array
// Key fix: Don't clear+rebuild when switching back to a pattern that already has valid VBO
```

当前 `MBPattern[]` 数组已经为每个形状保存了独立的 `WorldSceneRenderer`，但由于 VBO 是 static 的，切换回来时 VBO 数据已丢失。修复 Phase 6.1 后，缓存自然生效。

**预期改动量:** Phase 6.1 完成后仅需 ~5 行（移除不必要的 isDirty 设置）

---

### Phase 5: FBO 离屏渲染（P2 - 可选但高收益）

**目标:** 完全解耦预览渲染与主游戏帧率

当前 `FBOWorldSceneRenderer` 已存在且功能完整，但缺少：

1. **脏标记机制** — 只在相机移动/结构切换时重渲染
2. **独立渲染频率** — FBO 可以以低于主循环的频率更新（如 20fps）
3. **与 VBO 的结合** — 当前 FBO 继承自 `WorldSceneRenderer`（Immediate 渲染），应改为继承 `VBOWorldSceneRenderer`

**改动清单:**

```java
public class FBOWorldSceneRenderer extends VBOWorldSceneRenderer {  // 改继承关系
    private boolean fboDirty = true;
    private int renderInterval = 3;  // 每3帧才真正渲染 FBO 一次
    private int fboFrameCount;
    
    public void markFBODirty() { this.fboDirty = true; }
    
    @Override
    public void render(float x, float y, float width, float height, int mouseX, int mouseY) {
        fboFrameCount++;
        if (fboDirty || fboFrameCount % renderInterval == 0) {
            // Render to FBO
            int lastID = bindFBO();
            super.render(0, 0, resolutionWidth, resolutionHeight, ...);
            unbindFBO(lastID);
            fboDirty = false;
        }
        // Always: draw FBO texture as quad (nearly free)
        drawFBOTextureQuad(x, y, width, height);
    }
}
```

**预期改动量:** ~80 行
**风险:** 中（需要处理 FBO 与主 GL 上下文的交互、鼠标坐标映射）

---

### Phase 3.2: 远距离简化渲染（P3 - 低优先级）

**评估:** 由于已实施的 TESR 过滤和内部剔除已覆盖大部分性能收益，远距离 LOD 的边际收益较低。仅在超大结构（>500方块）仍有明显卡顿时再考虑。

**如果实施:** 建议基于缩放级别动态调整 TESR 策略，而非实现完整的面简化：

| zoom 级别 | TESR 策略 |
|-----------|-----------|
| 近 (zoom < 8) | 按当前策略 |
| 中 (8-15) | 完全禁用 TESR |
| 远 (> 15) | 完全禁用 TESR + 跳过 Translucent 层 |

---

## 新的实施优先级

| 阶段 | 描述 | 改动量 | 性能/架构收益 | 风险 | 优先级 |
|------|------|--------|-------------|------|--------|
| ~~Phase 1.1~~ | ~~TESR过滤~~ | — | — | — | ~~✅ 已完成~~ |
| ~~Phase 1.2~~ | ~~TESR距离剔除~~ | — | — | — | ~~✅ 已完成~~ |
| ~~Phase 2~~ | ~~命中检测降频~~ | — | — | — | ~~✅ 已完成~~ |
| ~~Phase 3.1~~ | ~~内部方块剔除~~ | — | — | — | ~~✅ 已完成~~ |
| **Phase 6.1** | VBO 实例化 | 小(~20行) | 架构★★★★★ | 低 | **P0 - 立即** |
| **Phase 6.2** | TILE_ENTITIES 实例化 | 小(~15行) | 架构★★★★ | 低 | **P0 - 立即** |
| **Phase 4.1** | 层切换优化(裁剪平面) | 中(~40行) | 性能★★★ | 中 | **P1 - 紧随** |
| **Phase 4.2** | 预览缓存(依赖6.1) | 小(~5行) | 性能★★★★ | 低 | **P1 - 紧随** |
| **Phase 5** | FBO离屏渲染 | 大(~80行) | 性能★★★★★ | 中 | **P2 - 后续** |
| Phase 3.2 | 远距离简化 | 中(~30行) | 性能★★ | 低 | **P3 - 可选** |

---

## 已完成优化的代码位置

### WorldSceneRenderer.java

| 功能 | 位置 | 描述 |
|------|------|------|
| TESR 数量限制 | `maxTileEntityRenderers` 字段, L85 | `setMaxTileEntityRenderers(int)` |
| TESR 距离剔除 | `maxTileEntityRenderDistSq` 字段, L86 | `setMaxTileEntityRenderDistance(double)` |
| TESR 自定义过滤 | `tileEntityFilter` 字段, L87 | `setTileEntityFilter(Predicate<TileEntity>)` |
| 命中检测降频 | `hitTestInterval` / `frameCount`, L90-91 | `setHitTestInterval(int)` |
| 内部方块剔除 | `cullInternal` 字段, L94 | `setCullInternalBlocks(boolean)` + `isFullyEnclosed()` |
| 渲染TE时三重过滤 | `renderTileEntities()`, L407-441 | 数量→距离→自定义过滤器 |

### MultiblockInfoRecipeWrapper.java

| 功能 | 位置 | 描述 |
|------|------|------|
| 大结构TESR过滤 | `initializePattern()`, L831-842 | 按方块数量分档配置 |
| 内部剔除启用 | `initializePattern()`, L821-824 | >50方块时启用 |

---

## 参考：GT5 NEI vs 当前 JEI 对比

| 特性 | GT5 NEI (structurelib) | 当前 JEI (优化后) |
|------|----------------------|---------|
| 渲染方式 | Display List / 简化面渲染 | VBO + 受限 TESR |
| TileEntity | 完全跳过 | ✅ 过滤+限制+距离剔除 |
| 方块面 | 只渲染可见面 | ✅ 内部方块剔除 (block level) |
| 鼠标交互 | 无/简单 | ✅ 降频 glReadPixels (3-5帧/次) |
| 初始化 | 按需惰性加载 | 全量同步加载（待优化） |
| 大结构支持 | 分片渲染（按 piece） | 全部一次性渲染（待优化） |

### GTM/LDLib 优化特性对比

| LDLib 优化 | 当前实现状态 | 等价/差距 |
|-----------|------------|-----------|
| Section-based 渲染 | ❌ | MC 1.12 无原生支持，需自行实现 |
| 面剔除 | ⚠️ 方块级剔除 | 比面级剔除粒度粗，但实现简单 |
| TESR 过滤 | ✅ | 已实现，策略更激进（大结构仅控制器） |
| 纯数学命中检测 | ⚠️ 降频替代 | 未完全消除 glReadPixels，但频率降低了 |
| 延迟初始化 | ❌ | 仍为全量同步，待 Phase 4 |

---

## 附录：关键类继承关系

```
WorldSceneRenderer (abstract)
├─ ImmediateWorldSceneRenderer    — 每帧即时渲染方块+TE
│   └─ VBOWorldSceneRenderer      — 方块VBO缓存 + 每帧TE (当前JEI使用)
└─ FBOWorldSceneRenderer          — FBO离屏渲染 (当前未使用)
```

**注意:** `FBOWorldSceneRenderer` 直接继承 `WorldSceneRenderer`（使用 Immediate 方块渲染），如果启用 FBO 方案，应改为继承 `VBOWorldSceneRenderer` 以获得 VBO 缓存的方块渲染优势。
