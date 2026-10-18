package gregtech.api.items.metaitem.stats;

import net.minecraft.util.ResourceLocation;

public interface IItemRendererManager extends IItemComponent {

    void onRendererRegistry(ResourceLocation location);
}
