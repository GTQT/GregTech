package gregtech.common.items.behaviors;

import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiblockState;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.GTUtility;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiblockBuilderBehavior implements IItemBehaviour, ItemUIFactory {

    private int tier = 0;

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IGregTechTileEntity)) {
            if (!world.isRemote) {
                MetaItemGuiFactory.open(player, hand);
            }
            return EnumActionResult.SUCCESS;
        }
        MetaTileEntity mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        if (!(mte instanceof MultiblockControllerBase multiblock)) return EnumActionResult.PASS;
        if (!player.canPlayerEdit(pos, side, player.getHeldItem(hand))) return EnumActionResult.FAIL;
        if (world.isRemote) return EnumActionResult.SUCCESS;

        if (player.isSneaking()) {
            if (!multiblock.isStructureFormed()) {
                Map<String, Integer> channelValues = tierToChannelValues(tier);
                // Multi-piece structures (new StructureDefinition system) need
                // per-piece auto-build so dynamic offsets can resolve their
                // anchor's repeat count. The legacy single-piece autoBuild
                // path only places blocks at the controller's position, so
                // pieces like "top" anchored to a repeatable "body" would be
                // placed at the wrong Y (typically at the static baseOffset
                // or the controller's position).
                if (autoBuildAllPieces(multiblock, player, channelValues)) {
                    return EnumActionResult.SUCCESS;
                }
                MultiblockState state = multiblock.getMultiblockState();
                if (state != null) {
                    state.autoBuild(player, multiblock, channelValues, false);
                }
                return EnumActionResult.SUCCESS;
            }
            return EnumActionResult.PASS;
        } else {
            if (!multiblock.isStructureFormed()) {
                MultiblockState state = multiblock.getMultiblockState();
                PatternError error = state != null ? state.getError() : null;
                if (error != null) {
                    player.sendMessage(new TextComponentString("============================"));
                    player.sendMessage(
                            new TextComponentTranslation("gregtech.multiblock.pattern.error_message_header"));
                    for (List<ItemStack> stack : error.getCandidates()) {
                        player.sendMessage(new TextComponentString(
                                TextFormatting.RED + "  " + stack.get(0).getDisplayName()));
                    }
                    player.sendMessage(new TextComponentString(
                            TextFormatting.GRAY + "  @ " + error.getPosString(error.getPos())));
                    player.sendMessage(new TextComponentString(
                            TextFormatting.YELLOW + "  " + error.getErrorInfo()));
                    player.sendMessage(new TextComponentString("============================"));
                    return EnumActionResult.SUCCESS;
                }
            }
            player.sendMessage(new TextComponentTranslation("gregtech.multiblock.pattern.no_errors")
                    .setStyle(new Style().setColor(TextFormatting.GREEN)));
            return EnumActionResult.SUCCESS;
        }
    }

    /**
     * Attempt to auto-build all pieces of a multi-piece multiblock structure
     * via the new {@link gregtech.api.pattern.element.StructureDefinition}
     * pipeline. Returns true if the controller's structure definition is a
     * multi-piece pattern and a build was attempted (regardless of whether
     * every piece could be placed — some may fail if the player is missing
     * required materials). Returns false for single-piece structures (legacy
     * {@link BlockPattern} path) so the caller can fall back to
     * {@link MultiblockState#autoBuild}.
     *
     * <p>The per-piece build is required for structures that mix fixed
     * pieces (e.g. a "top" cap) with repeatable pieces (e.g. a "body"
     * whose extent is determined by the user-selected tier / channel
     * value). The single-piece legacy path doesn't know about dynamic
     * offsets and would place the "top" at the static baseOffset, which
     * is typically the controller's position.
     */
    private boolean autoBuildAllPieces(@NotNull MultiblockControllerBase multiblock,
                                       @NotNull EntityPlayer player,
                                       @NotNull Map<String, Integer> channelValues) {
        StructureDefinition definition = multiblock.getStructureDefinition();
        if (definition == null) return false;
        MultiPiecePattern multiPiece = definition.getCompiledPattern();
        int pieceCount = multiPiece.getPieceList().size();
        if (pieceCount == 0) return false;
        for (int i = 1; i <= pieceCount; i++) {
            multiPiece.autoBuildPiece(i, player, multiblock, channelValues, false,
                    multiblock.getPieceRuntimes());
        }
        return true;
    }

    /**
     * 将旧的全局 tier 转换为信道值映射。
     * tier=0 → 最大尺寸，tier=1 → 最小尺寸，tier>=2 → 指定尺寸。
     */
    private static Map<String, Integer> tierToChannelValues(int tier) {
        Map<String, Integer> channels = new HashMap<>();
        channels.put(GTStructureChannels.STRUCTURE_WIDTH.getName(), tier);
        channels.put(GTStructureChannels.STRUCTURE_HEIGHT.getName(), tier);
        channels.put(GTStructureChannels.STRUCTURE_LENGTH.getName(), tier);
        return channels;
    }

    @Override
    public void addPropertyOverride(@NotNull Item item) {
        item.addPropertyOverride(GTUtility.gregtechId("auto_mode"),
                (stack, world, entity) -> (entity != null && entity.isSneaking()) ? 1.0F : 0.0F);
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        lines.add(I18n.format("metaitem.tool.multiblock_builder.tooltip2"));
        if (tier == 0)
            lines.add(I18n.format("构建结构：最大等级"));
        else if (tier == 1)
            lines.add(I18n.format("构建结构：最小等级"));
        else
            lines.add(I18n.format("构建结构：" + tier));
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        var panel = GTGuis.createPanel(guiData.getUsedItemStack(), 176, 100);

        IntSyncValue tierValue = new IntSyncValue(
                this::getTier,
                this::setTier
        );

        StringSyncValue formattedUpdateTime = new StringSyncValue(() -> {
            if (tierValue.getValue() == 0) return "等级：MAX";
            return "等级：" + tierValue.getValue();
        });

        guiSyncManager.syncValue("tier_value", tierValue);

        return panel.child(IKey.lang("多方块构建器").asWidget().pos(5, 5))
                .child(new TextFieldWidget()
                        .widthRel(0.8f)
                        .pos(15, 17)
                        .height(20)
                        .setTextColor(Color.WHITE.darker(1))
                        .value(formattedUpdateTime)
                        .background(GTGuiTextures.DISPLAY)
                )
                .child(Flow.row()
                        .pos(15, 42)
                        .widthRel(0.8f)
                        .height(36 + 9)
                        .child(new ButtonWidget<>()
                                .left(0).width(60)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang(
                                                "当前可重复的通道重复次数-1（只有当前可重复通道达到最小重复次数时恢复下一个通道）")))
                                .onMousePressed(mouseButton -> {
                                    tierValue.setValue(MathHelper.clamp(
                                            tierValue.getValue() - 1, 0,
                                            100));
                                    return true;
                                })
                                .onUpdateListener(w -> w.overlay(IKey.str("减小等级")))
                        )
                        .child(new ButtonWidget<>()
                                .left(80).width(60)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang(
                                                "当前可重复的通道重复次数+1（只有当前可重复通道达到最大重复次数时重复下一个通道）")))
                                .onMousePressed(mouseButton -> {
                                    tierValue.setValue(MathHelper.clamp(
                                            tierValue.getValue() + 1, 0,
                                            100));
                                    return true;
                                })
                                .onUpdateListener(w -> w.overlay(IKey.str("增大等级")))
                        )
                        .child(new ButtonWidget<>()
                                .top(27)
                                .left(0).width(60)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("每个通道只重复一次")))
                                .onMousePressed(mouseButton -> {
                                    tierValue.setValue(1);
                                    return true;
                                })
                                .onUpdateListener(w -> w.overlay(IKey.str("最小等级")))
                        )
                        .child(new ButtonWidget<>()
                                .top(27)
                                .left(80).width(60)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("每个通道重复最大次数")))
                                .onMousePressed(mouseButton -> {
                                    tierValue.setValue(0);
                                    return true;
                                })
                                .onUpdateListener(w -> w.overlay(IKey.str("最大等级")))
                        )
                );
    }

    private void setTier(int newTier) {
        tier = newTier;
    }

    private int getTier() {
        return tier;
    }
}
