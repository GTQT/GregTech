package gregtech.client.utils;

import net.minecraft.util.BlockRenderLayer;

import com.creativemd.creativecore.client.mods.optifine.OptifineHelper;
import com.creativemd.littletiles.client.render.cache.LayeredRenderBoxCache;
import com.creativemd.littletiles.client.render.tile.LittleRenderBox;

import java.util.Iterator;
import java.util.List;

public class LittleTilesHooks {

    public static LayeredRenderBoxCache initLayeredRenderBoxCache() {
        return new BloomLayeredRenderBoxCache();
    }

    public static class BloomLayeredRenderBoxCache extends LayeredRenderBoxCache {

        private List<LittleRenderBox> solid = null;
        private List<LittleRenderBox> cutout_mipped = null;
        private List<LittleRenderBox> cutout = null;
        private List<LittleRenderBox> bloom = null;
        private List<LittleRenderBox> translucent = null;

        public List<LittleRenderBox> get(BlockRenderLayer layer) {
            if (layer == BloomEffectUtil.getBloomLayer()) {
                return bloom;
            }
            return switch (layer) {
                case SOLID -> solid;
                case CUTOUT_MIPPED -> cutout_mipped;
                case CUTOUT -> cutout;
                case TRANSLUCENT -> translucent;
            };
        }

        public void set(List<LittleRenderBox> cubes, BlockRenderLayer layer) {
            if (layer == BloomEffectUtil.getBloomLayer()) {
                bloom = cubes;
            }
            switch (layer) {
                case SOLID:
                    solid = cubes;
                    break;
                case CUTOUT_MIPPED:
                    cutout_mipped = cubes;
                    break;
                case CUTOUT:
                    cutout = cubes;
                    break;
                case TRANSLUCENT:
                    translucent = cubes;
                    break;
            }
        }

        public boolean needUpdate() {
            return solid == null || cutout_mipped == null || cutout == null || translucent == null || bloom == null;
        }

        public void clear() {
            solid = null;
            cutout_mipped = null;
            cutout = null;
            translucent = null;
            bloom = null;
        }

        public void sort() {
            if (!OptifineHelper.isActive())
                return;

            for (Iterator<LittleRenderBox> iterator = solid.iterator(); iterator.hasNext();) {
                LittleRenderBox littleRenderingCube = iterator.next();
                if (littleRenderingCube.needsResorting) {
                    cutout_mipped.add(littleRenderingCube);
                    iterator.remove();
                }
            }
        }
    }
}
