package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Player-facing, localized description of why a controller's structure is not formed.
 *
 * <p>The report combines the immutable {@link StructureFailureTrace} snapshot of the controller
 * with a live read of the failing position, and every line is a translation component, so the
 * client resolves the language and the text appears in the receiving player's own language. That
 * also avoids invoking client-only helpers such as {@link PatternError#getErrorInfo()} on the
 * server thread.</p>
 *
 * <p>The live {@link PatternError} is consulted only for the expected-block preview, and only
 * while it still points at the reported cell: the block-state buffer behind it is recycled by the
 * next pattern check, so a report built after another structure operation would otherwise
 * describe a different place. Position, failure kind and the recorded detail always come from the
 * failure trace and stay accurate.</p>
 */
public final class StructureFailureReport {

    private static final String SEPARATOR = "============================";
    /** Maximum number of expected-block candidate groups listed individually. */
    private static final int MAX_EXPECTED_BLOCK_GROUPS = 6;
    /** Maximum number of missing-ability entries listed individually. */
    private static final int MAX_MISSING_ABILITY_ROWS = 4;

    private StructureFailureReport() {}

    /**
     * Send the complete failure report to {@code player}'s chat, framed by separators.
     *
     * <p>Call this while the controller's structure is not formed. Build the report before any
     * further structure operation of the same controller for the freshest candidate preview.</p>
     *
     * @param extraAdviceLangKey optional tool-specific hint appended after the generic advice
     */
    public static void sendTo(@NotNull EntityPlayer player,
                              @NotNull MultiblockControllerBase controller,
                              @Nullable World world,
                              @Nullable String extraAdviceLangKey) {
        player.sendMessage(separator());
        for (ITextComponent line : build(controller, world)) {
            player.sendMessage(line);
        }
        if (extraAdviceLangKey != null) {
            player.sendMessage(translation(TextFormatting.GRAY, extraAdviceLangKey));
        }
        player.sendMessage(separator());
    }

    /** Send the complete failure report without a tool-specific hint. */
    public static void sendTo(@NotNull EntityPlayer player,
                              @NotNull MultiblockControllerBase controller,
                              @Nullable World world) {
        sendTo(player, controller, world, null);
    }

    /**
     * Build the report body, one line per fact, in reading order. The controller name, failure
     * kind, problem position, current block, expected blocks, missing abilities, reason and advice
     * are each omitted when unknown, so a caller can also reuse the lines outside chat.
     */
    @NotNull
    public static List<ITextComponent> build(@NotNull MultiblockControllerBase controller,
                                             @Nullable World world) {
        StructureFailureTrace failure = getLastFailure(controller);
        PatternError error = failure == null ? controller.getLastStructureError() : failure.getError();
        BlockPos errorPos = resolveErrorPos(failure, error);

        List<ITextComponent> lines = new ArrayList<>();
        lines.add(translation(TextFormatting.RED, "gregtech.multiblock.pattern.failure.header",
                new TextComponentTranslation(controller.getMetaFullName()),
                new TextComponentString(formatPos(controller.getPos()))));
        if (failure != null) {
            lines.add(translation(TextFormatting.RED, "gregtech.multiblock.pattern.failure.kind_label",
                    new TextComponentTranslation(kindLangKey(failure.getKind()))));
        }
        if (errorPos != null) {
            lines.add(translation(TextFormatting.GRAY, "gregtech.multiblock.pattern.failure.location",
                    new TextComponentString(formatPos(errorPos)),
                    new TextComponentString(formatOffset(controller.getPos(), errorPos))));
        }
        String actualBlock = errorPos == null || world == null ? null : describeBlockAt(world, errorPos);
        if (actualBlock != null) {
            lines.add(translation(TextFormatting.GRAY, "gregtech.multiblock.pattern.failure.actual",
                    actualBlock));
        } else if (failure != null && failure.getActual() != null && !failure.getActual().isEmpty()) {
            // Lifecycle failures record a diagnostic sentence instead of a block at a position.
            lines.add(translation(TextFormatting.GRAY, "gregtech.multiblock.pattern.failure.detail",
                    failure.getActual()));
        }
        appendExpectedBlocks(lines, failure, error, errorPos);
        appendMissingAbilities(lines, controller);
        ITextComponent reason = describePatternError(error);
        if (reason != null) {
            lines.add(translation(TextFormatting.YELLOW, "gregtech.multiblock.pattern.failure.reason", reason));
        }
        if (failure == null && error == null) {
            lines.add(translation(TextFormatting.GRAY, "gregtech.multiblock.pattern.failure.no_trace"));
        }
        lines.add(translation(TextFormatting.GRAY, "gregtech.multiblock.pattern.failure.advice"));
        return lines;
    }

    /**
     * List the blocks the failing cell accepts, one line per candidate group; alternatives within
     * a group are separated by {@code " / "}.
     */
    private static void appendExpectedBlocks(@NotNull List<ITextComponent> lines,
                                             @Nullable StructureFailureTrace failure,
                                             @Nullable PatternError error,
                                             @Nullable BlockPos errorPos) {
        List<List<ItemStack>> groups = candidateGroups(error, errorPos);
        if (!groups.isEmpty()) {
            int listed = Math.min(groups.size(), MAX_EXPECTED_BLOCK_GROUPS);
            for (int i = 0; i < listed; i++) {
                lines.add(translation(TextFormatting.YELLOW,
                        "gregtech.multiblock.pattern.failure.expected_group",
                        joinCandidateNames(groups.get(i))));
            }
            if (groups.size() > listed) {
                lines.add(translation(TextFormatting.YELLOW,
                        "gregtech.multiblock.pattern.failure.expected_more",
                        Integer.toString(groups.size() - listed)));
            }
            return;
        }
        String expected = failure == null ? null : failure.getExpected();
        if (expected != null && !expected.isEmpty()) {
            lines.add(translation(TextFormatting.YELLOW,
                    "gregtech.multiblock.pattern.failure.expected_group", expected));
        }
    }

    /** List the hatch/part abilities the controller is still missing. */
    private static void appendMissingAbilities(@NotNull List<ITextComponent> lines,
                                               @NotNull MultiblockControllerBase controller) {
        Map<String, Integer> missing = controller.getMissingStructureAbilities();
        if (missing.isEmpty()) return;
        int listed = 0;
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            if (listed >= MAX_MISSING_ABILITY_ROWS) {
                lines.add(translation(TextFormatting.RED,
                        "gregtech.multiblock.pattern.failure.missing_more",
                        Integer.toString(missing.size() - listed)));
                break;
            }
            lines.add(translation(TextFormatting.RED,
                    "gregtech.multiblock.pattern.failure.missing_ability",
                    Integer.toString(entry.getValue()),
                    new TextComponentTranslation("gregtech.multiblock.ability." + entry.getKey())));
            listed++;
        }
    }

    /**
     * Read the expected-block alternatives from the live pattern error, but only while it still
     * points at the reported cell. A mismatching position means the shared block-state buffer has
     * been recycled by a later check, so its preview describes another place.
     */
    @NotNull
    private static List<List<ItemStack>> candidateGroups(@Nullable PatternError error,
                                                         @Nullable BlockPos errorPos) {
        if (error == null) return Collections.emptyList();
        try {
            if (errorPos != null && !errorPos.equals(error.getPos())) {
                return Collections.emptyList();
            }
            List<List<ItemStack>> groups = error.getCandidates();
            return groups == null ? Collections.emptyList() : groups;
        } catch (RuntimeException ignored) {
            // Third-party preview entries can expose an incomplete PatternError.
            return Collections.emptyList();
        }
    }

    @NotNull
    private static String joinCandidateNames(@NotNull List<ItemStack> candidates) {
        StringJoiner joiner = new StringJoiner(" / ");
        for (ItemStack stack : candidates) {
            if (stack != null && !stack.isEmpty()) {
                joiner.add(stack.getDisplayName());
            }
        }
        return joiner.toString();
    }

    /**
     * Turn the error subtype into its player-facing reason, mirroring the wording used by
     * {@code MultiblockUIBuilder} so chat and the controller UI stay consistent. The text is
     * resolved client-side from the key, so the reason is localized and the client-only
     * {@link PatternError#getErrorInfo()} is never called on a dedicated server.
     */
    @Nullable
    private static ITextComponent describePatternError(@Nullable PatternError error) {
        if (error instanceof PatternStringError stringError) {
            String key = stringError.translateKey;
            if (key == null || key.isEmpty()) return null;
            // Some call sites store a raw diagnostic sentence in this field, which must not
            // be run through the language map.
            return isTranslationKey(key) ? new TextComponentTranslation(key)
                    : new TextComponentString(key);
        }
        if (error instanceof CountLimitError countError) {
            return new TextComponentTranslation(
                    "gregtech.multiblock.pattern.failure.reason.limited." + countError.getKind().getIndex(),
                    Integer.toString(countError.getLimit()));
        }
        return null;
    }

    /** True when the text looks like a translation key instead of a raw diagnostic sentence. */
    private static boolean isTranslationKey(@NotNull String text) {
        if (text.indexOf('.') < 0) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '%' || c == '\'' || c == '"') return false;
        }
        return true;
    }

    /**
     * Describe the block occupying the failing position right now, so the player sees what has to
     * be replaced. Returns {@code null} when the chunk is not loaded, letting the caller fall back
     * to the raw detail recorded by the check.
     */
    @Nullable
    private static String describeBlockAt(@NotNull World world, @NotNull BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return null;
        }
        try {
            IBlockState state = world.getBlockState(pos);
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity instanceof IGregTechTileEntity) {
                MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
                if (metaTileEntity != null) {
                    ItemStack stackForm = metaTileEntity.getStackForm();
                    if (!stackForm.isEmpty()) {
                        return describeStack(stackForm, state);
                    }
                }
            }
            return describeStack(new ItemStack(Item.getItemFromBlock(state.getBlock()), 1,
                    state.getBlock().damageDropped(state)), state);
        } catch (RuntimeException ignored) {
            // A foreign block can expose an incomplete state or drop table.
            return null;
        }
    }

    @NotNull
    private static String describeStack(@NotNull ItemStack stack, @NotNull IBlockState state) {
        ResourceLocation id = state.getBlock().getRegistryName();
        String registryName = id == null ? "unknown" : id.toString();
        return stack.isEmpty() ? registryName : stack.getDisplayName() + " (" + registryName + ")";
    }

    @Nullable
    private static StructureFailureTrace getLastFailure(@NotNull MultiblockControllerBase controller) {
        StructureRuntime runtime = controller.getStructureRuntime();
        return runtime == null ? null : runtime.getLastFailure();
    }

    /**
     * Prefer the failure position captured when the check failed; only fall back to the live error
     * for addons that report a {@link PatternError} without a failure trace.
     */
    @Nullable
    private static BlockPos resolveErrorPos(@Nullable StructureFailureTrace failure,
                                            @Nullable PatternError error) {
        if (failure != null && failure.getErrorPos() != null) {
            return failure.getErrorPos();
        }
        if (error == null) return null;
        try {
            return error.getPos();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @NotNull
    private static String kindLangKey(@NotNull StructureFailureTrace.Kind kind) {
        return switch (kind) {
            case BLOCK_MISMATCH -> "gregtech.multiblock.pattern.failure.kind.block_mismatch";
            case MISSING_ABILITY -> "gregtech.multiblock.pattern.failure.kind.missing_ability";
            case COUNT_LIMIT -> "gregtech.multiblock.pattern.failure.kind.count_limit";
            case CAPABILITY_UNSUPPORTED -> "gregtech.multiblock.pattern.failure.kind.capability_unsupported";
            case ASSEMBLY_REJECTION -> "gregtech.multiblock.pattern.failure.kind.assembly_rejection";
            case COMMIT_REJECTION -> "gregtech.multiblock.pattern.failure.kind.commit_rejection";
            default -> "gregtech.multiblock.pattern.failure.kind.unknown";
        };
    }

    @NotNull
    private static ITextComponent translation(@NotNull TextFormatting color, @NotNull String langKey,
                                              Object... args) {
        return new TextComponentTranslation(langKey, args).setStyle(new Style().setColor(color));
    }

    @NotNull
    private static ITextComponent separator() {
        return new TextComponentString(TextFormatting.DARK_GRAY + SEPARATOR);
    }

    @NotNull
    private static String formatPos(@NotNull BlockPos pos) {
        return "[X:" + pos.getX() + " Y:" + pos.getY() + " Z:" + pos.getZ() + "]";
    }

    /** Format the offset from the controller as the language-neutral {@code X+n Y-n Z+n}. */
    @NotNull
    private static String formatOffset(@NotNull BlockPos origin, @NotNull BlockPos target) {
        return "X" + formatSigned(target.getX() - origin.getX()) +
                " Y" + formatSigned(target.getY() - origin.getY()) +
                " Z" + formatSigned(target.getZ() - origin.getZ());
    }

    @NotNull
    private static String formatSigned(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }
}
