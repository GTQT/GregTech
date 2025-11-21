package gtqt.client;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 在合成确认界面显示轮数信息
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(Side.CLIENT)
public class CraftConfirmRoundsRenderer {
//
//
//    private static FontRendererAccessor fontRendererAccessor;
//
//    /**
//     * 检查是否是合成确认界面
//     */
//    private static boolean isCraftConfirmGui(GuiScreen gui) {
//        return gui != null && gui.getClass().getSimpleName().equals("GuiCraftConfirm");
//    }
//
//    @SubscribeEvent
//    public static void onGuiDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
//        GuiScreen gui = event.getGui();
//        if (!isCraftConfirmGui(gui)) {
//            return;
//        }
//
//        try {
//            GuiCraftConfirm craftConfirm = (GuiCraftConfirm) gui;
//            renderRoundsInfo(craftConfirm, event.getMouseX(), event.getMouseY());
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * 渲染轮数信息
//     */
//    private static void renderRoundsInfo(GuiCraftConfirm gui, int mouseX, int mouseY) throws Exception {
//        // 获取私有字段
//        IItemList<IAEItemStack> storage = getStorageList(gui);
//        IItemList<IAEItemStack> pending = getPendingList(gui);
//        List<IAEItemStack> visual = getVisualList(gui);
//
//        // 获取 GUI 位置信息
//        int guiLeft = ((GuiContainer) gui).getGuiLeft();
//        int guiTop = ((GuiContainer) gui).getGuiTop();
//
//        // 获取字体渲染器
//        FontRendererAccessor fontAccessor = getFontRendererAccessor();
//        if (fontAccessor == null) return;
//
//        // 遍历所有可见物品并绘制轮数
//        int viewStart = getScrollPosition(gui) * 3;
//        int viewEnd = Math.min(viewStart + 15, visual.size()); // 3列 × 5行 = 15个物品
//
//        for (int i = viewStart; i < viewEnd; i++) {
//            IAEItemStack itemStack = visual.get(i);
//            if (itemStack == null) continue;
//
//            // 计算物品在网格中的位置
//            int index = i - viewStart;
//            int x = index % 3;
//            int y = index / 3;
//
//            // 计算屏幕坐标
//            int posX = guiLeft + x * 68 + 9 + 67 - 19;
//            int posY = guiTop + y * 23 + 22;
//
//            // 计算轮数
//            double rounds = calculateRounds(itemStack, storage, pending);
//
//            // 绘制轮数文本
//            drawRoundsText(fontAccessor, posX, posY, rounds);
//        }
//    }
//
//    /**
//     * 计算轮数：可用数量 / 存储数量
//     * 可用数量 = 当前合成需要该物品的数量（pending中的数量）
//     * 存储数量 = AE系统中该物品的总存储量（storage中的数量）
//     */
//    private static double calculateRounds(IAEItemStack itemStack,
//                                          IItemList<IAEItemStack> storage,
//                                          IItemList<IAEItemStack> pending) {
//        // 获取当前合成需要该物品的数量（可用数量）
//        IAEItemStack pendingStack = (IAEItemStack) pending.findPrecise(itemStack);
//        if (pendingStack == null || pendingStack.getStackSize() <= 0) {
//            return 0;
//        }
//        long requiredAmount = pendingStack.getStackSize();
//
//        // 获取AE系统中该物品的总存储量
//        IAEItemStack storageStack = (IAEItemStack) storage.findPrecise(itemStack);
//        long storageAmount = 0;
//        if (storageStack != null) {
//            storageAmount = storageStack.getStackSize();
//        }
//
//        // 如果存储量为0，避免除以0错误
//        if (storageAmount == 0) {
//            return Double.POSITIVE_INFINITY; // 无穷大，表示需要无限轮
//        }
//
//        // 计算轮数：可用数量 / 存储数量
//        return (double) requiredAmount / storageAmount;
//    }
//
//    /**
//     * 绘制轮数文本
//     */
//    private static void drawRoundsText(FontRendererAccessor fontRenderer, int posX, int posY, double rounds) {
//        String roundsText;
//
//        if (Double.isInfinite(rounds)) {
//            roundsText = "轮数: ∞";
//        } else {
//            roundsText = String.format("轮数: %.2f", rounds);
//        }
//
//        net.minecraft.client.renderer.GlStateManager.pushMatrix();
//        net.minecraft.client.renderer.GlStateManager.scale(0.5F, 0.5F, 0.5F);
//
//        int textX = (int) ((posX - fontRenderer.getStringWidth(roundsText) * 0.25F) * 2.0F);
//        int textY = (posY + 18) * 2;
//
//        fontRenderer.drawString(roundsText, textX, textY, 0xFFFFFF);
//
//        net.minecraft.client.renderer.GlStateManager.popMatrix();
//    }
//
//    // ========== 反射辅助方法 ==========
//
//    /**
//     * 获取存储列表
//     */
//    private static IItemList<IAEItemStack> getStorageList(GuiCraftConfirm gui) throws Exception {
//        Field field = GuiCraftConfirm.class.getDeclaredField("storage");
//        field.setAccessible(true);
//        return (IItemList<IAEItemStack>) field.get(gui);
//    }
//
//    /**
//     * 获取待处理列表
//     */
//    private static IItemList<IAEItemStack> getPendingList(GuiCraftConfirm gui) throws Exception {
//        Field field = GuiCraftConfirm.class.getDeclaredField("pending");
//        field.setAccessible(true);
//        return (IItemList<IAEItemStack>) field.get(gui);
//    }
//
//    /**
//     * 获取缺失列表
//     */
//    private static IItemList<IAEItemStack> getMissingList(GuiCraftConfirm gui) throws Exception {
//        Field field = GuiCraftConfirm.class.getDeclaredField("missing");
//        field.setAccessible(true);
//        return (IItemList<IAEItemStack>) field.get(gui);
//    }
//
//    /**
//     * 获取可视列表
//     */
//    private static List<IAEItemStack> getVisualList(GuiCraftConfirm gui) throws Exception {
//        Field field = GuiCraftConfirm.class.getDeclaredField("visual");
//        field.setAccessible(true);
//        return (List<IAEItemStack>) field.get(gui);
//    }
//
//    /**
//     * 获取滚动位置
//     */
//    private static int getScrollPosition(GuiCraftConfirm gui) throws Exception {
//        // 这里需要根据实际的滚动条实现来获取
//        // 可能需要访问 GuiScrollbar 的当前值
//        // 暂时返回0，您可能需要根据实际情况调整
//        return 0;
//    }
//
//    /**
//     * 获取字体渲染器访问器
//     */
//    private static FontRendererAccessor getFontRendererAccessor() {
//        if (fontRendererAccessor == null) {
//            fontRendererAccessor = new FontRendererAccessor();
//        }
//        return fontRendererAccessor;
//    }
//
//    /**
//     * 字体渲染器访问器
//     */
//    private static class FontRendererAccessor {
//        private Field fontRendererField;
//
//        public int getStringWidth(String text) {
//            try {
//                net.minecraft.client.gui.GuiScreen screen = getCurrentScreen();
//                if (screen != null) {
//                    return getFontRenderer(screen).getStringWidth(text);
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            return 0;
//        }
//
//        public void drawString(String text, int x, int y, int color) {
//            try {
//                net.minecraft.client.gui.GuiScreen screen = getCurrentScreen();
//                if (screen != null) {
//                    getFontRenderer(screen).drawString(text, x, y, color);
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//
//        private net.minecraft.client.gui.GuiScreen getCurrentScreen() {
//            return net.minecraft.client.Minecraft.getMinecraft().currentScreen;
//        }
//    }
//    private static FontRenderer cachedFontRenderer;
//    private static Field fontRendererField;
//    private static FontRenderer getFontRenderer(GuiScreen self) {
//        try {
//            if (cachedFontRenderer != null) {
//                return cachedFontRenderer;
//            }
//
//            if (fontRendererField == null) {
//                fontRendererField = GuiScreen.class.getDeclaredField("fontRenderer");
//                fontRendererField.setAccessible(true);
//            }
//
//            cachedFontRenderer = (FontRenderer) fontRendererField.get(self);
//            return cachedFontRenderer;
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
}
