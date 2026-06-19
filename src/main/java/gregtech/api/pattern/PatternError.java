package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import com.cleanroommc.modularui.api.drawable.IKey;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PatternError {

    protected BlockWorldState worldState;

    public void setWorldState(BlockWorldState worldState) {
        this.worldState = worldState;
    }

    public World getWorld() {
        return worldState.getWorld();
    }

    public BlockPos getPos() {
        return worldState.getPos();
    }

    public BlockWorldState getWorldState() {
        return worldState;
    }

    public List<List<ItemStack>> getCandidates() {
        StructureElementPreviewEntry previewEntry = worldState.getPreviewEntry();
        if (previewEntry != null && !previewEntry.getPreview().isEmpty()) {
            List<List<ItemStack>> previewCandidates = candidatesFromPreview(previewEntry.getPreview());
            if (!previewCandidates.isEmpty()) {
                return previewCandidates;
            }
        }

        List<List<ItemStack>> candidates = new ArrayList<>();
        TraceabilityPredicate predicate = worldState.predicate;
        if (predicate == null) {
            return candidates;
        }
        for (TraceabilityPredicate.SimplePredicate common : predicate.common) {
            candidates.add(common.getCandidates());
        }
        for (TraceabilityPredicate.SimplePredicate limited : predicate.limited) {
            candidates.add(limited.getCandidates());
        }
        return candidates;
    }

    private static List<List<ItemStack>> candidatesFromPreview(@NotNull StructureElementPreview preview) {
        List<List<ItemStack>> candidates = new ArrayList<>();
        for (StructureElementPreview.CandidateGroup common : preview.getCommon()) {
            addCandidateGroup(candidates, common);
        }
        for (StructureElementPreview.CandidateGroup limited : preview.getLimited()) {
            addCandidateGroup(candidates, limited);
        }
        return candidates;
    }

    private static void addCandidateGroup(@NotNull List<List<ItemStack>> candidates,
                                          @NotNull StructureElementPreview.CandidateGroup group) {
        List<ItemStack> itemCandidates = new ArrayList<>();
        for (BlockInfo info : group.getCandidates()) {
            ItemStack stack = itemStackFrom(info);
            if (!stack.isEmpty()) {
                itemCandidates.add(stack);
            }
        }
        if (!itemCandidates.isEmpty()) {
            candidates.add(itemCandidates);
        }
    }

    @NotNull
    private static ItemStack itemStackFrom(@Nullable BlockInfo info) {
        if (info == null || info.getBlockState() == null || info.getBlockState().getBlock() == null) {
            return ItemStack.EMPTY;
        }
        if (info.getTileEntity() instanceof IGregTechTileEntity) {
            MetaTileEntity metaTileEntity = ((IGregTechTileEntity) info.getTileEntity()).getMetaTileEntity();
            if (metaTileEntity != null) {
                return metaTileEntity.getStackForm();
            }
        }
        return new ItemStack(Item.getItemFromBlock(info.getBlockState().getBlock()), 1,
                info.getBlockState().getBlock().damageDropped(info.getBlockState()));
    }

    @SideOnly(Side.CLIENT)
    public String getErrorInfo() {
        List<List<ItemStack>> candidates = getCandidates();
        StringBuilder builder = new StringBuilder();
        for (List<ItemStack> candidate : candidates) {
            if (!candidate.isEmpty()) {
                builder.append(candidate.get(0).getDisplayName());
                builder.append(", ");
            }
        }
        builder.append("...");
        return IKey.lang("gregtech.multiblock.pattern.error", builder.toString(), getPosString(worldState.pos)).toString();
    }
    public String getPosString(BlockPos pos) {
        return "[X:"+pos.getX()+" Y:"+pos.getY()+" Z:"+pos.getZ()+"]";
    }
}
