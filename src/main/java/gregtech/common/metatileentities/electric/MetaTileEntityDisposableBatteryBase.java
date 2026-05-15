package gregtech.common.metatileentities.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.GTGuiTextures;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.PipelineUtil;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Concrete MTE class for all A-series disposable (single-use) battery blocks.
 *
 * <p>Each instance is parameterised by a {@link DisposableBatteryType} enum constant
 * that defines the voltage tier, EU capacity, and depletion byproducts.  No per-variant
 * subclasses are needed — new battery chemistries are added by extending the enum.
 *
 * <p>The block emits energy every server tick via the {@link EnergyContainerHandler}
 * emitter path (no RecipeMap involved), and drops chemical byproducts once when the
 * internal charge reaches zero.
 *
 * <p>Energy state is persisted automatically by {@link EnergyContainerHandler}'s own
 * NBT serialisation.  A separate {@code depleted} flag is written to the MTE's own NBT
 * so the depletion action is not re-triggered after a chunk reload.
 */
public class MetaTileEntityDisposableBatteryBase extends TieredMetaTileEntity {

    /** Fixed output amperage for all disposable battery blocks. */
    protected static final long OUTPUT_AMPERAGE = 4L;

    private static final String NBT_KEY_DEPLETED = "Depleted";

    /** The battery variant this instance represents. */
    private final DisposableBatteryType batteryType;

    /**
     * Total EU capacity baked into this block at craft time.
     * Cached from {@link DisposableBatteryType#getMaxStoredEU()} for fast access.
     */
    protected final long maxStoredEU;

    /** True once {@link #onDepleted()} has been called to prevent re-entry. */
    private boolean depleted;

    /**
     * @param metaTileEntityId unique registry ResourceLocation
     * @param batteryType      enum constant defining tier, capacity and byproducts
     */
    public MetaTileEntityDisposableBatteryBase(@NotNull ResourceLocation metaTileEntityId,
                                               @NotNull DisposableBatteryType batteryType) {
        super(metaTileEntityId, batteryType.getTier());
        this.batteryType = batteryType;
        this.maxStoredEU = batteryType.getMaxStoredEU();
        // The parent constructor already called reinitializeEnergyContainer() with
        // the default capacity (tierVoltage * 64). We must re-initialise now that
        // maxStoredEU is assigned so the handler holds the correct capacity.
        reinitializeEnergyContainer();
        // Pre-fill to maximum on first construction (fresh item placement)
        ((EnergyContainerHandler) energyContainer).setEnergyStored(maxStoredEU);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityDisposableBatteryBase(metaTileEntityId, batteryType);
    }

    // -------------------------------------------------------------------------
    // Energy container configuration
    // -------------------------------------------------------------------------

    @Override
    protected boolean isEnergyEmitter() {
        return true;
    }

    @Override
    protected long getMaxInputOutputAmperage() {
        return OUTPUT_AMPERAGE;
    }

    /**
     * Override to use {@code maxStoredEU} as the handler capacity instead of the
     * default {@code tierVoltage * 64} buffer, and to allow output on all sides.
     */
    @Override
    protected void reinitializeEnergyContainer() {
        if (maxStoredEU == 0) {
            // Called from TieredMetaTileEntity constructor before maxStoredEU is assigned.
            // Fall back to the default behaviour; the constructor body will re-call us.
            super.reinitializeEnergyContainer();
            return;
        }
        long tierVoltage = GTValues.V[getTier()];
        this.energyContainer = EnergyContainerHandler.emitterContainer(
                this, maxStoredEU, tierVoltage, OUTPUT_AMPERAGE);
        // Output on five sides; the front face (control face) does not emit energy
        ((EnergyContainerHandler) this.energyContainer)
                .setSideOutputCondition(side -> side != getFrontFacing());
    }

    // -------------------------------------------------------------------------
    // Per-tick depletion check
    // -------------------------------------------------------------------------

    @Override
    public void update() {
        super.update();

        if (getWorld() == null || getWorld().isRemote || depleted) {
            return;
        }
        // EnergyContainerHandler.update() runs inside super.update() and pushes
        // energy packets to the network, decrementing energyStored automatically.
        if (energyContainer.getEnergyStored() <= 0) {
            onDepleted();
        }
    }

    /**
     * Called exactly once on the server side when the internal charge reaches zero.
     * Retrieves byproduct stacks from the {@link DisposableBatteryType} and drops them.
     */
    private void onDepleted() {
        depleteAndDrop(batteryType.createByproducts());
    }

    // -------------------------------------------------------------------------
    // Shared depletion utility
    // -------------------------------------------------------------------------

    /**
     * Marks this block as depleted, spawns each non-null/non-empty byproduct stack
     * at the block's position, and removes the block from the world.
     *
     * @param byproducts chemical byproduct stacks to drop; nulls and empties are skipped
     */
    protected final void depleteAndDrop(ItemStack... byproducts) {
        depleted = true;
        World world = getWorld();
        BlockPos pos  = getPos();
        if (world == null || world.isRemote) {
            return;
        }
        for (ItemStack stack : byproducts) {
            if (stack != null && !stack.isEmpty()) {
                Block.spawnAsEntity(world, pos, stack);
            }
        }
        world.setBlockToAir(pos);
    }

    // -------------------------------------------------------------------------
    // NBT — only the depleted flag; energy state is handled by EnergyContainerHandler
    // -------------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean(NBT_KEY_DEPLETED, depleted);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        depleted = data.getBoolean(NBT_KEY_DEPLETED);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation,
                                     IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.ENERGY_OUT.renderSided(getFrontFacing(), renderState, translation,
                PipelineUtil.color(pipeline, GTValues.VC[getTier()]));
    }

    // -------------------------------------------------------------------------
    // GUI (MUI2)
    // -------------------------------------------------------------------------

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager,
                                UISettings settings) {
        DoubleSyncValue chargeValue = new DoubleSyncValue(
                () -> maxStoredEU > 0
                        ? (double) energyContainer.getEnergyStored() / maxStoredEU
                        : 0.0);
        panelSyncManager.syncValue("charge", chargeValue);

        return GTGuis.createPanel(this, 176, 120)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(new ProgressWidget()
                        .value(chargeValue)
                        // PROGRESS_BAR_LCE_FUEL is a 62×14 strip texture (7 frames each 62×2)
                        .texture(GTGuiTextures.PROGRESS_BAR_LCE_FUEL, 7)
                        .size(62, 7)
                        .pos(57, 30))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));
    }

    // -------------------------------------------------------------------------
    // Tooltip
    // -------------------------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world,
                               @NotNull List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_out",
                GTValues.V[getTier()], GTValues.VNF[getTier()]));
        tooltip.add(I18n.format("gregtech.universal.tooltip.amperage_out_till", OUTPUT_AMPERAGE));
        tooltip.add(I18n.format("gregtech.machine.disposable_battery.max_capacity",
                maxStoredEU));
        tooltip.add(I18n.format("gregtech.machine.disposable_battery.single_use_warning"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addToolUsages(ItemStack stack, @Nullable World world,
                              List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}
