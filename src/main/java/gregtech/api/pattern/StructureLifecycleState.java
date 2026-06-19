package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime-owned snapshot of the controller lifecycle projection.
 *
 * <p>The Minecraft controller still keeps legacy fields for networking and old
 * addon-facing accessors, but this value is the canonical committed structure
 * state published by the server-thread committer.
 */
public final class StructureLifecycleState {

    private static final StructureLifecycleState EMPTY = new StructureLifecycleState(
            false,
            Collections.emptyList(),
            Collections.emptyMap(),
            null,
            new StructureChannelValues(),
            null);

    private final boolean formed;
    @NotNull
    private final List<IMultiblockPart> parts;
    @NotNull
    private final Map<MultiblockAbility<Object>, AbilityInstances> abilities;
    @Nullable
    private final FormedStructureMetadata formedMetadata;
    @NotNull
    private final StructureChannelValues channelValues;
    @Nullable
    private final CommittedStructureGraph committedGraph;

    @NotNull
    public static StructureLifecycleState empty() {
        return EMPTY;
    }

    @NotNull
    public static StructureLifecycleState formed(
            @NotNull List<IMultiblockPart> parts,
            @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities,
            @Nullable FormedStructureMetadata formedMetadata,
            @NotNull StructureChannelValues channelValues,
            @Nullable CommittedStructureGraph committedGraph) {
        return new StructureLifecycleState(
                true, parts, abilities, formedMetadata, channelValues, committedGraph);
    }

    private StructureLifecycleState(
            boolean formed,
            @NotNull List<IMultiblockPart> parts,
            @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities,
            @Nullable FormedStructureMetadata formedMetadata,
            @NotNull StructureChannelValues channelValues,
            @Nullable CommittedStructureGraph committedGraph) {
        this.formed = formed;
        this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
        this.abilities = Collections.unmodifiableMap(copyAbilities(abilities));
        this.formedMetadata = formedMetadata;
        this.channelValues = channelValues.copy();
        this.committedGraph = committedGraph;
    }

    public boolean isFormed() {
        return formed;
    }

    @NotNull
    public List<IMultiblockPart> getParts() {
        return parts;
    }

    @NotNull
    public Map<MultiblockAbility<Object>, AbilityInstances> getAbilities() {
        return Collections.unmodifiableMap(copyAbilities(abilities));
    }

    @Nullable
    public FormedStructureMetadata getFormedMetadata() {
        return formedMetadata;
    }

    @NotNull
    public StructureChannelValues getChannelValues() {
        return channelValues.copy();
    }

    @Nullable
    public CommittedStructureGraph getCommittedGraph() {
        return committedGraph;
    }

    @NotNull
    public StructureLifecycleState withFormedMetadata(
            @Nullable FormedStructureMetadata formedMetadata) {
        return new StructureLifecycleState(
                formed, parts, abilities, formedMetadata, channelValues, committedGraph);
    }

    @NotNull
    public StructureLifecycleState withChannelValues(
            @NotNull StructureChannelValues channelValues) {
        return new StructureLifecycleState(
                formed, parts, abilities, formedMetadata, channelValues, committedGraph);
    }

    @NotNull
    public StructureLifecycleState withCommittedGraph(
            @Nullable CommittedStructureGraph committedGraph) {
        return new StructureLifecycleState(
                formed, parts, abilities, formedMetadata, channelValues, committedGraph);
    }

    @NotNull
    private static Map<MultiblockAbility<Object>, AbilityInstances> copyAbilities(
            @NotNull Map<MultiblockAbility<Object>, AbilityInstances> source) {
        if (source.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<MultiblockAbility<Object>, AbilityInstances> copy = new LinkedHashMap<>();
        for (Map.Entry<MultiblockAbility<Object>, AbilityInstances> entry : source.entrySet()) {
            AbilityInstances instances = new AbilityInstances(entry.getKey());
            for (Object instance : entry.getValue()) {
                instances.add(instance);
            }
            copy.put(entry.getKey(), instances);
        }
        return copy;
    }
}
