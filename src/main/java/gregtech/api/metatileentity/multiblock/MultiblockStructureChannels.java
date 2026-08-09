package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.casing.GTStructureChannels;
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

final class MultiblockStructureChannels {

    private MultiblockStructureChannels() {}

    @NotNull
    static List<StructureChannel> collectChannelsFromTemplate(@NotNull PieceTemplate template) {
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

        // Piece selection is a tooling concern rather than a cell predicate, so it
        // cannot be discovered while scanning template elements above.
        if (multiPiecePattern.getToolingPieceCount() > 1) {
            seenNames.add(GTStructureChannels.STRUCTURE_PIECE.getName());
        }

        List<StructureChannel> channels = new ArrayList<>();
        addResolvedChannels(seenNames, channels);
        return channels;
    }

    @NotNull
    static int[] getTemplateChannelRange(
            @NotNull PieceTemplate template,
            @NotNull String channelName) {
        for (PieceTemplate.AisleDef aisle : template.getAisles()) {
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

            PieceTemplate template = piece.getTemplate();
            for (PieceTemplate.AisleDef aisle : template.getAisles()) {
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
            @NotNull PieceTemplate template,
            @NotNull Set<String> out) {
        forEachPreviewGroup(template, (element, group) -> {
            String channelName = group.getChannelName();
            if (channelName != null && !channelName.isEmpty()) {
                out.add(channelName);
            }
        });

        for (PieceTemplate.AisleDef aisle : template.getAisles()) {
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
            StructureChannel channel = StructureChannelRegistry.getByName(name);
            if (channel != null) {
                out.add(channel);
            }
        }
    }

    private static int getTemplateChannelCandidateCount(
            @NotNull PieceTemplate template,
            @NotNull String channelName) {
        final int[] max = {0};
        forEachPreviewGroup(template, (element, group) -> {
            if (channelName.equals(group.getChannelName())) {
                max[0] = Math.max(max[0], group.getCandidates().length);
            }
        });
        return max[0];
    }

    private static void forEachPreviewGroup(
            @NotNull PieceTemplate template,
            @NotNull PreviewGroupConsumer consumer) {
        for (IStructureElement<?>[][] layer : template.getElements()) {
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

    @FunctionalInterface
    private interface PreviewGroupConsumer {

        void accept(@NotNull IStructureElement<?> element,
                    @NotNull StructureElementPreview.CandidateGroup group);
    }
}
