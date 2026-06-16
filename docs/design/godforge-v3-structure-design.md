# Godforge V3 Structure Design

**Implementation snapshot:** 2026-06-16  
**Related design:** `../structure-system-v3-design.md`  
**Status:** current implementation already uses V3 as the canonical path; this document is now a stabilization and
hardening design, not a pre-migration refactor plan.

## Conclusion

Do not continue the old document's broad refactor plan as-is.

The current code has already completed the important V3 migration boundary for Forge of the Gods:

- `MetaTileEntityForgeOfGods` returns a stable static `STRUCTURE_DEFINITION` from `createStructureDefinition()`.
- Formation uses `formStructure(FormedStructureView)`, not `PatternMatchContext`.
- Initial formation, recheck, preview/build metadata, and piece activation are sourced from the V3 definition.
- Ring count is committed from the successful multi-piece operation path, not by post-formation legacy template scans.
- Module discovery is rebuilt from committed `GODFORGE_MODULE` abilities.
- Renderer block replacement is treated as a world mutation after formation.
- `StructureInternalLegacyBoundaryScanTest` already locks Godforge as a runtime-shaped V3 controller with stable
  contextual pieces.

The better design from this point is:

1. Keep the stable V3 `StructureDefinition`.
2. Keep normal and rendered-air ring pieces as mutually exclusive contextual pieces.
3. Harden the rendered-air activation policy so air rings are valid only when the renderer is owned by this controller,
   or during an explicit persisted-render recovery mode.
4. Keep `discoverModules()` as a cache rebuild from committed abilities; do not turn it back into an independent
   structure scan.
5. Treat future work as small correctness and tooling follow-ups, not as a second migration.

When behavior is uncertain, add low-frequency Godforge logs first and ask for logs before changing matching semantics.

## Current Implementation

Current Godforge structure code lives mainly in:

- `src/main/java/gregtech/common/metatileentities/multi/electric/godforge/MetaTileEntityForgeOfGods.java`
- `src/main/java/gregtech/common/metatileentities/multi/electric/godforge/util/ForgeOfGodsData.java`
- `src/main/java/gregtech/common/metatileentities/multi/electric/godforge/module/MTEBaseModule.java`
- `src/test/java/gregtech/api/pattern/StructureInternalLegacyBoundaryScanTest.java`

### Structure Declaration

`MetaTileEntityForgeOfGods` owns a single static definition:

```text
STRUCTURE_DEFINITION = buildGodforgeStructureDefinition()
createStructureDefinition() -> STRUCTURE_DEFINITION
```

The definition contains stable pieces:

| Piece | Role |
|---|---|
| `beam_shaft` | Controller, hatches, and Godforge module slots. |
| `first_ring` | Physical first ring. |
| `first_ring_air` | Rendered first ring footprint after renderer replacement. |
| `second_ring` | Physical second ring. |
| `second_ring_air` | Rendered second ring footprint. |
| `third_ring` | Physical third ring. |
| `third_ring_air` | Rendered third ring footprint. |

This is intentionally different from the old design's "four pieces only" target. Seven stable pieces are acceptable
because the definition is still immutable, and the normal/air variants are selected by contextual conditions instead of
being rebuilt at runtime.

### Ring Conditions

Ring pieces are selected by `ringTemplateCondition(ringIndex, rendererOwned)`.

The current rule is:

- Ring 1 is always structurally available.
- Rings 2 and 3 are available when `getStructureRingTargetAmount() >= ringIndex`.
- For each ring, exactly one of the physical piece or rendered-air piece should be active:
  - physical piece when `canUseRenderedRingTemplate(ringIndex) == false`;
  - air piece when `canUseRenderedRingTemplate(ringIndex) == true`.
- `canUseRenderedRingTemplate(...)` requires `clearedRingAmount >= ringIndex`.
- Normal rendered validation additionally requires the `GODFORGE_RENDER` tile at `getRenderPos()` to be owned by this
  controller.
- Explicit recovery mode may accept a missing renderer so persisted air-ring saves can be repaired, but it rejects a
  loaded renderer owned by another controller.

`getStructureRingTargetAmount()` is:

```text
max(desiredRingAmount, clearedRingAmount when render/recovery is active)
```

This keeps already-rendered rings in the validation target during save/load recovery, even if the normal desired tier
would otherwise be lower.

### State Model

`ForgeOfGodsData` now separates the important ring concepts:

