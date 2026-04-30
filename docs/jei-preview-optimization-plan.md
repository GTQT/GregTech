# JEI 多方块预览性能优化计划

## 现状分析

### 当前渲染管线

```
MultiblockInfoRecipeWrapper.render()
  └─ WorldSceneRenderer.render(x, y, w, h, mouseX, mouseY)
       ├─ setupCamera()              — 设置 GL 投影/视图矩阵
       ├─ drawWorld()
       │    ├─ renderBlockLayer() x4  — 4个渲染层（Solid/Cutout/CutoutMipped/Translucent）
       │    │    └─ 遍历 renderedBlocks, 调用 blockrendererdispatcher.renderBlock() [瓶颈1]
       │    ├─ renderTileEntities()   — 遍历 TILE_ENTITIES, 调用 dispatcher.render()  [瓶颈2]
       │    └─ afterRender()          — 高亮覆盖层
       ├─ unProject(mouseX, mouseY)   — glReadPixels (GPU→CPU同步)                    [瓶颈3]
       ├─ rayTrace(hitPos)            — world.rayTraceBlocks()
       └─ resetCamera()
```

### 渲染器类型

| 渲染器 | 方块渲染 | TileEntity | 使用场景 |
|--------|----------|------------|---------|
| `ImmediateWorldSceneRenderer` | 每帧重绘 | 每帧TESR | 旧代码默认 |
| `VBOWorldSceneRenderer` | VBO缓存(仅dirty时重建) | 每帧TESR | 当前JEI使用 |
| `FBOWorldSceneRenderer` | VBO+FBO离屏渲染 | 每帧TESR | 未使用 |

### 性能瓶颈（按严重程度排序）

| # | 瓶颈 | 影响 | 复杂度 |
|---|------|------|--------|
| 1 | **TileEntity TESR 每帧渲染** | 每个GT MetaTileEntity都有TESR（管道贴图、覆盖板等），500+个TE会导致严重掉帧 | 高 |
| 2 | **VBO重建（初始化/dirty时）** | 遍历所有方块位置调用 renderBlock()，大结构初始化时卡顿数秒 | 中 |
| 3 | **glReadPixels GPU同步** | 每帧从GPU回读深度缓冲区做鼠标命中检测，强制GPU-CPU同步 | 低 |
| 4 | **初始化收集** | initializePattern() 创建所有BlockInfo/TileEntity/ItemStack | 中 |
| 5 | **afterRender高亮** | CodeChickenLib的renderBlockOverLay，每帧绘制 | 低 |

### GTM (LDLib) 对比

GTM 使用 LDLib 的 `WorldSceneRenderer`，核心差异：

| 方面 | GTCEu 1.12 (当前) | GTM/LDLib (1.20+) |
|------|-------------------|-------------------|
| 方块渲染 | VBO（4层分别draw） | VBO + 合并顶点（section-based） |
| TESR | 每帧逐个渲染所有TE | **过滤：仅渲染 IFastRenderMetaTileEntity**，且有距离剔除 |
| 鼠标命中 | glReadPixels + gluUnProject + rayTrace | 纯数学 ray-AABB 测试（不依赖GL） |
| 初始化 | 全量一次性 | 支持分批/惰性加载 |
| 内部方块 | 全部渲染 | 面剔除（被遮挡面不生成顶点） |

---

## 优化方案

### Phase 1: TESR 优化（效果最大，改动最小）

**目标：** 减少 90%+ 的 TESR 调用

#### 1.1 TESR 白名单过滤

当前 `addRenderedBlocks()` 中已有过滤：
```java
if (tile != null && (!(tile instanceof IGregTechTileEntity gtte) ||
        gtte.getMetaTileEntity() instanceof IFastRenderMetaTileEntity)) {
    TILE_ENTITIES.put(pos, tile);
}
```
但这只过滤了非 FastRender 的 MTE。实际上在 JEI 预览场景中，**大部分 TESR 完全不需要渲染**（管道覆盖板、仓室前面板等视觉效果不影响结构展示）。

**改动：**
- 在 `WorldSceneRenderer` 中添加 `setTileEntityRenderLimit(int maxTEs)` — 超过阈值时跳过 TESR
- 或添加 `setTileEntityFilter(Predicate<TileEntity>)` — 自定义过滤
- 在 `MultiblockInfoRecipeWrapper` 中设置过滤：只渲染控制器的 TESR

