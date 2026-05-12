# 投影预览渲染重构计划书

## 一、现状分析

### 1.1 当前架构

两个入口共用同一套渲染管线：

```
┌──────────────────────────────┐
│  控制器 Shift+右键（空手）     │  → renderMultiBlockPreview(controller, 60000)
│  投影仪 右键                  │  → renderMultiBlockPreview(multiblock, 10000)
└──────────────┬───────────────┘
               ▼
    MultiblockPreviewRenderer.rebuildMultiblockPreview()
               │
               ▼
    glNewList(GL_COMPILE)  ←── Display List 编译
      ├─ renderControllerInList()      // 主结构
      │    └─ BlockRendererDispatcher.renderBlock() × N  ← 完整MC渲染管线
      ├─ renderPieceInList()           // 分片结构
      │    └─ BlockRendererDispatcher.renderBlock() × N
      └─ computeComparisonFromController()  // 对比模式数据
    glEndList()
               │
    renderWorldLastEvent() 每帧:
      ├─ callList(opList)              // 重放 Display List
      └─ renderComparisonOverlay()     // 蓝/红框叠加
```

### 1.2 性能瓶颈

| 瓶颈点 | 原因 |
|--------|------|
| `BlockRendererDispatcher.renderBlock()` | 每个方块走完整 MC 渲染管线（模型查找→纹理绑定→顶点生成），发生在**重建时** |
| 4 层 `BlockRenderLayer` 各遍历一次 | 表面方块 × 4 次完整渲染 |
| 逐方块 `pushMatrix/translate/scale/popMatrix` | Display List 内录入大量矩阵操作，GPU 驱动优化困难 |
| `glNewList(GL_COMPILE)` | Display List 是 OpenGL 3.0 已废弃的 API，现代驱动兼容性差 |
| 重建频率高 | 每次 layer 动画变化（用户点击）都全量重建 |

> **注意**：Display List 编译后，每帧 `callList()` 本身很快。真正的瓶颈在于**重建开销**——每次点击/动画变化都要重新走完整渲染管线。

### 1.3 与 GT5 的差异

| 对比维度 | GT5 (StructureLib) | 当前项目 (自研) |
|----------|-------------------|-----------------|
| 渲染引擎 | StructureLib 内置渲染 | 自研 OpenGL Display List |
| 方块数据来源 | `IStructureDefinition` 字符模板 | `MultiblockShapeInfo` 的 `BlockInfo[][][]` |
| 虚拟世界 | StructureLib 内部处理 | `TrackedDummyWorld` |
| 方块渲染调用 | StructureLib 内部（简单几何体） | `BlockRendererDispatcher.renderBlock()`（完整MC管线） |
| 半透明效果 | StructureLib 控制 | `GL_CONSTANT_ALPHA` + `glBlendColor(1,1,1,0.6)` |
| 分层动画 | 无 | `layer % (maxY + 1)` 逐层展开 |
| 对比模式 | 无 | 蓝框=缺失，红框=错误 |
| 分片预览 | `piece` 参数 | `STRUCTURE_PIECE` channel |

**核心差异**：GT5 的 StructureLib 渲染简单半透明几何体，不走 Minecraft 方块渲染管线，因此性能极好。当前项目为了显示真实方块纹理，付出了巨大的重建性能代价。

---

## 二、目标架构

```
┌─────────────────────────────────────────────────────┐
│  控制器 Shift+右键（空手）                            │
│  → MultiblockPreviewRenderer.renderControllerPreview()│
│     └─ VBO 方案（真实纹理 + 现代 OpenGL）             │
├─────────────────────────────────────────────────────┤
│  投影仪 右键                                         │
│  → GhostBlockRenderer.renderGhostPreview()           │
│     └─ 轻量幽灵方块（简单几何体 + 半透明色块）         │
└─────────────────────────────────────────────────────┘
```

---

## 三、任务拆分

### 任务 1：提取公共工具层（基础）

**目标**：将两个渲染方案共用的逻辑抽取为独立工具类，避免代码重复。

**涉及文件**：
- 新建 `GhostBlockRenderer.java` — 投影仪幽灵方块渲染器
- 新建 `PreviewRenderUtils.java` — 公共工具方法
- 改造 `MultiblockPreviewRenderer.java` — 保留 VBO 渲染，移除 Display List

**公共逻辑提取**：

