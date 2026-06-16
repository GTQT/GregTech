package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockStructureChannelsTest {

    private static BlockInfo stoneInfo;
    private static BlockInfo dirtInfo;

    @BeforeAll
    static void bootstrapMinecraft() {
        if (!Bootstrap.isRegistered()) {
            Bootstrap.register();
        }
        stoneInfo = new BlockInfo(Blocks.STONE.getDefaultState(), null);
        dirtInfo = new BlockInfo(Blocks.DIRT.getDefaultState(), null);
    }

    @Test
    void channelDiscoveryAndRangePreferDirectPreviewGroups() {
        String channelName = GTStructureChannels.STRUCTURE_TIER.getName();
        BlockPatternTemplate template = new BlockPatternTemplate(singleCellTemplate(
                new DirectPreviewChannelElement(channelName, stoneInfo, dirtInfo)));

        List<StructureChannel> channels = MultiblockStructureChannels.collectChannelsFromTemplate(template);
        int[] range = MultiblockStructureChannels.getTemplateChannelRange(template, channelName);

        assertEquals(1, channels.size());
        assertEquals(channelName, channels.get(0).getName());
        assertArrayEquals(new int[] {0, 2}, range);
    }

    @Test
    void directPreviewChannelDoesNotRequestLegacyPredicateFallback() {
        String channelName = GTStructureChannels.STRUCTURE_TIER.getName();
        BlockPatternTemplate template = new BlockPatternTemplate(singleCellTemplate(
                new ThrowingLegacyPredicateChannelElement(channelName, stoneInfo, dirtInfo)));

        List<StructureChannel> channels = MultiblockStructureChannels.collectChannelsFromTemplate(template);
        int[] range = MultiblockStructureChannels.getTemplateChannelRange(template, channelName);

        assertEquals(1, channels.size());
        assertEquals(channelName, channels.get(0).getName());
        assertArrayEquals(new int[] {0, 2}, range);
    }

    @NotNull
    private static PieceTemplate singleCellTemplate(@NotNull IStructureElement<?> element) {
        return new PieceTemplate(
                new IStructureElement<?>[][][] {
                        {
                                { element }
                        }
                },
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[][] {
                        { 1, 1 }
                },
                null,
                new int[] {0, 0, 0, 0, 0},
                null);
    }

    private static class DirectPreviewChannelElement implements IStructureElement<Object> {

        @NotNull
        private final BlockInfo[] candidates;
        @NotNull
        private final StructureElementPreview preview;

        private DirectPreviewChannelElement(@NotNull String channelName, @NotNull BlockInfo... candidates) {
            this.candidates = candidates;
            this.preview = StructureElementPreview.builder()
                    .common(StructureElementPreview.CandidateGroup.builder(this::getCandidates)
                            .channel(channelName)
                            .build())
                    .build();
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            return true;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return candidates;
        }

        @NotNull
        @Override
        public StructureElementPreview getPreview() {
            return preview;
        }

    }

    private static final class ThrowingLegacyPredicateChannelElement extends DirectPreviewChannelElement {

        private ThrowingLegacyPredicateChannelElement(@NotNull String channelName, @NotNull BlockInfo... candidates) {
            super(channelName, candidates);
        }

        @Override
        public gregtech.api.pattern.TraceabilityPredicate toPredicate() {
            throw new AssertionError("Direct preview channel metadata should avoid legacy predicate fallback");
        }
    }
}