**预期效果：** 帧率提升 50-80%

#### 1.2 TESR 距离剔除

对于超大结构，即使保留部分 TESR，也应该基于相机距离剔除远处的 TE：

```java
protected void renderTileEntities() {
    double maxDistSq = MAX_TESR_RENDER_DIST * MAX_TESR_RENDER_DIST;
    TILE_ENTITIES.forEach((pos, tile) -> {
        double distSq = pos.distanceSq(eyePos.x, eyePos.y, eyePos.z);
        if (distSq < maxDistSq && tile.shouldRenderInPass(finalPass)) {
            dispatcher.render(tile, ...);
        }
    });
}
```

**预期效果：** 额外 20-30% 提升

---

### Phase 2: 鼠标命中检测优化

**目标：** 消除 glReadPixels GPU 同步

当前方案：每帧调用 `glReadPixels` → `gluUnProject` → `world.rayTraceBlocks()`
- `glReadPixels` 强制 GPU pipeline flush，是 OpenGL 中最慢的操作之一

**替代方案：CPU 侧 Ray-AABB 测试**

```java
public RayTraceResult rayTraceBlocks(int mouseX, int mouseY, int x, int y, int w, int h) {
    // 1. 从屏幕坐标计算射线方向（纯数学，不需要 GL）
    Vec3d rayOrigin = getCameraPosition();
    Vec3d rayDir = screenToWorldRay(mouseX, mouseY, x, y, w, h);
    
    // 2. 遍历 renderedBlocks 做 Ray-AABB 测试
    double closestDist = Double.MAX_VALUE;
    BlockPos closestPos = null;
    for (BlockPos pos : renderedBlocks) {
        if (world.isAirBlock(pos)) continue;
        double dist = rayIntersectsAABB(rayOrigin, rayDir, pos);
        if (dist >= 0 && dist < closestDist) {
            closestDist = dist;
            closestPos = pos;
        }
    }
    return closestPos != null ? new RayTraceResult(...) : null;
}
```

**优化：** 使用空间索引（八叉树或分区哈希）加速射线测试，将 O(n) 降为 O(log n)。

但对于 JEI 预览，更简单的方案是：**降低命中检测频率**，不需要每帧检测，每 3-5 帧检测一次即可。

**改动：**
- 添加 `hitTestInterval` 字段（默认=3帧）
- `render()` 中只在 `frameCount % hitTestInterval == 0` 时做命中检测
- 保留上次结果用于中间帧

**预期效果：** 帧率稳定性提升，消除 GPU 管线同步卡顿

---

### Phase 3: 大结构 LOD（Level of Detail）

**目标：** 超大结构（>1000方块）降低渲染复杂度

#### 3.1 内部方块剔除

对于 3x3x3 以上的实心区域，内部被完全遮挡的方块不需要渲染。

**算法：**
```
对于每个方块位置 pos:
  如果 pos 的6个相邻位置都是非空方块:
    跳过渲染（被完全包围，不可见）
```

**改动：**
- 在 `addRenderedBlocks()` 后添加 `cullInternalBlocks()` 预处理
- 生成 `visibleBlocks` 子集（只包含至少一面暴露的方块）
- VBO 和 TESR 只处理 `visibleBlocks`

**预期效果：** 对于大型多方块（如 5x5x5+），渲染方块数减少 30-50%

#### 3.2 远距离简化渲染

当相机距离结构较远时（缩小视角），使用简化渲染：

| 距离 | 渲染模式 |
|------|---------|
| 近 | 完整方块纹理 + TESR |
| 中 | 只渲染方块纹理，跳过TESR |
| 远 | 只渲染外壳颜色方块（不加载纹理） |

**改动：**
- `drawWorld()` 中根据相机距离选择渲染策略
- 缩放时动态切换

---

### Phase 4: 初始化优化

**目标：** 消除切换结构预览时的卡顿

#### 4.1 异步 VBO 构建

当前 VBO 重建在 `isDirty` 时同步执行（卡主线程）。改为异步构建：

```
主线程：收集 BlockPos 列表 → 标记 dirty
后台线程：遍历方块，调用 renderBlock() 填充 BufferBuilder
主线程（下一帧）：上传 VBO 数据到 GPU
```

