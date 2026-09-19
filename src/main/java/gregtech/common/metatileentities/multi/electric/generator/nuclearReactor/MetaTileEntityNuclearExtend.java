package gregtech.common.metatileentities.multi.electric.generator.nuclearReactor;

import gregtech.api.capability.INuclearExtend;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.SCMultiblockAbility;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Structural extension hatch of the nuclear reactor. Every installed hatch widens the reactor's internal component
 * grid by one column; the hatch itself holds no items and has no UI.
 */
public class MetaTileEntityNuclearExtend extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<INuclearExtend>, INuclearExtend {

    public MetaTileEntityNuclearExtend(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 4);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityNuclearExtend(metaTileEntityId);
    }

    @Override
    public MultiblockAbility<INuclearExtend> getAbility() {
        return SCMultiblockAbility.REACTOR_EXTEND_HATCH;
    }

    @Override
    public void registerAbilities(AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return false;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.getController() instanceof MetaTileEntityNuclearReactor nuclearReactor) {
            this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), true,
                    nuclearReactor.isWorkingEnabled());
        } else {
            this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), false,
                    false);
        }
    }

    protected @NotNull ICubeRenderer getFrontOverlay() {
        return Textures.NUCLEAR_REACTOR_EXTEND_OVERLAY;
    }

    @Override
    public void addInformation(ItemStack stack, World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(TextFormatting.GREEN + I18n.format("-工作原理："));
        tooltip.add("通过安装燃料拓展仓来扩展核反应堆的内部容量。");
        tooltip.add("每安装一个拓展仓，反应堆内部空间在X方向增加1格：初始3×6，最多9×6（54个组件槽位）。");
        tooltip.add("安装多个拓展仓可以叠加效果，最多安装 " + MetaTileEntityNuclearReactor.MAX_EXTEND_HATCHES + " 个。");
        tooltip.add("拆除拓展仓时，超出新空间的组件会被送回输出总线，不会丢失。");
    }
}
