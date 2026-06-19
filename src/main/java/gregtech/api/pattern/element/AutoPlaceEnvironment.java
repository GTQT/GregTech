package gregtech.api.pattern.element;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Context available to one structure element during survival construction.
 */
public final class AutoPlaceEnvironment {

    @NotNull
    private final IItemSource source;

    @Nullable
    private final EntityPlayer actor;

    @NotNull
    private final Consumer<ITextComponent> chatter;

    public AutoPlaceEnvironment(@NotNull IItemSource source,
                                @Nullable EntityPlayer actor,
                                @NotNull Consumer<ITextComponent> chatter) {
        this.source = source;
        this.actor = actor;
        this.chatter = chatter;
    }

    @NotNull
    public static AutoPlaceEnvironment create(@NotNull IItemSource source,
                                              @Nullable EntityPlayer actor) {
        return new AutoPlaceEnvironment(source, actor, IStructureElement.playerChatter(actor));
    }

    @NotNull
    public IItemSource getSource() {
        return source;
    }

    @Nullable
    public EntityPlayer getActor() {
        return actor;
    }

    @NotNull
    public Consumer<ITextComponent> getChatter() {
        return chatter;
    }

    @NotNull
    public AutoPlaceEnvironment withSource(@NotNull IItemSource source) {
        return new AutoPlaceEnvironment(source, actor, chatter);
    }

    @NotNull
    public AutoPlaceEnvironment withActor(@Nullable EntityPlayer actor) {
        return new AutoPlaceEnvironment(source, actor, chatter);
    }

    @NotNull
    public AutoPlaceEnvironment withChatter(@NotNull Consumer<ITextComponent> chatter) {
        return new AutoPlaceEnvironment(source, actor, chatter);
    }
}
