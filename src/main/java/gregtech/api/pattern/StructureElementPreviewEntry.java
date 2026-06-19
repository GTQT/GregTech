package gregtech.api.pattern;

import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.pattern.element.IStructureElement;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-cell preview metadata consumed by JEI/projector tooling.
 *
 * <p>The canonical data is the direct element preview plus its preview tooltip.
 * Legacy predicates are adapted into {@link StructureElementPreview} at the
 * compatibility boundary, so normal preview/tooltip/diagnostic tooling does
 * not need to carry a predicate-shaped view.
 */
public final class StructureElementPreviewEntry {

    @NotNull
    private final StructureElementPreview preview;
    @NotNull
    private final List<String> tooltip;

    private StructureElementPreviewEntry(@NotNull StructureElementPreview preview,
                                         @NotNull List<String> tooltip) {
        this.preview = preview;
        this.tooltip = Collections.unmodifiableList(new ArrayList<>(tooltip));
    }

    @NotNull
    public static StructureElementPreviewEntry of(@NotNull StructureElementPreview preview,
                                                  @NotNull List<String> tooltip) {
        return new StructureElementPreviewEntry(preview, tooltip);
    }

    @NotNull
    public static StructureElementPreviewEntry fromElement(@NotNull IStructureElement<?> element,
                                                           @NotNull TraceabilityPredicate legacyPredicate) {
        List<String> tooltip = new ArrayList<>();
        element.addPreviewTooltip(tooltip);
        return of(element.getPreview(), tooltip);
    }

    @NotNull
    public static StructureElementPreviewEntry fromPredicate(@NotNull TraceabilityPredicate predicate) {
        return of(StructureElementPreview.fromPredicate(predicate), Collections.emptyList());
    }

    @NotNull
    public StructureElementPreview getPreview() {
        return preview;
    }

    @NotNull
    public List<String> getTooltip() {
        return tooltip;
    }

}
