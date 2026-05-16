package gregtech.common.metatileentities.multi;

import gregtech.api.capability.impl.FilteredFluidHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.PropertyFluidFilter;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.ParametricMultiblockController;
import gregtech.api.metatileentity.multiblock.ParametricVariantRegistries;
import gregtech.api.metatileentity.multiblock.ParametricVariantRegistry;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.BlockSteamCasing;
import gregtech.common.blocks.BlockTankCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
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

import java.util.List;

/**
 * Single-ID multiblock tank supporting multiple material variants via NBT.
 * Extends {@link ParametricMultiblockController} for automatic variant serialization,
 * sub-item generation, and localization.
 */
public class MetaTileEntityMultiblockTank extends ParametricMultiblockController<MetaTileEntityMultiblockTank.TankMaterial> {

    private static final ParametricVariantRegistry<TankMaterial> VARIANTS =
            ParametricVariantRegistries.enumRegistry("gregtech", TankMaterial.class, TankMaterial.WOOD);

    private static BlockPatternTemplate buildTemplate(TankMaterial tankMaterial) {
        IBlockState casingState = tankMaterial.getCasingState();
        return DeclarativePatternBuilder.start()
                .aisle("XXX", "XXX", "XXX")
                .aisle("XXX", "X X", "XXX")
                .aisle("XXX", "XSX", "XXX")
                .where('S', selfPredicateByClass(MetaTileEntityMultiblockTank.class))
                .where(' ', air())
                .casing('X', CasingDefinition.simple(casingState, tankMaterial.getCasingLangKey()))
                    .withCustomHatches(
                            metaTileEntities(MetaTileEntities.MULTIBLOCK_TANK_VALVE)
                                    .setMaxGlobalLimited(2), 2)
                .buildTemplate();
    }

