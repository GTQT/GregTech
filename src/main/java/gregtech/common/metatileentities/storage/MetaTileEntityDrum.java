package gregtech.common.metatileentities.storage;

import gregtech.api.capability.IPropertyFluidFilter;
import gregtech.api.capability.impl.FilteredFluidHandler;
import gregtech.api.capability.impl.GTFluidHandlerItemStack;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.ParametricMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.variant.ParametricVariantRegistries;
import gregtech.api.metatileentity.variant.ParametricVariantRegistry;
import gregtech.api.recipes.ModHandler;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.block.SoundType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.colour.ColourRGBA;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.UPDATE_AUTO_OUTPUT;

/**
 * Single-ID drum supporting multiple material variants via stable registry ids in NBT.
 */
public class MetaTileEntityDrum extends ParametricMetaTileEntity<DrumVariant> {

    private final boolean fixedVariantRegistry;

    private FilteredFluidHandler fluidTank;
    private boolean isAutoOutput = false;

    public MetaTileEntityDrum(ResourceLocation metaTileEntityId) {
        this(metaTileEntityId, DrumVariants.registry(), DrumVariants.WOOD, false);
    }

    private MetaTileEntityDrum(@NotNull ResourceLocation metaTileEntityId,
                               @NotNull DrumVariant fixedVariant) {
        this(metaTileEntityId, ParametricVariantRegistries.single(fixedVariant.getId(), fixedVariant),
                fixedVariant, true);
    }

    private MetaTileEntityDrum(@NotNull ResourceLocation metaTileEntityId,
                               @NotNull ParametricVariantRegistry<DrumVariant> variantRegistry,
                               @NotNull DrumVariant defaultVariant,
                               boolean fixedVariantRegistry) {
        super(metaTileEntityId, variantRegistry, defaultVariant);
        this.fixedVariantRegistry = fixedVariantRegistry;
        initializeInventory();
    }

    /**
     * @deprecated Drums are now a single parametric MTE using {@link DrumVariant}. Add new drum variants through
     *             {@link DrumVariants#register(DrumVariant)} instead of registering separate MTEs with this constructor.
     */
    @Deprecated
    public MetaTileEntityDrum(ResourceLocation metaTileEntityId, @NotNull Material material, int tankSize) {
        this(metaTileEntityId, DrumVariant.legacy(metaTileEntityId,
                DrumVariant.getFluidFilterForMaterial(material),
                ModHandler.isMaterialWood(material), material.getMaterialRGB(), tankSize));
    }

    /**
     * @deprecated Drums are now a single parametric MTE using {@link DrumVariant}. This constructor remains for addons
     *             that still extend/register separate drum-like MTEs.
     */
    @Deprecated
    public MetaTileEntityDrum(ResourceLocation metaTileEntityId, @NotNull IPropertyFluidFilter fluidFilter,
                              boolean isWood, int color, int tankSize) {
        this(metaTileEntityId, DrumVariant.legacy(metaTileEntityId, fluidFilter, isWood, color, tankSize));
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        if (fixedVariantRegistry) {
            return new MetaTileEntityDrum(metaTileEntityId, getVariant());
        }
        MetaTileEntityDrum drum = new MetaTileEntityDrum(metaTileEntityId);
        drum.setVariant(getVariant());
        drum.initializeInventory();
        return drum;
    }

    @Override
    @NotNull
    protected String getVariantTranslationPrefix() {
        return "gregtech.machine.drum";
    }

    @Override
    protected void onVariantChanged() {
        initializeInventory();
    }

    @Override
    public String getHarvestTool() {
        return getVariant().isWood() ? ToolClasses.AXE : ToolClasses.WRENCH;
    }

    @Override
    public boolean hasFrontFacing() {
        return false;
    }

    @Override
    protected void initializeInventory() {
        DrumVariant variant = getVariant();
        if (variant == null) return;

        super.initializeInventory();
        this.fluidTank = new FilteredFluidHandler(variant.getTankSize()).setFilter(variant.getFluidFilter());
        this.fluidInventory = this.fluidTank;
    }

    // region Fluid NBT persistence (stored fluid in ItemStack and TileEntity)

