package gregtech.integration.tconstruct.handler;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.tinkering.ITinkerable;
import slimeknights.tconstruct.library.utils.TagUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Attaches GT capabilities to TiC tools based on their active modifiers.
 *
 * <p>
 * During {@link AttachCapabilitiesEvent}, this handler scans the tool's modifier NBT
 * and creates capability providers for each modifier that has registered a capability
 * factory via {@link #addModifierCap}.
 *
 * <p>
 * <b>Registration:</b> Add this class to your module's {@code getEventBusSubscribers()}
 * return list. The constructor registers the event handler statically.
 */
public final class ToolCapabilityHandler {

    private static final ResourceLocation CAP_KEY = new ResourceLocation("gregtech", "tic_capabilities");

    /** Modifier identifier → capability provider factory. */
    private static final Map<String, Function<ItemStack, ICapabilityProvider>> MODIFIER_CAPS = new HashMap<>();

    private ToolCapabilityHandler() {}

    /**
     * Register a capability factory for a TiC modifier.
     *
     * <p>
     * When a tool has this modifier, the factory will be called to create an
     * {@link ICapabilityProvider} that is attached to the tool's capability chain.
     *
     * @param modifier   the TiC modifier
     * @param capFactory function that creates an ICapabilityProvider from the tool stack
     */
    public static void addModifierCap(@Nullable Modifier modifier,
                                      @Nullable Function<ItemStack, ICapabilityProvider> capFactory) {
        MODIFIER_CAPS.put(modifier.getIdentifier(), capFactory);
    }

    @SubscribeEvent
    public static void onItemCapAttach(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (!(stack.getItem() instanceof ITinkerable)) return;
        if (MODIFIER_CAPS.isEmpty()) return;
        event.addCapability(CAP_KEY, new ToolCapProvider(stack));
    }

    // ==================== Internal Provider ====================

    private static class ToolCapProvider implements ICapabilityProvider {

        private final ItemStack stack;
        private final Map<String, Optional<ICapabilityProvider>> cache = new HashMap<>();

        ToolCapProvider(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean hasCapability(@Nullable Capability<?> capability, @Nullable EnumFacing facing) {
            return getCapability(capability, facing) != null;
        }

        @Nullable
        @Override
        public <T> T getCapability(@Nullable Capability<T> capability, @Nullable EnumFacing facing) {
            for (NBTBase tag : TagUtil.getModifiersTagList(stack)) {
                if (!(tag instanceof NBTTagCompound compound)) continue;
                ModifierNBT modTag = ModifierNBT.readTag(compound);

                ICapabilityProvider capProvider = cache
                        .computeIfAbsent(modTag.identifier, id -> Optional.ofNullable(
                                MODIFIER_CAPS.containsKey(id) ? MODIFIER_CAPS.get(id).apply(stack) : null))
                        .orElse(null);

                if (capProvider != null && capProvider.hasCapability(capability, facing)) {
                    T result = capProvider.getCapability(capability, facing);
                    if (result != null) return result;
                }
            }
            return null;
        }
    }
}
