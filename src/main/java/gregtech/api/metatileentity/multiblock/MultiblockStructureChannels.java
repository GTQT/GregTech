package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.casing.StructureChannelRegistry;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

final class MultiblockStructureChannels {

    private MultiblockStructureChannels() {}

    @NotNull
    static List<StructureChannel> collectChannelsFromTemplate(@NotNull BlockPatternTemplate template) {
        Set<String> seen = new LinkedHashSet<>();
        collectChannelsFromTemplateInto(template, seen);
        List<StructureChannel> result = new ArrayList<>();
        addResolvedChannels(seen, result);
        return result;
    }

    @NotNull
    static List<StructureChannel> collectChannelsFromMultiPiece(@Nullable MultiPiecePattern multiPiecePattern) {
        if (multiPiecePattern == null) return Collections.emptyList();
        Set<String> seenNames = new LinkedHashSet<>();
        for (StructurePiece piece : multiPiecePattern.getPieceList()) {
            if (piece instanceof RepeatGroupPiece) {
                String[] channelNames = ((RepeatGroupPiece) piece).getRepeatChannelNames();
                if (channelNames != null) {
                    for (String name : channelNames) {
                        if (name != null && !name.isEmpty()) {
                            seenNames.add(name);
                        }
                    }
                }
            }

            collectChannelsFromTemplateInto(piece.getTemplate(), seenNames);
        }

        List<StructureChannel> channels = new ArrayList<>();
        addResolvedChannels(seenNames, channels);
        return channels;
    }

    @NotNull
    static int[] getTemplateChannelRange(
            @NotNull BlockPatternTemplate template,
            @NotNull String channelName) {
        for (BlockPatternTemplate.AisleDef aisle : template.getAisles()) {
            if (channelName.equals(aisle.channelName())) {
                return new int[] { aisle.minRepeat(), aisle.maxRepeat() };
            }
        }

        int maxCandidates = getTemplateChannelCandidateCount(template, channelName);
        if (maxCandidates > 0) {
            // Channel value semantics: 0 = auto, 1..N = specific candidate (1-based).
            return new int[] { 0, maxCandidates };
        }
        return new int[] { 0, 0 };
    }

    @NotNull
    static int[] getChannelRangeFromMultiPiece(
            @Nullable MultiPiecePattern multiPiecePattern,
            @NotNull StructureChannel channel) {
        if (multiPiecePattern == null) {
            return new int[] { 0, 0 };
        }
        String channelName = channel.getName();
        if (channel == GTStructureChannels.STRUCTURE_PIECE) {
            return new int[] { 0, multiPiecePattern.getToolingPieceCount() };
        }

        for (StructurePiece piece : multiPiecePattern.getPieceList()) {
            if (piece instanceof RepeatGroupPiece) {
                String[] channelNames = ((RepeatGroupPiece) piece).getRepeatChannelNames();
                int[][] ranges = ((RepeatGroupPiece) piece).getRepeatRanges();
                if (channelNames != null && ranges != null) {
                    for (int i = 0; i < channelNames.length; i++) {
                        if (channelName.equals(channelNames[i])) {
                            return new int[] { ranges[i][0], ranges[i][1] };
                        }
                    }
                }
            }

            BlockPatternTemplate template = piece.getTemplate();
            for (BlockPatternTemplate.AisleDef aisle : template.getAisles()) {
                if (channelName.equals(aisle.channelName())) {
                    return new int[] { aisle.minRepeat(), aisle.maxRepeat() };
                }
            }

            int maxCandidates = getTemplateChannelCandidateCount(template, channelName);
            if (maxCandidates > 0) {
                return new int[] { 0, maxCandidates };
            }
        }
        return new int[] { 0, 0 };
    }

    private static void collectChannelsFromTemplateInto(
            @NotNull BlockPatternTemplate template,
            @NotNull Set<String> out) {
        forEachPreviewGroup(template, (element, group) -> {
            String channelName = group.getChannelName();
            if (channelName != null && !channelName.isEmpty()) {
                out.add(channelName);
            }
        });

        forEachElementWithoutPreviewChannel(template, element -> {
            TraceabilityPredicate predicate = element.toPredicate();
            if (predicate != null) {
                collectChannelNames(predicate.common, out);
                collectChannelNames(predicate.limited, out);
            }
        });

        for (BlockPatternTemplate.AisleDef aisle : template.getAisles()) {
            String name = aisle.channelName();
            if (name != null && !name.isEmpty()) {
                out.add(name);
            }
        }
    }

