package gregtech.api.pattern.element;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Direct-element preview/build metadata.
 *
 * <p>This is the V3 replacement for reading candidate/channel/default metadata
 * from {@link TraceabilityPredicate.SimplePredicate}. Legacy predicates are
 * still adapted into this shape, but new elements can expose preview and build
 * behavior without converting themselves into a predicate.
 */
public final class StructureElementPreview {

    @NotNull
    private final List<CandidateGroup> limited;
    @NotNull
    private final List<CandidateGroup> common;

    private StructureElementPreview(@NotNull List<CandidateGroup> limited,
                                    @NotNull List<CandidateGroup> common) {
        this.limited = Collections.unmodifiableList(new ArrayList<>(limited));
        this.common = Collections.unmodifiableList(new ArrayList<>(common));
    }

    @NotNull
    public static StructureElementPreview of(@NotNull Supplier<BlockInfo[]> candidates) {
        return builder().common(candidates).build();
    }

    @NotNull
    public static StructureElementPreview of(@Nullable BlockInfo[] candidates) {
        return of(() -> candidates);
    }

    @NotNull
    public static StructureElementPreview empty() {
        return builder().build();
    }

    @NotNull
    public static StructureElementPreview fromPredicate(@NotNull TraceabilityPredicate predicate) {
        Builder builder = builder();
        for (TraceabilityPredicate.SimplePredicate simple : predicate.limited) {
            builder.limited(simple);
        }
        for (TraceabilityPredicate.SimplePredicate simple : predicate.common) {
            builder.common(simple);
        }
        return builder.build();
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    public List<CandidateGroup> getLimited() {
        return limited;
    }

    @NotNull
    public List<CandidateGroup> getCommon() {
        return common;
    }

    public boolean isEmpty() {
        return limited.isEmpty() && common.isEmpty();
    }

    public static final class Builder {

        @NotNull
        private final List<CandidateGroup> limited = new ArrayList<>();
        @NotNull
        private final List<CandidateGroup> common = new ArrayList<>();

        private Builder() {}

        @NotNull
        public Builder common(@NotNull Supplier<BlockInfo[]> candidates) {
            common.add(CandidateGroup.builder(candidates).build());
            return this;
        }

        @NotNull
        public Builder common(@Nullable BlockInfo[] candidates) {
            return common(() -> candidates);
        }

        @NotNull
        public Builder common(@NotNull CandidateGroup group) {
            common.add(group);
            return this;
        }

        @NotNull
        public Builder common(@NotNull TraceabilityPredicate.SimplePredicate predicate) {
            common.add(CandidateGroup.fromPredicate(predicate));
            return this;
        }

        @NotNull
        public Builder limited(@NotNull Supplier<BlockInfo[]> candidates,
                               int minGlobalCount,
                               int maxGlobalCount,
                               int minLayerCount,
                               int maxLayerCount,
                               int previewCount) {
            limited.add(CandidateGroup.builder(candidates)
                    .global(minGlobalCount, maxGlobalCount)
                    .layer(minLayerCount, maxLayerCount)
                    .previewCount(previewCount)
                    .build());
            return this;
        }

        @NotNull
        public Builder limited(@NotNull CandidateGroup group) {
            limited.add(group);
            return this;
        }

        @NotNull
        public Builder limited(@NotNull TraceabilityPredicate.SimplePredicate predicate) {
            limited.add(CandidateGroup.fromPredicate(predicate));
            return this;
        }

        @NotNull
        public StructureElementPreview build() {
            return new StructureElementPreview(limited, common);
        }
    }

    public static final class CandidateGroup {

        @NotNull
        private final Supplier<BlockInfo[]> candidates;
        private final int minGlobalCount;
        private final int maxGlobalCount;
        private final int minLayerCount;
        private final int maxLayerCount;
        private final int previewCount;
        @Nullable
        private final String channelName;
        @Nullable
        private final Supplier<? extends MetaTileEntity> defaultCandidate;
        @Nullable
        private final TraceabilityPredicate.SimplePredicate legacyPredicate;

        private CandidateGroup(@NotNull Supplier<BlockInfo[]> candidates,
                               int minGlobalCount,
                               int maxGlobalCount,
                               int minLayerCount,
                               int maxLayerCount,
                               int previewCount,
                               @Nullable String channelName,
                               @Nullable Supplier<? extends MetaTileEntity> defaultCandidate,
                               @Nullable TraceabilityPredicate.SimplePredicate legacyPredicate) {
            this.candidates = candidates;
            this.minGlobalCount = minGlobalCount;
            this.maxGlobalCount = maxGlobalCount;
            this.minLayerCount = minLayerCount;
            this.maxLayerCount = maxLayerCount;
            this.previewCount = previewCount;
            this.channelName = channelName;
            this.defaultCandidate = defaultCandidate;
            this.legacyPredicate = legacyPredicate;
        }

        @NotNull
        public static CandidateGroup fromPredicate(@NotNull TraceabilityPredicate.SimplePredicate predicate) {
            return builder(predicate.candidates == null ? null : predicate.candidates)
                    .global(predicate.minGlobalCount, predicate.maxGlobalCount)
                    .layer(predicate.minLayerCount, predicate.maxLayerCount)
                    .previewCount(predicate.previewCount)
                    .channel(predicate.channelName)
                    .defaultCandidate(predicate.defaultCandidate)
                    .legacyPredicate(predicate)
                    .build();
        }

        @NotNull
        public static CandidateGroup of(@NotNull Supplier<BlockInfo[]> candidates) {
            return builder(candidates).build();
        }

        @NotNull
        public static CandidateGroup of(@Nullable BlockInfo[] candidates) {
            return builder(candidates).build();
        }

        @NotNull
        public static CandidateGroup.Builder builder(@Nullable Supplier<BlockInfo[]> candidates) {
            return new CandidateGroup.Builder(candidates);
        }

        @NotNull
        public static CandidateGroup.Builder builder(@Nullable BlockInfo[] candidates) {
            return builder(() -> candidates);
        }

        @NotNull
        public BlockInfo[] getCandidates() {
            BlockInfo[] infos = candidates.get();
            return infos == null ? new BlockInfo[0] : Arrays.copyOf(infos, infos.length);
        }

        public int getMinGlobalCount() {
            return minGlobalCount;
        }

        public int getMaxGlobalCount() {
            return maxGlobalCount;
        }

        public int getMinLayerCount() {
            return minLayerCount;
        }

        public int getMaxLayerCount() {
            return maxLayerCount;
        }

        public int getPreviewCount() {
            return previewCount;
        }

        @Nullable
        public String getChannelName() {
            return channelName;
        }

        @Nullable
        public Supplier<? extends MetaTileEntity> getDefaultCandidate() {
            return defaultCandidate;
        }

        @Nullable
        public TraceabilityPredicate.SimplePredicate getLegacyPredicate() {
            return legacyPredicate;
        }

        @NotNull
        public CandidateGroup withChannel(@Nullable String channelName) {
            return new CandidateGroup(candidates, minGlobalCount, maxGlobalCount,
                    minLayerCount, maxLayerCount, previewCount, channelName,
                    defaultCandidate, legacyPredicate);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CandidateGroup)) return false;
            CandidateGroup other = (CandidateGroup) obj;
            return candidates == other.candidates
                    && minGlobalCount == other.minGlobalCount
                    && maxGlobalCount == other.maxGlobalCount
                    && minLayerCount == other.minLayerCount
                    && maxLayerCount == other.maxLayerCount
                    && previewCount == other.previewCount
                    && Objects.equals(channelName, other.channelName)
                    && defaultCandidate == other.defaultCandidate
                    && legacyPredicate == other.legacyPredicate;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(candidates);
            result = 31 * result + minGlobalCount;
            result = 31 * result + maxGlobalCount;
            result = 31 * result + minLayerCount;
            result = 31 * result + maxLayerCount;
            result = 31 * result + previewCount;
            result = 31 * result + Objects.hashCode(channelName);
            result = 31 * result + System.identityHashCode(defaultCandidate);
            result = 31 * result + System.identityHashCode(legacyPredicate);
            return result;
        }