    public MetaTileEntityMultiblockTank(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, VARIANTS);
        initializeInventory();
    }

    @Override
    protected void onVariantChanged() {
        initializeInventory();
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        TankMaterial mat = getVariant();
        if (mat == null) return;

        FilteredFluidHandler tank = new FilteredFluidHandler(mat.getCapacity());
        if (mat.isWood()) {
            tank.setFilter(new PropertyFluidFilter(340, false, false, false, false));
        }

        this.exportFluids = this.importFluids = new FluidTankList(true, tank);
        this.fluidInventory = tank;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        MetaTileEntityMultiblockTank mte = new MetaTileEntityMultiblockTank(metaTileEntityId);
        mte.setVariant(getVariant());
        mte.initializeInventory();
        return mte;
    }

    @Override
    @NotNull
    protected BlockPatternTemplate buildStructureTemplate(@NotNull TankMaterial variantValue) {
        return buildTemplate(variantValue);
    }

    @Override
    @NotNull
    protected String getVariantTranslationPrefix() {
        return "gregtech.machine.tank";
    }

    @Override
    protected void updateFormedValid() {}

    @SideOnly(Side.CLIENT)
    @Override
    @NotNull
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return getVariant().getTexture();
    }

    @Override
    public GTGuiTheme getUITheme() {
        if (getVariant().isWood()) return GTGuiTheme.PRIMITIVE;
        return GTGuiTheme.STEEL;
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
        TankMaterial mat = getVariantFromStack(stack);
        tooltip.add(I18n.format("gregtech.multiblock.tank.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity", mat.getCapacity()));
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
        return getVariant().isWood() ? SoundType.WOOD : SoundType.METAL;
    }

    /**
     * Defines all available tank materials with their properties.
     */
    public enum TankMaterial {

        // Sorted by tech level (ascending): Primitive -> LV -> MV -> HV -> EV -> IV -> LuV -> UV -> UHV -> MAX
        // Designed with Aluminium (MV) = 10M as anchor. Each tier roughly doubles.
        // Multiblock tanks should have more capacity than same-tier Super Tanks (single block).
        WOOD(250_000, null, null),
        COPPER(500_000, Materials.Copper, BlockTankCasing.TankCasingType.COPPER),
        LEAD(750_000, Materials.Lead, BlockTankCasing.TankCasingType.LEAD),
        IRON(1_000_000, Materials.Iron, BlockTankCasing.TankCasingType.IRON),
        BRONZE(1_500_000, Materials.Bronze, BlockTankCasing.TankCasingType.BRONZE),
        GOLD(2_500_000, Materials.Gold, BlockTankCasing.TankCasingType.GOLD),
        STEEL(5_000_000, Materials.Steel, null),
        ALUMINIUM(10_000_000, Materials.Aluminium, BlockTankCasing.TankCasingType.ALUMINIUM),
        CHROME(10_000_000, Materials.Chrome, BlockTankCasing.TankCasingType.CHROME),
        STAINLESS_STEEL(20_000_000, Materials.StainlessSteel, BlockTankCasing.TankCasingType.STAINLESS_STEEL),
        TITANIUM(40_000_000, Materials.Titanium, BlockTankCasing.TankCasingType.TITANIUM),
        TUNGSTEN(80_000_000, Materials.Tungsten, BlockTankCasing.TankCasingType.TUNGSTEN),
        TUNGSTENSTEEL(160_000_000, Materials.TungstenSteel, BlockTankCasing.TankCasingType.TUNGSTENSTEEL),
        IRIDIUM(320_000_000, Materials.Iridium, BlockTankCasing.TankCasingType.IRIDIUM),
        RHODIUM_PLATED_PALLADIUM(640_000_000, Materials.RhodiumPlatedPalladium, BlockTankCasing.TankCasingType.RHODIUM_PLATED_PALLADIUM),
        NAQUADAH_ALLOY(1_000_000_000, Materials.NaquadahAlloy, BlockTankCasing.TankCasingType.NAQUADAH_ALLOY),
        DARMSTADTIUM(1_500_000_000, Materials.Darmstadtium, BlockTankCasing.TankCasingType.DARMSTADTIUM),
        NEUTRONIUM(2_000_000_000, Materials.Neutronium, BlockTankCasing.TankCasingType.NEUTRONIUM);

        private final int capacity;
        @Nullable
        private final gregtech.api.unification.material.Material material;
        @Nullable
        private final BlockTankCasing.TankCasingType casingType;

        TankMaterial(int capacity, @Nullable gregtech.api.unification.material.Material material,
                     @Nullable BlockTankCasing.TankCasingType casingType) {
            this.capacity = capacity;
            this.material = material;
            this.casingType = casingType;
        }

        public int getCapacity() {
            return capacity;
        }

        @Nullable
        public gregtech.api.unification.material.Material getMaterial() {
            return material;
        }

        @NotNull
        public gregtech.api.unification.material.Material getRecipeMaterial() {
            return this == WOOD ? Materials.Lead : material;
        }

        public boolean isWood() {
            return this == WOOD;
        }

        public boolean hasOwnCasing() {
            return casingType != null;
        }

        public IBlockState getCasingState() {
            if (this == WOOD) {
                return MetaBlocks.STEAM_CASING.getState(BlockSteamCasing.SteamCasingType.WOOD_WALL);
            }
            if (this == STEEL) {
                return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID);
            }
            return MetaBlocks.TANK_CASING.getState(casingType);
        }

        public ItemStack getCasingItemStack() {
            if (this == WOOD) {
                return MetaBlocks.STEAM_CASING.getItemVariant(BlockSteamCasing.SteamCasingType.WOOD_WALL);
            }
            if (this == STEEL) {
                return MetaBlocks.METAL_CASING.getItemVariant(BlockMetalCasing.MetalCasingType.STEEL_SOLID);
            }
            return MetaBlocks.TANK_CASING.getItemVariant(casingType);
        }

        public String getCasingLangKey() {
            return switch (this) {
                case WOOD -> "gregtech.machine.casing.wood_wall";
                case STEEL -> "gregtech.machine.casing.solid_steel";
                default -> "tile.tank_casing." + this.name().toLowerCase() + ".name";
            };
        }

        @SideOnly(Side.CLIENT)
        public ICubeRenderer getTexture() {
            return switch (this) {
                case WOOD -> Textures.WOOD_WALL;
                case STEEL -> Textures.SOLID_STEEL_CASING;
                default -> Textures.TANK_CASINGS[casingType.ordinal()];
            };
        }
    }
}
