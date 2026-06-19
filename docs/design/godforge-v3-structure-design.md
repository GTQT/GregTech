# Godforge V3 Structure Design

**Implementation snapshot:** 2026-06-16
**Related design:** `../structure-system-v3-design.md`
**Status:** current code uses V3 as the canonical Godforge structure path. This document records the completed
stabilization design and the remaining conservative follow-ups.

## Conclusion

Do not continue the old pre-migration refactor plan as-is.

The better design is the complete V3 solution now reflected in code:

1. Keep Godforge on one stable `StructureDefinition`.
2. Keep physical ring pieces and rendered-air ring pieces as separate contextual runtime pieces.
3. Add generic V3 `runtimeOnly()` / `hideFromTooling()` piece semantics for validation-only pieces.
4. Mark Godforge `*_ring_air` pieces runtime-only so they validate saves/rendered worlds but are hidden from normal
   preview, hint, projector, and build-all tooling.
5. Commit formed ring amount from the matched piece metadata, not from desired target state and not from a legacy
   post-check scan.
6. Gate rendered-air validation by renderer ownership, with a narrow persisted-save recovery path.
7. Keep modules as committed `GODFORGE_MODULE` abilities and rebuild only the runtime cache from those abilities.

This is intentionally broader than a Godforge-only workaround. The runtime-only concept belongs in the V3 structure
system because any multiblock can have validation-only pieces that must participate in matching but must not be offered
as player-buildable structure pieces.

When behavior is uncertain, add low-frequency Godforge logs first and ask for logs before changing matching semantics.

## Current Code Map

Godforge structure and policy code:

- `src/main/java/gregtech/common/metatileentities/multi/electric/godforge/MetaTileEntityForgeOfGods.java`
- `src/main/java/gregtech/common/metatileentities/multi/electric/godforge/GodforgeRenderedRingPolicy.java`
- `src/main/java/gregtech/common/metatileentities/multi/electric/godforge/GodforgeRingMatchPolicy.java`
- `src/main/java/gregtech/common/metatileentities/multi/electric/godforge/util/ForgeOfGodsData.java`
- `src/main/java/gregtech/common/metatileentities/multi/electric/godforge/module/MTEBaseModule.java`

Generic V3 runtime-only/tooling visibility code:

- `src/main/java/gregtech/api/pattern/element/IStructurePiece.java`
- `src/main/java/gregtech/api/pattern/element/StructureDefinition.java`
- `src/main/java/gregtech/api/pattern/element/StructureCompiler.java`
- `src/main/java/gregtech/api/pattern/StructurePiece.java`
- `src/main/java/gregtech/api/pattern/MultiPiecePattern.java`
- `src/main/java/gregtech/api/pattern/MultiPiecePreviewAssembler.java`
- `src/main/java/gregtech/api/pattern/StructureBuildOperationService.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockStructureChannels.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockStructurePreviews.java`
- `src/main/java/gregtech/client/renderer/handler/MultiblockPreviewRenderer.java`
- `src/main/java/gregtech/common/items/behaviors/StructureProjectorBehavior.java`

Regression coverage:

- `src/test/java/gregtech/api/pattern/StructureToolingVisibilityTest.java`
- `src/test/java/gregtech/api/pattern/StructureInternalLegacyBoundaryScanTest.java`
- `src/test/java/gregtech/common/metatileentities/multi/electric/godforge/GodforgeRenderedRingTemplatePolicyTest.java`
- `src/test/java/gregtech/common/metatileentities/multi/electric/godforge/GodforgeRingMatchPolicyTest.java`

## Structure Declaration

`MetaTileEntityForgeOfGods` owns one static definition:

```text
STRUCTURE_DEFINITION = buildGodforgeStructureDefinition()
createStructureDefinition() -> STRUCTURE_DEFINITION
```

The definition has seven stable pieces:

| Piece | Tooling visible | Role |
|---|---:|---|
| `beam_shaft` | yes | Controller, hatches, and Godforge module slots. |
| `first_ring` | yes | Physical first ring. |
| `first_ring_air` | no | Runtime validation footprint after renderer replacement. |
| `second_ring` | yes | Physical second ring. |
| `second_ring_air` | no | Runtime validation footprint after renderer replacement. |
| `third_ring` | yes | Physical third ring. |
| `third_ring_air` | no | Runtime validation footprint after renderer replacement. |

Seven stable pieces are correct. The old "four pieces only" target is obsolete because the V3 definition is immutable
and contextual conditions select exactly one physical/air variant per ring.

## Runtime-Only Piece Semantics

V3 pieces now carry a tooling visibility flag:

- `IStructurePiece.isToolingVisible()` defaults to `true`.
- `StructureDefinition.PieceBuilder.runtimeOnly()` and `hideFromTooling()` set tooling visibility to `false`.
- `RepeatablePieceBuilder` has the same API.
- `StructureCompiler` preserves the flag into `StructurePiece`, `RepeatGroupPiece`, `DynamicOffsetPiece`, and
  `DynamicRepeatGroupPiece`.