    private static void addResolvedChannels(
            @NotNull Set<String> channelNames,
            @NotNull List<StructureChannel> out) {
        for (String name : channelNames) {
            StructureChannel channel = StructureChannelRegistry.resolve(name);
            if (channel != null) {
                out.add(channel);
            }
        }
    }

    private static void collectChannelNames(
            @NotNull List<TraceabilityPredicate.SimplePredicate> predicates,
            @NotNull Set<String> out) {
        for (TraceabilityPredicate.SimplePredicate sp : predicates) {
            if (sp.channelName != null && !sp.channelName.isEmpty()) {
                out.add(sp.channelName);
            }
        }
    }

    private static int countChannelCandidates(
            @NotNull List<TraceabilityPredicate.SimplePredicate> predicates,
            @NotNull String channelName) {
        for (TraceabilityPredicate.SimplePredicate sp : predicates) {
            if (channelName.equals(sp.channelName) && sp.candidates != null) {
                return sp.candidates.get().length;
            }
        }
        return 0;
    }

    private static int getTemplateChannelCandidateCount(
            @NotNull BlockPatternTemplate template,
            @NotNull String channelName) {
        int maxCandidates = 0;
        final int[] typedMax = {0};
        forEachPreviewGroup(template, (element, group) -> {
            if (channelName.equals(group.getChannelName())) {
                typedMax[0] = Math.max(typedMax[0], group.getCandidates().length);
            }
        });
        if (typedMax[0] > 0) {
            return typedMax[0];
        }

        final int[] legacyMax = {0};
        forEachElementWithoutPreviewChannel(template, element -> {
            TraceabilityPredicate predicate = element.toPredicate();
            if (predicate != null) {
                legacyMax[0] = Math.max(legacyMax[0],
                        countChannelCandidates(predicate.common, channelName));
                legacyMax[0] = Math.max(legacyMax[0],
                        countChannelCandidates(predicate.limited, channelName));
            }
        });
        maxCandidates = Math.max(maxCandidates, legacyMax[0]);
        return maxCandidates;
    }

    private static void forEachPreviewGroup(
            @NotNull BlockPatternTemplate template,
            @NotNull PreviewGroupConsumer consumer) {
        for (IStructureElement<?>[][] layer : template.getDelegate().getElements()) {
            for (IStructureElement<?>[] row : layer) {
                for (IStructureElement<?> element : row) {
                    if (element == null) continue;
                    StructureElementPreview preview = element.getPreview();
                    for (StructureElementPreview.CandidateGroup group : preview.getCommon()) {
                        consumer.accept(element, group);
                    }
                    for (StructureElementPreview.CandidateGroup group : preview.getLimited()) {
                        consumer.accept(element, group);
                    }
                }
            }
        }
    }

    private static void forEachElementWithoutPreviewChannel(
            @NotNull BlockPatternTemplate template,
            @NotNull Consumer<IStructureElement<?>> consumer) {
        for (IStructureElement<?>[][] layer : template.getDelegate().getElements()) {
            for (IStructureElement<?>[] row : layer) {
                for (IStructureElement<?> element : row) {
                    if (element != null && !hasPreviewChannel(element.getPreview())) {
                        consumer.accept(element);
                    }
                }
            }
        }
    }

    private static boolean hasPreviewChannel(@NotNull StructureElementPreview preview) {
        for (StructureElementPreview.CandidateGroup group : preview.getCommon()) {
            if (group.getChannelName() != null && !group.getChannelName().isEmpty()) {
                return true;
            }
        }
        for (StructureElementPreview.CandidateGroup group : preview.getLimited()) {
            if (group.getChannelName() != null && !group.getChannelName().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface PreviewGroupConsumer {

        void accept(@NotNull IStructureElement<?> element,
                    @NotNull StructureElementPreview.CandidateGroup group);
    }
}
