package gregtech.api.metatileentity.registry;

import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.client.renderer.scene.WorldSceneRenderer;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MBPattern {

    final WorldSceneRenderer sceneRenderer;
    final List<ItemStack> parts;
    final Map<BlockPos, StructureElementPreviewEntry> previewEntries;
    private boolean disposed;

    public MBPattern(final WorldSceneRenderer sceneRenderer, final List<ItemStack> parts) {
        this(sceneRenderer, parts, Collections.emptyMap());
    }

    public MBPattern(final WorldSceneRenderer sceneRenderer, final List<ItemStack> parts,
                     Map<BlockPos, StructureElementPreviewEntry> previewEntries) {
        this.sceneRenderer = sceneRenderer;
        this.parts = parts;
        this.previewEntries = Collections.unmodifiableMap(new HashMap<>(previewEntries));
    }

    public List<ItemStack> getParts() {
        return parts;
    }

    public WorldSceneRenderer getSceneRenderer() {
        return sceneRenderer;
    }

    /**
     * Release the renderer's GPU resources. JEI preview patterns are replaced as
     * the active recipe or channel configuration changes, so relying on GC here
     * would leave FBO/VBO allocations alive indefinitely.
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        sceneRenderer.dispose();
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Nullable
    public StructureElementPreviewEntry getPreviewEntry(@NotNull BlockPos pos) {
        return previewEntries.get(pos);
    }
}