- `MultiPiecePattern` exposes:
  - `getToolingPieceList()`
  - `getToolingPieceCount()`
  - `resolveToolingPieceIndex(int)`
  - `getToolingPiece(int)`

Runtime-only pieces still participate in:

- structure matching,
- contextual activation,
- formed metadata,
- dirty tracking,
- dependency-driven validation,
- later piece offset/center metadata.

Runtime-only pieces are hidden from:

- combined multi-piece previews,
- single-piece preview selection,
- hint-all,
- build-all,
- projector `STRUCTURE_PIECE` range,
- projector selected-piece auto-build.

The user-facing `STRUCTURE_PIECE` channel is now a tooling index. It maps visible piece `1..N` to the compiled piece
index only at the operation boundary. Internal runtime operations still use compiled indexes.

This distinction is the key fix: `first_ring_air` can be checked when the renderer has cleared a ring, but the player
cannot select or build it as "piece 2".

## Ring Conditions

Ring pieces are selected by `ringTemplateCondition(ringIndex, rendererOwned)`.

The current rule is:

- Ring 1 is always structurally available.
- Rings 2 and 3 are available when `getStructureRingTargetAmount() >= ringIndex`.
- For each ring, the physical piece is active when rendered-air policy returns false.
- For each ring, the air piece is active when rendered-air policy returns true.
- `getStructureRingTargetAmount()` is:

```text
max(desiredRingAmount, clearedRingAmount when render/recovery is active)
```

That keeps already-rendered rings in the validation target during save/load recovery, even if the normal desired tier
would otherwise be lower.

## Rendered-Air Policy

`GodforgeRenderedRingPolicy` defines the policy boundary:

- Uncleared rings never use rendered-air templates.
- Normal rendered validation requires the renderer tile to be present and owned by this controller.
- Recovery may accept a missing renderer only when the battery is active and no loaded foreign renderer occupies the
  render position.
- Recovery rejects loaded renderers owned by another controller.

`MetaTileEntityForgeOfGods.canUseRenderedRingTemplate(...)` adapts live world state into that pure policy and logs
skipped rendered-air activation in debug mode.

`ensureRendererState()` repairs missing or invalid owner data for this controller's renderer, but refuses foreign
render blocks and disables render-active state instead of silently taking ownership.

## Formed Ring Commit

`commitFormedRingAmountFromStructure(FormedStructureView formed)` now commits via `GodforgeRingMatchPolicy`:

```text
third_ring or third_ring_air  -> formed rings = 3
second_ring or second_ring_air -> formed rings = 2
otherwise                     -> formed rings = 1
```

This is the correct V3 boundary:

- The successful structure check decides which pieces matched.
- Godforge consumes matched piece metadata through `FormedStructureView`.
- No extra legacy ring template scan is allowed after formation.
- Desired ring amount remains an input to the next check; it is not gameplay authority.

## State Model

`ForgeOfGodsData` separates the important ring states:

| State | Storage / method | Meaning |
|---|---|---|
| Desired ring amount | `getDesiredRingAmount()` | Tier requested by upgrades/current policy. Input to the next check. |
| Formed ring amount | `ringAmount`, `getFormedRingAmount()` | Highest ring tier committed by a successful structure check. Gameplay authority. |
| Cleared ring amount | `clearedRingAmount` | Highest formed ring tier currently replaced with air by the renderer. |
| Render active | `isRenderActive` | Controller believes a renderer should own visuals. |
| Renderer disabled | `isRendererDisabled` | User/config state preventing automatic renderer creation. |
| Module cache | `moduleHatches` | Runtime cache rebuilt from committed `GODFORGE_MODULE` abilities. |

The renderer may clear or restore ring blocks, but it must not grant ring tiers.

## Formation Flow

The canonical flow is:

1. V3 checks the stable multi-piece definition.
2. Contextual conditions activate physical or rendered-air ring pieces.
3. Runtime-only pieces participate in validation but stay hidden from tooling.
4. `formStructure(FormedStructureView)` calls `formStructureWithDisplay(formed)`.
5. `formGodforgeStructure()` commits Godforge-specific state.
6. `commitFormedRingAmountFromStructure(formed)` writes formed ring amount from matched piece metadata.
7. `discoverModules()` rebuilds `moduleHatches` from committed `GODFORGE_MODULE` abilities.
8. Milestones are recalculated.
9. If the battery is active and the renderer is allowed, the renderer is created or repaired.

The invariant is that `formStructure()` must not run another ring structure check. It should only consume the V3 result.

## Module Attachment

Godforge modules are normal multiblock ability parts:

- `MTEBaseModule` implements `IGodforgeModule` and `IMultiblockAbilityPart<IGodforgeModule>`.
- `getAbility()` returns `MultiblockAbility.GODFORGE_MODULE`.
- `registerAbilities()` adds the module to committed ability instances.
- `beam_shaft` `J` slots accept module blocks, normal casing, and air for empty module slots.
- `discoverModules()` reads `getAbilities(MultiblockAbility.GODFORGE_MODULE)` and rebuilds `moduleHatches`.