| 功能 | 当前位置 | 目标位置 |
|------|---------|---------|
| `computeSurfaceBlocks()` | `MultiblockPreviewRenderer` | `PreviewRenderUtils` |
| `transformPreviewOffset()` | `MultiblockPreviewRenderer` | `PreviewRenderUtils` |
| `transformPieceOffset()` | `MultiblockPreviewRenderer` | `PreviewRenderUtils` |
| `getAxisComponent()` | `MultiblockPreviewRenderer` | `PreviewRenderUtils` |
| `TargetBlockAccess` | `MultiblockPreviewRenderer` 内部类 | `PreviewRenderUtils` 或独立文件 |
| `renderColoredBox()` | `MultiblockPreviewRenderer` | `PreviewRenderUtils` |
| `renderComparisonOverlay()` | `MultiblockPreviewRenderer` | `PreviewRenderUtils` |
| `computeComparisonData()` | `MultiblockPreviewRenderer` | `PreviewRenderUtils` |
| `computeComparisonFromController()` | `MultiblockPreviewRenderer` | `PreviewRenderUtils` |

**状态管理**：两个渲染器各自维护独立状态，不共享。
- `MultiblockPreviewRenderer`：保留 `mbpPos`, `mbpEndTime`, `layer`, `channelValues` + 新增 `VertexBuffer[] vbos`
- `GhostBlockRenderer`：独立的 `ghostPos`, `ghostEndTime`, `compareMode`, `channelValues` + `VertexBuffer ghostVbo`

> ⚠️ 不再新建 `PreviewState.java`。两个渲染器互斥（同一时间只能有一个活跃预览），通过各自的 `reset()` 方法清理对方状态。

---

### 任务 2：控制器预览 → VBO 方案

**目标**：将 `MultiblockPreviewRenderer` 从 Display List 迁移到 VBO（Vertex Buffer Object），参考已有的 `StructureVBO.java`。

**改造对比**：

```
改造前（Display List）:
  opList = GLAllocation.generateDisplayLists(1);
  glNewList(opList, GL11.GL_COMPILE);
    for BlockRenderLayer:
      buff.begin(GL_QUADS, BLOCK)
      for surfaceBlocks:
        brd.renderBlock(state, pos, targetBA, buff)  // 完整MC管线
      tes.draw()
  glEndList()

  // 每帧:
  callList(opList)

改造后（VBO）:
  for BlockRenderLayer:
    buff.begin(GL_QUADS, BLOCK)
    for surfaceBlocks:
      FaceCulledRenderBlocks.renderBlock(state, pos, visibility, buff)  // 面剔除+亮度
    buff.finishDrawing()
    vbo = new VertexBuffer(DefaultVertexFormats.BLOCK)
    vbo.bufferData(buff.getByteBuffer())
    vbos[layer.ordinal()] = vbo

  // 每帧:
  for BlockRenderLayer:
    vbo.bindBuffer()
    setupVertexPointers()
    vbo.drawArrays(GL_QUADS)
    vbo.unbindBuffer()
```

**关键改动点**：

| 项目 | 改造前 | 改造后 |
|------|--------|--------|
| 几何存储 | `int opList` (Display List) | `VertexBuffer[] vbos` (VBO 数组) |
| 方块渲染 | `BlockRendererDispatcher.renderBlock()` | `FaceCulledRenderBlocks.renderBlock()` |
| 面剔除 | `computeSurfaceBlocks()` 粗粒度 | `FaceVisibility` 细粒度逐面剔除 |
| 亮度 | `disableLighting()` 全亮 | `FULL_BRIGHT = 15728880` 逐顶点 |
| 编译 | `glNewList(GL_COMPILE)` | `buffer.finishDrawing()` + `vbo.bufferData()` |
| 每帧绘制 | `callList(opList)` | `vbo.bindBuffer()` + `vbo.drawArrays()` |
| 销毁 | `glDeleteLists(opList, 1)` | `vbo.deleteGlBuffers()` |

**保留功能**：
- ✅ 分层动画（`layer % (maxY + 1)`）
- ✅ 对比模式（蓝框缺失 / 红框错误）
- ✅ 分片预览（`STRUCTURE_PIECE` channel）
- ✅ 半透明全息效果（`GL_CONSTANT_ALPHA` blending）
- ✅ 0.75x 缩放 + 0.125 偏移（需顶点级变换，见下方说明）

**⚠️ VBO 缩放方案关键差异**：

当前 Display List 方案对每个方块做逐块矩阵变换（`translate + scale`），VBO 不能这样做。

