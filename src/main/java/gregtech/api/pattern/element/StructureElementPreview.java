package gregtech.api.pattern.element;

import gregtech.api.metatileentity.MetaTileEntity;
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
 * <p>This is the V3 typed view of an element's candidate blocks and limits.
 * Elements build instances directly via {@link #builder()} without going
 * through {@code TraceabilityPredicate.SimplePredicate}.
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
        @NotNull
        private final Supplier<List<String>> tooltip;

        private CandidateGroup(@NotNull Supplier<BlockInfo[]> candidates,
                               int minGlobalCount,
                               int maxGlobalCount,
                               int minLayerCount,
                               int maxLayerCount,
                               int previewCount,
                               @Nullable String channelName,
                               @Nullable Supplier<? extends MetaTileEntity> defaultCandidate,
                               @NotNull Supplier<List<String>> tooltip) {
            this.candidates = candidates;
            this.minGlobalCount = minGlobalCount;
            this.maxGlobalCount = maxGlobalCount;
            this.minLayerCount = minLayerCount;
            this.maxLayerCount = maxLayerCount;
            this.previewCount = previewCount;
            this.channelName = channelName;
            this.defaultCandidate = defaultCandidate;
            this.tooltip = tooltip;
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

        @NotNull
        public List<String> getTooltip() {
            List<String> result = tooltip.get();
            return result == null ? Collections.emptyList() : new ArrayList<>(result);
        }

        @NotNull
        public CandidateGroup withChannel(@Nullable String channelName) {
            return new CandidateGroup(candidates, minGlobalCount, maxGlobalCount,
                    minLayerCount, maxLayerCount, previewCount, channelName,
                    defaultCandidate, tooltip);
        }

        @NotNull
        public CandidateGroup withDefaultCandidate(@Nullable Supplier<? extends MetaTileEntity> defaultCandidate) {
            return new CandidateGroup(candidates, minGlobalCount, maxGlobalCount,
                    minLayerCount, maxLayerCount, previewCount, channelName,
                    defaultCandidate, tooltip);
        }

        @NotNull
        public CandidateGroup withAdditionalTooltip(@NotNull List<String> additionalTooltip) {
            if (additionalTooltip.isEmpty()) {
                return this;
            }
            return new CandidateGroup(candidates, minGlobalCount, maxGlobalCount,
                    minLayerCount, maxLayerCount, previewCount, channelName,
                    defaultCandidate, () -> {
                        List<String> merged = new ArrayList<>(getTooltip());
                        merged.addAll(additionalTooltip);
                        return merged;
                    });
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
                    && tooltip == other.tooltip;
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
            result = 31 * result + System.identityHashCode(tooltip);
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
            @NotNull
            private Supplier<List<String>> tooltip = Collections::emptyList;

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
            public Builder tooltip(@NotNull Supplier<List<String>> tooltip) {
                this.tooltip = tooltip;
                return this;
            }

            @NotNull
            public CandidateGroup build() {
                Supplier<BlockInfo[]> candidateSupplier = candidates == null ? () -> new BlockInfo[0] : candidates;
                return new CandidateGroup(candidateSupplier, minGlobalCount, maxGlobalCount,
                        minLayerCount, maxLayerCount, previewCount, channelName,
                        defaultCandidate, tooltip);
            }
        }
    }
}