        public static final class Builder {

            @Nullable
            private final Supplier<BlockInfo[]> candidates;
            private int minGlobalCount = -1;
            private int maxGlobalCount = -1;
            private int minLayerCount = -1;
            private int maxLayerCount = -1;
            private int previewCount = -1;
            @Nullable
            private String channelName;
            @Nullable
            private Supplier<? extends MetaTileEntity> defaultCandidate;
            @Nullable
            private TraceabilityPredicate.SimplePredicate legacyPredicate;

            private Builder(@Nullable Supplier<BlockInfo[]> candidates) {
                this.candidates = candidates;
            }

            @NotNull
            public Builder global(int min, int max) {
                this.minGlobalCount = min;
                this.maxGlobalCount = max;
                return this;
            }

            @NotNull
            public Builder layer(int min, int max) {
                this.minLayerCount = min;
                this.maxLayerCount = max;
                return this;
            }

            @NotNull
            public Builder previewCount(int previewCount) {
                this.previewCount = previewCount;
                return this;
            }

            @NotNull
            public Builder channel(@Nullable String channelName) {
                this.channelName = channelName;
                return this;
            }

            @NotNull
            public Builder defaultCandidate(@Nullable Supplier<? extends MetaTileEntity> defaultCandidate) {
                this.defaultCandidate = defaultCandidate;
                return this;
            }

            @NotNull
            private Builder legacyPredicate(@Nullable TraceabilityPredicate.SimplePredicate legacyPredicate) {
                this.legacyPredicate = legacyPredicate;
                return this;
            }

            @NotNull
            public CandidateGroup build() {
                Supplier<BlockInfo[]> candidateSupplier = candidates == null ? () -> new BlockInfo[0] : candidates;
                return new CandidateGroup(candidateSupplier, minGlobalCount, maxGlobalCount,
                        minLayerCount, maxLayerCount, previewCount, channelName,
                        defaultCandidate, legacyPredicate);
            }
        }
    }
}
