package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.casing.StructureChannelRegistry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        TraceabilityPredicate[][][] matches = template.getBlockMatches();
        for (TraceabilityPredicate[][] layer : matches) {
            for (TraceabilityPredicate[] row : layer) {
                for (TraceabilityPredicate predicate : row) {
                    if (predicate == null) continue;
                    collectChannelNames(predicate.common, out);
                    collectChannelNames(predicate.limited, out);
                }
            }
        }
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
        TraceabilityPredicate[][][] matches = template.getBlockMatches();
        for (TraceabilityPredicate[][] layer : matches) {
            for (TraceabilityPredicate[] row : layer) {
                for (TraceabilityPredicate predicate : row) {
                    if (predicate == null) continue;
                    maxCandidates = Math.max(maxCandidates,
                            countChannelCandidates(predicate.common, channelName));
                    maxCandidates = Math.max(maxCandidates,
                            countChannelCandidates(predicate.limited, channelName));
                }
            }
        }
        return maxCandidates;
    }
}