解决方案：在构建 VBO 顶点数据时，将缩放和偏移直接烘焙到顶点坐标中：
```java
// 当前 Display List 方式（逐方块 GL 矩阵）：
GlStateManager.translate(tPos.getX(), tPos.getY(), tPos.getZ());
GlStateManager.translate(0.125, 0.125, 0.125);
GlStateManager.scale(0.75, 0.75, 0.75);
brd.renderBlock(state, BlockPos.ORIGIN, targetBA, buff);

// VBO 方式（顶点数据级烘焙）：
// putPosition 时不再是 pos，而是 pos * 0.75 + tPos + 0.125
// 需要扩展 FaceCulledRenderBlocks 或新建 ScaledRenderBlocks：
//   renderPos = new BlockPos(0, 0, 0);  // 模型空间渲染在原点
//   putPosition(tPos.getX() + 0.125 + vertex.x * 0.75,
//               tPos.getY() + 0.125 + vertex.y * 0.75,
//               tPos.getZ() + 0.125 + vertex.z * 0.75);
```

或者放弃逐方块缩放，改为整体 `glScale(0.75)` + 调整 VBO 渲染前的 translate。
GodForge 的 StructureVBO 方块是紧密排列不留间隙的，所以预览方案可以选择：
- 方案 A：不缩放，方块紧密排列（参考 GodForge），通过半透明效果区分预览
- 方案 B：对 BakedQuad 顶点数据做后处理缩放（需要修改 `renderQuads()`）

**复用已有代码**：
- `FaceCulledRenderBlocks` — 已存在于 `godforge/util/`，可直接复用（需要传入 `TrackedDummyWorld` 作为 `IBlockAccess`，而非 `StructureBlockAccess`）
- `FaceVisibility` — 已存在，可直接复用
- `StructureVBO` 的 `render()` 方法 — 参考其 VBO 绑定/绘制模式

**⚠️ `IBlockAccess` 适配**：
`FaceCulledRenderBlocks` 构造函数接收 `IBlockAccess`。`StructureVBO` 使用的是基于 `String[][]` + `char mapper` 的 `StructureBlockAccess`，但当前预览使用的是基于 `Map<BlockPos, BlockInfo>` 的 `TrackedDummyWorld`。需要确保 `TrackedDummyWorld` 实现了 `getActualState()` 和 `getExtendedState()` 不报错。

---

### 任务 3：投影仪预览 → 轻量幽灵方块方案

**目标**：新建 `GhostBlockRenderer`，用简单半透明几何体替代完整方块渲染。

**核心思路**：不调用 `BlockRendererDispatcher`，直接用 `BufferBuilder` 画带颜色的半透明立方体面。

**渲染流程**：

```
GhostBlockRenderer.renderGhostPreview(controller, durTimeMillis)
  │
  ├─ 1. 获取结构数据
  │     shapeInfo = controller.getMatchingShapes(channelValues).get(0)
  │     blocks = shapeInfo.getBlocks()
  │
  ├─ 2. 计算世界坐标方块列表
  │     for each BlockInfo in blocks:
  │       worldPos = controller.getPos() + transformPreviewOffset(relPos)
  │       ghostBlocks.add(worldPos, tintColor)
  │
  ├─ 3. 构建 VBO（一次性）
  │     for BlockRenderLayer (只需 SOLID 一层):
  │       buff.begin(GL_QUADS, POSITION_COLOR)
  │       for each ghostBlock:
  │         renderGhostCube(buff, pos, color, alpha)
  │       buff.finishDrawing()
  │       vbo.bufferData(...)
  │
  └─ 4. 每帧渲染（renderWorldLastEvent）
        disableTexture2D()     // 纯色几何体不需要纹理
        setupBlending(0.4 alpha)
        vbo.bindBuffer() → drawArrays() → unbindBuffer()
        enableTexture2D()
```

**幽灵方块外观参数**：

| 参数 | 值 | 说明 |
|------|-----|------|
| 方块大小 | 0.9 × 0.9 × 0.9 | 比实际方块略小，留间隙 |
| 边框线宽 | 1px | 可选线框模式 |
| 颜色 | 根据方块类型映射 | 例如：外壳=灰色，线圈=橙色，玻璃=浅蓝 |
| 透明度 | α = 0.4 | 半透明 |
| 面剔除 | 仅渲染表面方块 | 复用 `computeSurfaceBlocks()` |

**颜色映射策略**（二选一，待确认）：

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| A: 统一色 | 所有幽灵方块同一颜色（如淡蓝） | 最简单，性能最优 | 无法区分方块类型 |
| B: 分类色 | 根据 BlockInfo 分类着色 | 直观区分外壳/线圈/玻璃 | 需要维护映射表 |

**对比模式**（投影仪特有）：
- 幽灵方块模式下，对比模式改为：正确位置=绿色半透明，缺失=蓝色闪烁，错误=红色闪烁
- 复用 `PreviewRenderUtils.computeComparisonData()`

