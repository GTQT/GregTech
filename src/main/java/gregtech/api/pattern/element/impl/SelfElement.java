package gregtech.api.pattern.element.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Element that matches the controller's own MetaTileEntity type (self predicate).
 */
public class SelfElement implements ITypedStructureElement<Object> {

    private final Class<? extends MultiblockControllerBase> controllerClass;
    @Nullable
    private final ResourceLocation controllerId;
    private final StructureElementPreview preview;

    public SelfElement(Class<? extends MultiblockControllerBase> controllerClass) {
        this(controllerClass, null);
    }

    /**
     * Creates a self element constrained to one controller registration.
     *
     * <p>This is needed for controller families that share an implementation class but have
     * different structure variants. Without the ID constraint, the first registered variant is
     * used for structure previews.</p>
     */
    public SelfElement(Class<? extends MultiblockControllerBase> controllerClass,
                       @Nullable ResourceLocation controllerId) {
        this.controllerClass = controllerClass;
        this.controllerId = controllerId;
        this.preview = StructureElementPreview.of(this::getCandidates);
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        TileEntity te = context.getTileEntity();
        if (te instanceof IGregTechTileEntity) {
            MetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            return matches(mte);
        }
        return false;
    }

    @Override
    @NotNull
    public BlockInfo[] getCandidates() {
        if (GregTechAPI.mteManager == null) {
            return new BlockInfo[] { BlockInfo.EMPTY };
        }

        List<BlockInfo> matches = new ArrayList<>();
        for (var registry : GregTechAPI.mteManager.getRegistries()) {
            for (MetaTileEntity metaTileEntity : registry) {
                if (matches(metaTileEntity)) {
                    matches.add(candidateInfo(metaTileEntity));
                }
            }
        }
        return matches.isEmpty() ? new BlockInfo[] { BlockInfo.EMPTY } : matches.toArray(new BlockInfo[0]);
    }

    @Override
    @NotNull
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public boolean isCenter() {
        return true;
    }

    private boolean matches(@Nullable MetaTileEntity metaTileEntity) {
        return metaTileEntity != null && controllerClass.isInstance(metaTileEntity) &&
                (controllerId == null || controllerId.equals(metaTileEntity.metaTileEntityId));
    }

    @NotNull
    private static BlockInfo candidateInfo(@NotNull MetaTileEntity metaTileEntity) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(metaTileEntity);
        holder.getMetaTileEntity().onPlacement();
        holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
        return new BlockInfo(metaTileEntity.getBlock().getDefaultState(), holder);
    }
}