**注意：** MC 1.12 的 `BlockRendererDispatcher.renderBlock()` 不是线程安全的，需要对 DummyWorld 做快照。

**替代方案：** 分帧构建 — 每帧只处理 N 个方块（如 500个/帧），用 2-3 帧完成大结构的 VBO 构建。用户可以看到结构"逐渐出现"。

#### 4.2 预览缓存

对于同一个多方块的同一个结构变体（repetition组合），缓存 VBO 数据：
```java
Map<String, int[]> vboCache; // key = "multiblockId:shapeIndex:repetitions"
```

**预期效果：** 多次切换已浏览过的预览时零延迟

---

### Phase 5: FBO 离屏渲染（可选，最大化优化）

**目标：** 完全解耦预览渲染与主游戏帧率

当前项目已有 `FBOWorldSceneRenderer`，但未在 JEI 中使用。

**方案：**
- JEI 预览使用 FBO 渲染到纹理
- 主渲染只绘制一个贴图四边形（几乎零开销）
- FBO 渲染频率可以独立于主帧率（如 20fps）
- 只在相机移动/结构切换时重新渲染 FBO

**改动：**
- `MultiblockInfoRecipeWrapper` 中将 `VBOWorldSceneRenderer` 替换为 `FBOWorldSceneRenderer`
- 设置 FBO 分辨率（如 512x512 或根据 JEI 区域大小动态调整）
- 添加脏标记：只在需要时重渲染 FBO

**预期效果：** 预览渲染完全不影响主游戏帧率

---

## 实施优先级

| 阶段 | 改动量 | 性能提升 | 风险 | 推荐优先级 |
|------|--------|---------|------|-----------|
| Phase 1.1: TESR过滤 | 小(~20行) | ★★★★★ | 低 | **P0 - 立即** |
| Phase 1.2: TESR距离剔除 | 小(~15行) | ★★★ | 低 | **P0 - 立即** |
| Phase 2: 命中检测降频 | 小(~10行) | ★★★ | 低 | **P1 - 紧随** |
| Phase 3.1: 内部方块剔除 | 中(~50行) | ★★★★ | 低 | **P1 - 紧随** |
| Phase 4.1: 分帧VBO构建 | 中(~80行) | ★★★ | 中 | **P2 - 后续** |
| Phase 4.2: 预览缓存 | 中(~60行) | ★★★ | 低 | **P2 - 后续** |
| Phase 3.2: 远距离简化 | 大(~100行) | ★★ | 中 | **P3 - 可选** |
| Phase 5: FBO离屏渲染 | 大(~150行) | ★★★★ | 高 | **P3 - 可选** |

---

## 参考：GT5 NEI vs 当前 JEI 对比

| 特性 | GT5 NEI (structurelib) | 当前 JEI |
|------|----------------------|---------|
| 渲染方式 | Display List / 简化面渲染 | VBO + TESR |
| TileEntity | 完全跳过 | 逐个渲染 |
| 方块面 | 只渲染可见面 | 渲染所有面 |
| 鼠标交互 | 无/简单 | 每帧 GL 命中检测 |
| 初始化 | 按需惰性加载 | 全量同步加载 |
| 大结构支持 | 分片渲染（按 piece） | 全部一次性渲染 |

GT5 之所以不卡，核心原因是**完全不渲染 TileEntity TESR**，只画方块面。这对应我们的 Phase 1 优化。

---

## 参考：GTM/LDLib 优化特性

GTM 通过 LDLib 的 `WorldSceneRenderer` 实现了以下优化：

1. **Section-based 渲染** — 将方块按 16x16x16 chunk section 分组，类似游戏内 chunk 渲染
2. **面剔除** — 被相邻方块遮挡的面不生成顶点数据
3. **TESR 过滤** — 只渲染实现了 `IFastRenderMetaTileEntity` 的 TE，其余跳过
4. **纯数学命中检测** — 不依赖 glReadPixels，用射线-AABB 测试
5. **延迟初始化** — 首次显示时才构建渲染数据

这些优化中，1 和 2 是 MC 1.20+ 渲染管线带来的自然优势（MC 重写了渲染系统），3-5 是 LDLib 的主动优化。我们的 Phase 1-4 覆盖了 3-5 的等价实现。