---

### 任务 4：入口分流

**目标**：让控制器和投影仪走不同的渲染入口。

**改动点**：

1. `MultiblockControllerBase.onRightClick()`（第1055行）
   ```java
   // 改造前
   MultiblockPreviewRenderer.renderMultiBlockPreview(this, 60000);
   // 改造后
   MultiblockPreviewRenderer.renderControllerPreview(this, 60000);  // VBO 方案
   ```

2. `MultiblockControllerBase.refreshPreviewOnClient()`（第1073行）
   ```java
   // 改造前
   MultiblockPreviewRenderer.refreshCurrentPreview(this);
   // 改造后（保持不变，VBO 版内部实现 refresh 逻辑）
   MultiblockPreviewRenderer.refreshCurrentPreview(this);
   ```

3. `StructureProjectorBehavior.onItemUseFirst()`（第252行）
   ```java
   // 改造前
   MultiblockPreviewRenderer.setCompareMode(compareMode);
   MultiblockPreviewRenderer.setChannelValues(channelValues);
   MultiblockPreviewRenderer.renderMultiBlockPreview(multiblock, 10000);
   // 改造后
   GhostBlockRenderer.setCompareMode(compareMode);
   GhostBlockRenderer.setChannelValues(channelValues);
   GhostBlockRenderer.renderGhostPreview(multiblock, 10000);  // 幽灵方块方案
   ```

4. `ClientEventHandler`（第91行）
   ```java
   // 改造前
   MultiblockPreviewRenderer.renderWorldLastEvent(event);
   // 改造后
   MultiblockPreviewRenderer.renderWorldLastEvent(event);  // VBO 渲染
   GhostBlockRenderer.renderWorldLastEvent(event);         // 幽灵方块渲染
   ```

**⚠️ 遗漏的重载方法**：
`renderMultiBlockPreview` 有 3 个重载需要全部处理：
- `renderMultiBlockPreview(controller, durTimeMillis)` — 第121行
- `renderMultiBlockPreview(controller, pos, durTimeMillis)` — 第173行
- `renderMultiBlockPreview(controller, pos, layer, durTimeMillis)` — 第192行

所有重载都需要改造为 VBO 版本或标记 deprecated 并转发。

---

### 任务 5：清理与兼容

| 项目 | 操作 |
|------|------|
| `opList` (Display List) | 删除，替换为 `VertexBuffer[]` |
| `GLAllocation.generateDisplayLists()` | 删除 |
| `glNewList` / `glEndList` / `callList` | 删除 |
| `BlockRendererDispatcher.renderBlock()` 调用 | 替换为 `FaceCulledRenderBlocks` |
| `resetMultiblockRender()` | 改为 `deleteVBOs()` |
| `renderControllerInList()` (2个重载) | 重命名为 `buildControllerVBO()` |
| `renderPieceInList()` | 重命名为 `buildPieceVBO()` |
| `renderMultiBlockPreview()` (3个重载) | 保留为 deprecated，内部转发到新方法 |
| `transformPieceOffset()` | 移至 `PreviewRenderUtils` |
| 逐方块 `pushMatrix/translate/scale/popMatrix` | 替换为顶点级坐标烘焙 |

---

## 四、文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `MultiblockPreviewRenderer.java` | **改造** | Display List → VBO，提取公共逻辑 |
| `GhostBlockRenderer.java` | **新建** | 投影仪幽灵方块渲染器 |
| `PreviewRenderUtils.java` | **新建** | 公共工具方法（坐标变换、面计算、对比数据） |
| `MultiblockControllerBase.java` | **微调** | 入口改为 `renderControllerPreview()` |
| `StructureProjectorBehavior.java` | **微调** | 入口改为 `GhostBlockRenderer.renderGhostPreview()` |
| `ClientEventHandler.java` | **微调** | 注册两个 `renderWorldLastEvent` |

---

## 五、执行顺序

```
任务1（公共层提取）→ 任务2（VBO改造）→ 任务3（幽灵方块）→ 任务4（入口分流）→ 任务5（清理）
```

---

## 六、待确认问题

在开始前需要确认：

1. **幽灵方块颜色方案**：选 A（统一色）还是 B（分类色）？
2. **幽灵方块是否需要线框**：纯半透明面，还是带边框线？
3. **是否需要分层动画**：幽灵方块也逐层展开，还是一次性显示全部？
4. **投影仪是否保留对比模式**：幽灵方块 + 蓝红叠加？
5. **VBO 缩放方案**：选方案 A（不缩放，紧密排列）还是方案 B（顶点级缩放，留间隙）？