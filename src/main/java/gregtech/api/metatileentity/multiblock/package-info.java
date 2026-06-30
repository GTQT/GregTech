/**
 * 多方块控制器 API — 结构匹配、能力管理和 CTM 渲染支持。
 *
 * <h2>getCasingBlock 使用指南</h2>
 * <p>
 * {@link gregtech.api.metatileentity.multiblock.MultiblockControllerBase#getCasingBlock()}
 * 和
 * {@link gregtech.api.metatileentity.multiblock.MultiblockControllerBase#getCasingBlock(IMultiblockPart)}
 * 是多方块 CTM（Connected Textures Mod，连接纹理）支持的核心入口。
 * CTM 在渲染时通过 {@code BlockMachine.getFacade()} 获取机器方块的"伪装视觉状态"，
 * 将多方块组件方块渲染为连续的 casing 方块，使纹理相互连接。
 * </p>
 *
 * <h3>两个方法的分工</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>调用场景</th><th>由谁覆写</th></tr>
 *   <tr>
 *     <td>{@code getCasingBlock()}</td>
 *     <td>控制器自身渲染、粒子纹理、带参版本的默认回退</td>
 *     <td>多变体类（接口型）需覆写；单变体类声明 {@code public static getCasingState()} 由反射自动发现</td>
 *   </tr>
 *   <tr>
 *     <td>{@code getCasingBlock(IMultiblockPart sourcePart)}</td>
 *     <td>组件方块渲染、CTM facade 查询、需要按位置/类型区分材质时覆写</td>
 *     <td>火室区分、数据仓区分等场景</td>
 *   </tr>
 * </table>
 *
 * <h3>模式 A：单变体 — 纯反射（如 EBF、CokeOven）</h3>
 * <p>
 * 适用于一个类对应一种多方块，所有组件使用同一种 casing 材质。
 * 只需声明 {@code public static IBlockState getCasingState()}，基类反射自动发现，无需覆写任何方法。
 * </p>
 * <pre>{@code
 * public class MetaTileEntityElectricBlastFurnace extends RecipeMapMultiblockController {
 *
 *     // 反射自动发现此方法 → getCasingBlock() 返回 INVAR_HEATPROOF
 *     public static IBlockState getCasingState() {
 *         return MetaBlocks.METAL_CASING.getState(MetalCasingType.INVAR_HEATPROOF);
 *     }
 * }
 * }</pre>
 *
 * <h3>模式 B：单变体 + 组件类型区分（如 AssemblyLine）</h3>
 * <p>
 * 基础材质通过反射获取，但某些特殊组件（如数据仓）需要不同材质。
 * 反射处理无参版本，带参版本覆写按组件类型返回不同 IBlockState。
 * </p>
 * <pre>{@code
 * public class MetaTileEntityAssemblyLine extends RecipeMapMultiblockController {
 *
 *     // 反射后备 — 普通外壳
 *     public static IBlockState getCasingState() {
 *         return MetaBlocks.METAL_CASING.getState(MetalCasingType.STEEL_SOLID);
 *     }
 *
 *     // 按组件类型区分 — 数据仓用炉排材质
 *     &#64;Override
 *     public IBlockState getCasingBlock(@Nullable IMultiblockPart sourcePart) {
 *         if (sourcePart instanceof IDataAccessHatch) {
 *             return getGrateState();
 *         }
 *         return getCasingState();
 *     }
 * }
 * }</pre>
 *
 * <h3>模式 C：多变体 — 接口型（如 LargeBoiler、FusionReactor、LargeTurbine 等）</h3>
 * <p>
 * 一个类通过接口/枚举承载多种多方块变体。反射方案不可行（静态方法只能返回一个值），
 * 必须覆写无参版本委托给类型接口。如需火室区分再覆写带参版本。
 * </p>
 * <pre>{@code
 * public class MetaTileEntityLargeBoiler extends MultiblockWithDisplayBase {
 *     public final IBoilerType boilerType;  // BRONZE / STEEL / TITANIUM / TUNGSTENSTEEL
 *
 *     // 控制器自身和普通外壳 → 从接口获取
 *     &#64;Override
 *     public IBlockState getCasingBlock() {
 *         return boilerType.getCasingState();
 *     }
 *
 *     // 组件按位置区分 → 火室 vs 普通外壳
 *     &#64;Override
 *     public IBlockState getCasingBlock(@Nullable IMultiblockPart sourcePart) {
 *         if (sourcePart != null && isFireboxPart(sourcePart)) {
 *             return boilerType.getFireboxState();
 *         }
 *         return boilerType.getCasingState();
 *     }
 *
 *     private boolean isFireboxPart(IMultiblockPart sourcePart) {
 *         return isStructureFormed()
 *             && ((MetaTileEntity) sourcePart).getPos().getY() < getPos().getY();
 *     }
 * }
 * }</pre>
 *
 * <h3>模式 D：单变体 + 反射 + 位置区分（如 SteamOven）</h3>
 * <p>
 * 有 {@code static getCasingState()} 反射可用，但底部火室位置需要不同材质。
 * 无参版本反射自动处理，只覆写带参版本区分火室。
 * </p>
 * <pre>{@code
 * public class MetaTileEntitySteamOven extends RecipeMapSteamMultiblockController {
 *
 *     // 反射处理无参版本
 *     public static IBlockState getCasingState() {
 *         return MetaBlocks.METAL_CASING.getState(MetalCasingType.BRONZE_BRICKS);
 *     }
 *
 *     public static IBlockState getFireboxState() {
 *         return MetaBlocks.BOILER_FIREBOX_CASING.getState(FireboxCasingType.BRONZE_FIREBOX);
 *     }
 *
 *     // 组件按位置 → 火室材质 vs 普通外壳
 *     &#64;Override
 *     public IBlockState getCasingBlock(@Nullable IMultiblockPart sourcePart) {
 *         if (sourcePart != null && isFireboxPart(sourcePart)) {
 *             return getFireboxState();
 *         }
 *         return super.getCasingBlock(sourcePart);  // → getCasingBlock() → 反射 → BRONZE_BRICKS
 *     }
 * }
 * }</pre>
 *
 * <h3>完整调用链路</h3>
 * <pre>
 * CTM 模组
 *   └→ BlockMachine.getFacade(world, pos, side)
 *        └→ metaTileEntity.getCasingBlock()
 *             ├─ [组件方块] MetaTileEntityMultiblockPart.getCasingBlock()
 *             │    └→ controller.getCasingBlock(this)          ← 带参，传入组件自身
 *             │         ├─ 子类覆写 → 按位置/类型返回不同 IBlockState → CTM 连接 ✓
 *             │         └─ 默认实现 → 调用无参版本 → 反射或覆写
 *             │
 *             └─ [控制器自身] MultiblockControllerBase.renderMetaTileEntity()
 *                  └→ getCasingBlock(null)                     ← 带参，null 表示控制器
 *
 * 多方块组件渲染
 *   └→ MetaTileEntityMultiblockPart.getBaseTexture()
 *        └→ controller.getCasingBlock(this)                   ← 有值 → VisualStateRenderer → CTM
 *        └→ controller.getBaseTexture(this)                  ← 无值 → 回退普通纹理
 * </pre>
 *
 * <h3>四种模式速查</h3>
 * <table border="1">
 *   <tr><th>场景</th><th>覆写无参 getCasingBlock()</th><th>覆写带参 getCasingBlock(IMultiblockPart)</th><th>示例</th></tr>
 *   <tr><td>纯反射</td><td>❌</td><td>❌</td><td>EBF、CokeOven、VacuumFreezer</td></tr>
 *   <tr><td>反射 + 组件类型区分</td><td>❌</td><td>✅</td><td>AssemblyLine</td></tr>
 *   <tr><td>反射 + 位置区分</td><td>❌</td><td>✅</td><td>SteamOven、SteamGrinder</td></tr>
 *   <tr><td>多变体接口型</td><td>✅ (委托 type.getXxx())</td><td>✅ (可选，火室区分)</td><td>LargeBoiler、FusionReactor、LargeTurbine、LargeMiner、FluidDrill</td></tr>
 * </table>
 *
 * <h3>蒸汽仓室注意事项</h3>
 * <p>
 * 蒸汽多方块组件（如 SteamHatch、SteamItemBus 等）不应覆写 {@code getBaseTexture()} 并跳过
 * {@code controller.getCasingBlock()} 检查，否则该组件会丢失 CTM。
 * 如果需要未成形时的回退纹理，正确写法是：
 * </p>
 * <pre>{@code
 * &#64;Override
 * public ICubeRenderer getBaseTexture() {
 *     if (getController() == null)
 *         return Textures.STEAM_CASING_BRONZE;  // 未成形时的 fallback
 *     return super.getBaseTexture();            // 成形后走 CTM 路径
 * }
 * }</pre>
 *
 * <h3>添加新多方块 CTM 检查清单</h3>
 * <ol>
 *   <li>是单变体？→ 声明 {@code public static getCasingState()}，完成。</li>
 *   <li>是多变体（接口型）？→ 覆写无参 {@code getCasingBlock()} 委托给类型接口。</li>
 *   <li>需要按位置/类型区分材质？→ 覆写带参 {@code getCasingBlock(IMultiblockPart)}。</li>
 *   <li>蒸汽仓室 or 自定义组件？→ 确保 {@code getBaseTexture()} 没有跳过 CTM 检查。</li>
 * </ol>
 *
 * @see gregtech.api.metatileentity.multiblock.MultiblockControllerBase#getCasingBlock()
 * @see gregtech.api.metatileentity.multiblock.MultiblockControllerBase#getCasingBlock(IMultiblockPart)
 */
package gregtech.api.metatileentity.multiblock;