    @Override
    public void initFromItemStackData(NBTTagCompound itemStack) {
        super.initFromItemStackData(itemStack);
        if (itemStack.hasKey(FluidHandlerItemStack.FLUID_NBT_KEY, Constants.NBT.TAG_COMPOUND)) {
            FluidStack fluidStack = FluidStack
                    .loadFluidStackFromNBT(itemStack.getCompoundTag(FluidHandlerItemStack.FLUID_NBT_KEY));
            fluidTank.setFluid(fluidStack);
        }
    }

    @Override
    public void writeItemStackData(NBTTagCompound itemStack) {
        super.writeItemStackData(itemStack);
        FluidStack fluidStack = fluidTank.getFluid();
        if (fluidStack != null && fluidStack.amount > 0) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            fluidStack.writeToNBT(tagCompound);
            itemStack.setTag(FluidHandlerItemStack.FLUID_NBT_KEY, tagCompound);
        }
    }

    @Override
    public ICapabilityProvider initItemStackCapabilities(ItemStack itemStack) {
        DrumVariant variant = getVariantFromStack(itemStack);
        return new GTFluidHandlerItemStack(itemStack, variant.getTankSize()).setFilter(variant.getFluidFilter());
    }

    // endregion

    // region Network sync

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        FluidStack fluidStack = fluidTank.getFluid();
        buf.writeBoolean(fluidStack != null);
        if (fluidStack != null) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            fluidStack.writeToNBT(tagCompound);
            buf.writeCompoundTag(tagCompound);
        }
        buf.writeBoolean(isAutoOutput);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        FluidStack fluidStack = null;
        if (buf.readBoolean()) {
            try {
                NBTTagCompound tagCompound = buf.readCompoundTag();
                fluidStack = FluidStack.loadFluidStackFromNBT(tagCompound);
            } catch (IOException ignored) {}
        }
        fluidTank.setFluid(fluidStack);
        isAutoOutput = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_AUTO_OUTPUT) {
            this.isAutoOutput = buf.readBoolean();
            scheduleRenderUpdate();
        }
    }

    // endregion

    // region Update and interaction

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            if (isAutoOutput && getOffsetTimer() % 5 == 0) {
                pushFluidsIntoNearbyHandlers(EnumFacing.DOWN);
            }
        }
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        if (playerIn.getHeldItem(hand).hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            return getWorld().isRemote ||
                    (!playerIn.isSneaking() && FluidUtil.interactWithFluidHandler(playerIn, hand, fluidTank));
        }
        return super.onRightClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing wrenchSide,
                                      CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            if (getWorld().isRemote) {
                scheduleRenderUpdate();
                return true;
            }
            playerIn.sendStatusMessage(new TextComponentTranslation(
                    "gregtech.machine.drum." + (isAutoOutput ? "disable" : "enable") + "_output"), true);
            toggleOutput();
            return true;
        }
        return super.onScrewdriverClick(playerIn, hand, wrenchSide, hitResult);
    }

    private void toggleOutput() {
        isAutoOutput = !isAutoOutput;
        if (!getWorld().isRemote) {
            notifyBlockUpdate();
            writeCustomData(UPDATE_AUTO_OUTPUT, buf -> buf.writeBoolean(isAutoOutput));
            markDirty();
        }
    }

    // endregion

    // region Rendering

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        if (getVariant().isWood()) {
            return Pair.of(Textures.WOODEN_DRUM.getParticleTexture(), getPaintingColorForRendering());
        } else {
            int color = GTUtility.convertOpaqueRGBA_CLtoRGB(ColourRGBA.multiply(
                    GTUtility.convertRGBtoOpaqueRGBA_CL(getVariant().getColor()),
                    GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
            return Pair.of(Textures.DRUM.getParticleTexture(), color);
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        if (getVariant().isWood()) {
            ColourMultiplier multiplier = new ColourMultiplier(
                    GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering()));
            Textures.WOODEN_DRUM.render(renderState, translation, ArrayUtils.add(pipeline, multiplier),
                    getFrontFacing());
        } else {
            ColourMultiplier multiplier = new ColourMultiplier(
                    ColourRGBA.multiply(GTUtility.convertRGBtoOpaqueRGBA_CL(getVariant().getColor()),
                            GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
            Textures.DRUM.render(renderState, translation, ArrayUtils.add(pipeline, multiplier), getFrontFacing());
            Textures.DRUM_OVERLAY.render(renderState, translation, pipeline);
        }

        if (isAutoOutput) {
            Textures.STEAM_VENT_OVERLAY.renderSided(EnumFacing.DOWN, renderState, translation, pipeline);
        }
    }

    @Override
    public int getDefaultPaintingColor() {
        return 0xFFFFFF;
    }

    // endregion

    // region Tooltip

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.quantum_tank.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity", getTankSize(stack)));

        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound != null && tagCompound.hasKey("Fluid", Constants.NBT.TAG_COMPOUND)) {
            FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(tagCompound.getCompoundTag("Fluid"));
            if (fluidStack == null) return;
            tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_stored", fluidStack.getLocalizedName(),
                    fluidStack.amount));
        }

        getFluidFilter(stack).appendTooltips(tooltip, true, true);

        if (TooltipHelper.isShiftDown()) {
            tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
            tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_output_down"));
            tooltip.add(I18n.format("gregtech.tool_action.crowbar"));
        }
    }

    // Override this so that we can control the "Hold SHIFT" tooltip manually
    @Override
    public boolean showToolUsages() {
        return false;
    }

    // endregion

    // region NBT persistence

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("FluidInventory", ((FluidTank) fluidInventory).writeToNBT(new NBTTagCompound()));
        data.setBoolean("AutoOutput", isAutoOutput);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        ((FluidTank) this.fluidInventory).readFromNBT(data.getCompoundTag("FluidInventory"));
        isAutoOutput = data.getBoolean("AutoOutput");
    }

    @Override
    protected boolean shouldSerializeInventories() {
        return false;
    }

    // endregion

    public int getTankSize() {
        return getVariant().getTankSize();
    }

    public int getTankSize(@NotNull ItemStack stack) {
        return getVariantFromStack(stack).getTankSize();
    }

    @NotNull
    @Override
    public SoundType getSoundType() {
        return getVariant().isWood() ? SoundType.WOOD : SoundType.METAL;
    }

    @Override
    public String getMetaName() {
        return getVariant().getTranslationKey();
    }

    @Override
    public String getMetaName(@NotNull ItemStack stack) {
        return getVariantFromStack(stack).getTranslationKey();
    }

    @Deprecated
    @NotNull
    public ItemStack getStackForm(@NotNull DrumMaterial material) {
        return getStackForm(material.getVariant());
    }

    private IPropertyFluidFilter getFluidFilter(@NotNull ItemStack stack) {
        return getVariantFromStack(stack).getFluidFilter();
    }

    /**
     * @deprecated Built-in drum materials now live in {@link DrumVariants}; use {@link DrumVariant} for new code.
     */
    @Deprecated
    public enum DrumMaterial {

        WOOD(DrumVariants.WOOD),
        COPPER(DrumVariants.COPPER),
        LEAD(DrumVariants.LEAD),
        IRON(DrumVariants.IRON),
        BRONZE(DrumVariants.BRONZE),
        GOLD(DrumVariants.GOLD),
        STEEL(DrumVariants.STEEL),
        ALUMINIUM(DrumVariants.ALUMINIUM),
        CHROME(DrumVariants.CHROME),
        STAINLESS_STEEL(DrumVariants.STAINLESS_STEEL),
        TITANIUM(DrumVariants.TITANIUM),
        TUNGSTEN(DrumVariants.TUNGSTEN),
        TUNGSTENSTEEL(DrumVariants.TUNGSTENSTEEL),
        IRIDIUM(DrumVariants.IRIDIUM),
        RHODIUM_PLATED_PALLADIUM(DrumVariants.RHODIUM_PLATED_PALLADIUM),
        NAQUADAH_ALLOY(DrumVariants.NAQUADAH_ALLOY),
        DARMSTADTIUM(DrumVariants.DARMSTADTIUM),
        NEUTRONIUM(DrumVariants.NEUTRONIUM);

        private final DrumVariant variant;

        DrumMaterial(@NotNull DrumVariant variant) {
            this.variant = variant;
        }

        @NotNull
        public DrumVariant getVariant() {
            return variant;
        }

        @NotNull
        public Material getMaterial() {
            Material material = variant.getMaterial();
            if (material == null) {
                throw new IllegalStateException("Drum material " + name() + " is not backed by a Material");
            }
            return material;
        }

        public int getTankSize() {
            return variant.getTankSize();
        }

        public int getColor() {
            return variant.getColor();
        }

        public boolean isWood() {
            return variant.isWood();
        }

        @NotNull
        public IPropertyFluidFilter getFluidFilter() {
            return variant.getFluidFilter();
        }
    }
}