This boundary should stay as-is. Structure formation identifies eligible modules; the tick loop owns connection,
disconnect, battery, max-module-count, upgrade, and anti-cheese policy.

## Renderer Lifecycle

Renderer operations remain world mutations outside the declaration:

- `createRenderer()` places or repairs `GODFORGE_RENDER`, sets owner position and rotation, marks render active, and
  starts ring replacement.
- `replaceRenderedRings(false)` replaces physical ring blocks with air up to `getFormedRingAmount()`.
- `destroyRenderer()` removes the render block and starts restoration for `clearedRingAmount`.
- `processRingReplacement()` suppresses event-driven rechecks while mutating ring blocks.
- `finishRingReplacement()` updates `clearedRingAmount`, notifies structure dependencies, and performs deferred refresh
  when needed.

Renderer replacement is a post-commit presentation mutation. It is not part of ring-tier calculation.

## Recovery Flow

`tryRecoverRenderedStructure()` supports persisted saves where:

- the battery is still active,
- `clearedRingAmount > 0`,
- physical rings are missing because the renderer previously replaced them with air.

It temporarily enables `recoveringRenderedStructure`, reruns the normal V3 check, and then recreates or repairs the
renderer when the structure forms. Recovery stays inside the canonical V3 operation path.

The recovery exception is intentionally narrow: missing renderer can be tolerated only to repair a persisted rendered
structure; a loaded foreign renderer remains invalid.

## Tooling Behavior

The user-facing structure piece list is now:

```text
0 = combined/default structure
1 = beam_shaft
2 = first_ring
3 = second_ring
4 = third_ring
```

The hidden compiled pieces remain:

```text
first_ring_air
second_ring_air
third_ring_air
```

Build and preview implications:

- Build-all iterates visible pieces only.
- Hint-all skips runtime-only pieces.
- Single-piece preview maps tooling index to compiled piece before resolving world center.
- Combined preview excludes runtime-only cells but keeps their prior metadata for later pieces.
- Runtime-only pieces do not consume preview ability/candidate placement quotas.
- Direct calls that try to build a hidden compiled piece return inactive/invalid instead of constructing it.

This preserves the player workflow while keeping rendered saves valid.

## Upgrade Policy

Upgrade unlocks change desired structure policy, not formed gameplay state:

- Unlocking `CD` raises desired rings to at least 2.
- Unlocking `END` raises desired rings to 3.
- Split-upgrade checks still use formed ring amount through save-compatible `ringAmount`.
- New ring benefits are not granted until a structure refresh/check succeeds and commits formed ring amount.
- Module limit uses formed rings:

```text
maxModuleCount = 8 + (formedRingAmount - 1) * 4
```

On respec or downgrade-like operations, restore rendered rings first when needed, then invalidate or refresh through
the same V3 operation path. Do not directly lower gameplay state without a structure operation unless the structure is
also being invalidated.

## Completed Acceptance Criteria

- Godforge uses stable V3 `STRUCTURE_DEFINITION`.
- Godforge formation consumes `FormedStructureView`.
- Physical ring pieces and rendered-air ring pieces are mutually exclusive for each ring.
- `*_ring_air` pieces are runtime-only and hidden from normal tooling.
- `STRUCTURE_PIECE` channel range uses visible piece count.
- Projector selected-piece build maps visible index to compiled index.
- Combined/single-piece previews hide runtime-only pieces.
- Rendered-air validation requires current-controller renderer ownership, except narrow recovery.
- Foreign renderers do not silently validate or get adopted.
- Ring amount commits from matched piece metadata.
- Renderer replacement uses formed ring amount.
- Module cache rebuilds from committed `GODFORGE_MODULE` abilities.
- `StructureInternalLegacyBoundaryScanTest` locks Godforge as a stable runtime-shaped V3 controller and requires
  `*_ring_air` pieces to stay runtime-only.

## Remaining Conservative Follow-Ups

Only small follow-ups remain:

1. Keep `allowsAsyncStructureCheck()` disabled for Godforge until renderer ownership, recovery, and dependency snapshots
   are proven deterministic for detached workers.
2. Add targeted logs before changing any uncertain rendered-air behavior, especially recovery acceptance/rejection and
   deferred refresh after ring restoration.
3. If player reports show confusing projector behavior, add visible piece names to logs/tooltips rather than exposing
   runtime-only pieces.
4. Broaden profiling only after the current synchronous path is stable under large rendered-ring replacement saves.

## What Not To Do

Do not reintroduce these obsolete directions:

- Do not rebuild Godforge around `createStructurePattern()` or legacy `createMultiPiecePattern()` paths.
- Do not add post-formation ring template scans.
- Do not make `ringAmount` mean desired, formed, and cleared state again.
- Do not remove `discoverModules()` because of its name; its current job is cache rebuild from committed abilities.
- Do not replace the seven stable contextual pieces with runtime-rebuilt templates.
- Do not make rendered-air templates globally valid.
- Do not let renderer replacement grant rings that were never physically formed.
- Do not expose `*_ring_air` as normal build/projector pieces.
