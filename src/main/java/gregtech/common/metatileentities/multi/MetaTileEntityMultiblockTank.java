package gregtech.common.metatileentities.multi;

import gregtech.api.capability.impl.FilteredFluidHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.PropertyFluidFilter;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.pattern.SoftReferenceHolder;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetaTileEntityMultiblockTank extends MultiblockWithDisplayBase {

    private static final Map<String, SoftReferenceHolder<? extends StructureDefinition<?>>> STRUCTURE_DEFINITIONS =
            new HashMap<>();
    private final String tankTypeName;
    private final boolean isWood;
    private final int capacity;
    private final IBlockState casingState;
    private final MetaTileEntity valve;
    private final ICubeRenderer baseTexture;
    private final SoundType soundType;

    public MetaTileEntityMultiblockTank(ResourceLocation metaTileEntityId, String tankTypeName, boolean isWood,
                                        int capacity, IBlockState casingState, MetaTileEntity valve,
                                        ICubeRenderer baseTexture, SoundType soundType) {
        super(metaTileEntityId);
        this.tankTypeName = tankTypeName;
        this.isWood = isWood;
        this.capacity = capacity;
        this.casingState = casingState;
        this.valve = valve;
        this.baseTexture = baseTexture;
        this.soundType = soundType;
        initializeInventory();
    }

    /**
     * Register a tank variant structure definition in the global template pool. Called from
     * {@code MultiblockRegistration} during initialization.
     *
     * @param name        tank variant name (e.g. "wood", "bronze", "steel")
     * @param casingState the casing block state for this variant
     * @param valve       the valve meta tile entity for this variant
     */
    public static void registerTankStructure(@NotNull String name, @NotNull IBlockState casingState,
                                             @NotNull MetaTileEntity valve) {
        STRUCTURE_DEFINITIONS.put(name, TemplatePool.getInstance()
                .registerStructure("gregtech:tank." + name, () -> buildStructureDefinition(casingState, valve)));
    }

    private static StructureDefinition<?> buildStructureDefinition(@NotNull IBlockState casingState,
                                                                   @NotNull MetaTileEntity valve) {
        return DeclarativePatternBuilder.start()
                .aisle("XXX", "XXX", "XXX")
                .aisle("XXX", "X X", "XXX")
                .aisle("XXX", "XSX", "XXX")
                .self('S', MetaTileEntityMultiblockTank.class)
                .air(' ')
                .casing('X', casingState)
                .custom(Elements.metaTileEntities(0, 2, 2, valve), 2)
                .buildStructureDefinition();
    }

    @Override
    public IBlockState getCasingBlock() {
        return casingState;
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();

        FilteredFluidHandler tank = new FilteredFluidHandler(capacity);
        if (isWood) {
            tank.setFilter(new PropertyFluidFilter(340, false, false, false, false));
        }

        this.exportFluids = this.importFluids = new FluidTankList(true, tank);
        this.fluidInventory = tank;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMultiblockTank(metaTileEntityId, tankTypeName, isWood, capacity, casingState, valve,
                baseTexture, soundType);
    }

    @Override
    protected void updateFormedValid() {}

    @Override
    @NotNull
    protected StructureDefinition<?> createStructureDefinition() {
        SoftReferenceHolder<? extends StructureDefinition<?>> definition = STRUCTURE_DEFINITIONS.get(tankTypeName);
        if (definition == null) {
            throw new IllegalStateException("Unknown tank type: " + tankTypeName);
        }
        return definition.get();
    }

    @SideOnly(Side.CLIENT)
    @Override
    @NotNull
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return baseTexture;
    }

    @Override
    public GTGuiTheme getUITheme() {
        if (!isWood) return GTGuiTheme.STEEL;
        else return GTGuiTheme.PRIMITIVE;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        if (!isStructureFormed())
            return false;
        return super.onRightClick(playerIn, hand, facing, hitResult);
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return isStructureFormed();
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return new MultiblockUIFactory(this)
                .setSize(176, 166)
                .disableDisplay()
                .disableButtons()
                .addScreenChildren((parent, syncManager) -> {
                    parent.child(IKey.lang(getMetaFullName())
                            .asWidget()
                            .pos(5, 5));
                    parent.child(new GTFluidSlot()
                            .pos(52, 18)
                            .size(72, 61)
                            // todo this looks ugly
                            .overlay(GTGuiTextures.PRIMITIVE_LARGE_FLUID_TANK_OVERLAY.asIcon()
                                    .alignment(Alignment.CenterLeft)
                                    .size(30, 58))
                            .syncHandler(GTFluidSlot.sync(importFluids.getTankAt(0))
                                    .showAmountOnSlot(false)
                                    .drawAlwaysFull(false)));
                });
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        getFrontOverlay().renderSided(getFrontFacing(), renderState, translation, pipeline);
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.MULTIBLOCK_TANK_OVERLAY;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.multiblock.tank.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity", capacity));
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if (isStructureFormed()) {
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidInventory);
            } else {
                return null;
            }
        }
        return super.getCapability(capability, side);
    }

    @NotNull
    @Override
    public SoundType getSoundType() {
        return soundType;
    }
}