| State | Current storage / method | Meaning |
|---|---|---|
| Desired ring amount | `getDesiredRingAmount()` | Tier requested by upgrades and current formed state. This is input to the next check. |
| Formed ring amount | `ringAmount`, `getFormedRingAmount()` | Save-compatible storage for the highest ring tier committed by formation. This is gameplay authority. |
| Cleared ring amount | `clearedRingAmount` | Highest ring tier currently replaced with air by the renderer. |
| Render active | `isRenderActive` | The controller believes a render block should own ring visuals. |
| Renderer disabled | `isRendererDisabled` | User/config state that prevents automatic renderer creation. |
| Module cache | `moduleHatches` | Runtime cache rebuilt from committed abilities after formation. |

The old single `ringAmount` problem is mostly solved: code comments and helper methods now treat the saved field as
formed-ring storage.

### Formation Flow

The canonical formation flow is:

1. V3 checks the stable multi-piece definition.
2. `formStructure(FormedStructureView)` calls `formStructureWithDisplay(formed)`.
3. `formGodforgeStructure()` commits Godforge-specific state.
4. `commitFormedRingAmountFromStructure()` writes the formed ring amount from the active structure target.
5. `discoverModules()` rebuilds `moduleHatches` from committed `GODFORGE_MODULE` abilities.
6. Milestones are recalculated.
7. If the battery is active and the renderer is allowed, the renderer is created or repaired.

The important invariant is that `formStructure()` must not run additional ring template checks. The structure check has
already selected the active piece set; Godforge-specific state should only consume that committed result.

### Module Attachment

Godforge modules are now regular multiblock ability parts:

- `MTEBaseModule` implements `IGodforgeModule` and `IMultiblockAbilityPart<IGodforgeModule>`.
- `getAbility()` returns `MultiblockAbility.GODFORGE_MODULE`.
- `registerAbilities()` adds the module to committed ability instances.
- `beam_shaft` `J` slots accept module blocks, the normal casing, and currently air for empty module slots.
- `discoverModules()` reads `getAbilities(MultiblockAbility.GODFORGE_MODULE)` and rebuilds `moduleHatches`.

This is the right boundary. Structure formation identifies eligible modules; the tick loop still owns connection,
disconnect, battery, max-module-count, upgrade, and anti-cheese policy.

### Renderer Lifecycle

Renderer operations are world mutations outside the declaration:

- `createRenderer()` places or repairs `GODFORGE_RENDER`, sets owner position and rotation, marks render active, and
  starts ring replacement through `ringsDirty`.
- `replaceRenderedRings(false)` replaces physical ring blocks with air up to `getFormedRingAmount()`.
- `destroyRenderer()` removes the render block and starts restoration for `clearedRingAmount`.
- `processRingReplacement()` suppresses event-driven rechecks while it mutates ring blocks.
- `finishRingReplacement()` updates `clearedRingAmount`, notifies structure dependencies, and performs deferred refresh
  when needed.

The renderer must not become a ring-tier source. It may only clear or restore rings that were already committed as
formed.

### Recovery Flow

`tryRecoverRenderedStructure()` supports persisted saves where:

- the battery is still active,
- `clearedRingAmount > 0`,
- physical rings are missing because a renderer had previously replaced them with air.

It temporarily enables `recoveringRenderedStructure`, re-runs the normal V3 check, and then recreates the renderer when
the structure forms. This keeps recovery inside the canonical V3 operation path. Recovery may also run when
`renderActive` was persisted but the renderer block or owner is missing; ordinary rendered validation remains strict.

## Design Decision: Normal/Air Pieces Are Better Than One Mixed Predicate

The old design recommended a single Godforge-specific cell predicate that accepted either physical ring blocks or
renderer-owned air. That is no longer the best default for the current V3 code.

The current normal/air piece pair design is better because:

- The structure definition remains stable and immutable.
- Physical and rendered footprints have clear names in traces: `first_ring` vs `first_ring_air`.
- Air-ring templates can use ordinary typed `Elements.air()` cells.
- Build tools can target physical pieces without teaching every cell how to reverse an air predicate.
- Conditional pieces can declare external dependencies on upgrades/configuration.
- Failure diagnostics can point to the active piece that failed.

The condition is that air pieces must be gated strictly. Air-ring variants are runtime validation pieces, not build
templates and not a way to skip construction.

## Remaining Hardening

Only a few targeted follow-ups remain.

### 1. Keep Ring Commit Tied To The Checked Piece Set

`commitFormedRingAmountFromStructure()` currently commits `getStructureRingTargetAmount()`. This is acceptable only
because the active piece set is deterministic and the V3 check must pass before commit.

Preferred future improvement, if the V3 result exposes matched piece names cleanly:

- commit ring 1 when `first_ring` or `first_ring_air` matched;
- commit ring 2 when `second_ring` or `second_ring_air` matched;
- commit ring 3 when `third_ring` or `third_ring_air` matched.

Do not reintroduce extra template scans to discover this.

### 2. Hide Or Mark Air Pieces In Build Tooling

Air pieces are validation variants. Builder/projector flows should build physical rings by default and should not offer
`*_ring_air` as ordinary construction targets.

Preferred direction:

- Default build/preview shows `beam_shaft`, `first_ring`, `second_ring`, and `third_ring`.
- If low-level piece-channel tooling exposes all pieces, mark `*_ring_air` as runtime-only or skip it in user-facing
  construction.
- Renderer replacement remains a post-commit world mutation.

### 3. Keep Async Disabled For Now

`allowsAsyncStructureCheck()` returns false. Keep that until renderer ownership, recovery, and dependency snapshots are
fully deterministic for detached workers. Godforge is large and stateful enough that conservative synchronous checks are
the right default.

## Upgrade Policy

Upgrade unlocks should change desired structure policy, not formed gameplay state.

Current intended behavior:

- Unlocking `CD` raises desired rings to at least 2.
- Unlocking `END` raises desired rings to 3.
- Split-upgrade checks still use the formed ring amount through save-compatible `ringAmount`.
- New ring benefits are not granted until a structure refresh/check succeeds and commits the formed ring amount.
- Module limit uses formed rings:

```text
maxModuleCount = 8 + (formedRingAmount - 1) * 4
```

On respec or downgrade-like operations, the code should restore rendered rings first when needed, then invalidate or
refresh through the same V3 operation path. Do not directly lower gameplay state without a structure operation unless
the structure is also being invalidated.

## Diagnostics

The current implementation already has useful Godforge logs:

- ring state before/after commit;
- structure failure with trace path, operation, expected/actual, candidates, ring state, and renderer state;
- module discovery mismatch;
- module connection decisions in debug mode;
- renderer creation and ring replacement completion;
- persisted rendered-structure recovery.

Keep logs rate-limited or debug-gated. Add logs before changing uncertain behavior, especially for:

- rendered-air piece activation and ownership failures;
- recovery accepting a missing renderer;
- recovery rejecting a foreign renderer;
- deferred refresh after ring restoration;
- build tooling hiding or exposing runtime-only air pieces.

## What Not To Do

Do not continue these obsolete items from the old draft:

- Do not rebuild Godforge around legacy `createStructurePattern()` or `createMultiPiecePattern()` paths.
- Do not add post-formation ring template scans.
- Do not make `ringAmount` mean desired, formed, and cleared state again.
- Do not remove `discoverModules()` just because it is named "discover"; its current job is cache rebuild from committed
  abilities.
- Do not replace the current seven stable contextual pieces with runtime-rebuilt templates.
- Do not make rendered-air templates globally valid.
- Do not let renderer replacement grant rings that were never physically formed.

## Acceptance Criteria

The stabilized design is acceptable when:

- One-ring Godforge forms through the V3 `STRUCTURE_DEFINITION`.
- Unlocking `CD` or `END` changes desired rings but does not grant ring benefits until a successful check commits.
- Physical ring pieces and rendered-air ring pieces are mutually exclusive for each ring.
- Rendered-air pieces validate only for this controller's renderer, or during explicit safe recovery.
- Missing or foreign renderer ownership produces useful logs and does not silently validate arbitrary air.
- Renderer replacement uses `formedRingAmount`, not `desiredRingAmount`.
- Ring restoration suppresses event-driven rechecks while blocks are being restored.
- `moduleHatches` is rebuilt from committed `GODFORGE_MODULE` abilities.
- Module connection policy remains in the tick loop.
- Build/projector tooling does not construct air-ring variants as normal structure pieces.
- Godforge remains locked by `StructureInternalLegacyBoundaryScanTest` as a stable V3 controller.

## Recently Completed

- Added `GodforgeRenderedRingPolicy` and tests for rendered-air activation.
- Normal rendered validation now requires renderer ownership by the current controller.
- Recovery accepts a missing renderer only when the battery is active and no loaded foreign renderer occupies the render
  position.
- Rendered-air activation failure logs ring index, cleared rings, recovery flag, and renderer ownership details in debug
  mode.
- `ensureRendererState()` repairs missing/invalid owner data but refuses a render block owned by another controller.

The next patch should start with matched-piece ring commit metadata or build-tool filtering for `*_ring_air` pieces.
