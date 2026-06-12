# Godforge V3 Structure Design

**Design status:** draft before refactor  
**Related design:** `docs/design/structure-system-v3-design.md`

## Goal

Move Forge of the Gods to the V3 structure model without changing its gameplay contract:

- The structure is declared once as a V3 `StructureDefinition`.
- `beam_shaft`, `first_ring`, `second_ring`, and `third_ring` remain separate pieces.
- Multi-piece checking becomes the only structure source, not a post-formation validation layer.
- Upgrades, ring replacement, renderer recovery, module attachment, previews, and build tools all use the same
  structure model.

This refactor must avoid two known traps:

- Ring count must not depend on a structure check result that depends on the same ring count.
- Rendered rings must not make air globally valid for rings that were never physically built.

When behavior is uncertain, add trace logs first and ask for logs before changing matching semantics.

## Current Problem

The current Godforge implementation has two structure sources:

- Initial formation and JEI use a legacy merged `BlockPattern` containing `beam_shaft + first_ring`.
- Post-formation dirty checking uses a `MultiPiecePattern` with ring pieces.

That split causes drift:

- `ringAmount` is computed after formation by extra template checks.
- Render state rebuilds structure templates between normal ring and air ring variants.
- Module hatches are collected by normal ability assembly and then discovered again from abilities and parts.
- Event-driven dirty checking only becomes authoritative after the first legacy formation path has already succeeded.

V3 wants one immutable declaration, one per-controller runtime, one operation request boundary, and one commit boundary.
Godforge should follow that shape.

## State Model

Godforge needs separate state names for separate meanings. Do not use one `ringAmount` for all of these roles.

| State | Owner | Meaning | Persistence |
|---|---|---|---|
| `desiredRingAmount` | Godforge data / structure policy | Highest ring tier the controller intends to validate or build. Derived from upgrades, GUI channel, and existing formed state. | May be persisted or derived from persisted fields. |
| `formedRingAmount` | Structure runtime / Godforge formed metadata | Highest ring tier actually matched by the last successful structure check. | Persisted through existing Godforge data for save compatibility, but treated as commit output. |
| `clearedRingAmount` | Godforge data / render policy | Highest ring tier currently replaced with air by the renderer. | Persisted. |
| `renderActive` | Godforge data / render policy | The render block is expected to own ring visuals. | Persisted, but must be recovered from world state on load. |
| `rendererDisabled` | Godforge data / user config | Player disabled renderer replacement. | Persisted. |
| `moduleHatches` | Godforge runtime cache | Modules collected by the last committed structure result. | Not authoritative; rebuild from commit result. |

Compatibility mapping for the current `ForgeOfGodsData` fields:

- Existing `ringAmount` should become the save-compatible storage for `formedRingAmount`.
- Existing `clearedRingAmount` keeps its meaning.
- During migration, `desiredRingAmount` can be computed instead of persisted:
  - At least 1.
  - At least `formedRingAmount` while formed.
  - At least 2 when the `CD` upgrade is active and a refresh/build operation is requested.
  - At least 3 when the `END` upgrade is active and a refresh/build operation is requested.
  - Clamped to 1..3.

## Ring Activation Policy

Ring piece activation must be deterministic before checking starts.

Recommended rule:

- `first_ring` is always active.
- `second_ring` is active when `desiredRingAmount >= 2`.
- `third_ring` is active when `desiredRingAmount >= 3`.

The structure checker may also support a discovery mode during manual refresh:

- Start with `desiredRingAmount` from upgrades and current formed state.
- Check active pieces.
- Commit `formedRingAmount` from matched active pieces.
- Do not run hidden extra template checks after commit.

This removes the current loop where `updateRingAmount()` decides active pieces by checking ring templates outside the
canonical structure operation.

## Rendered Ring Validity

Rendered rings are the highest-risk part of the migration. Air must be valid only when it represents a ring that was
already formed and is currently owned by the renderer.

Each ring cell should use a Godforge-specific ring element or predicate with this logic:

1. If the physical block matches the normal ring requirement, accept it.
2. Otherwise, accept air only when all conditions are true:
   - `renderActive` is true.
   - `clearedRingAmount >= ringIndex`.
   - The render block exists at `getRenderPos()`.
   - The render tile owner is this controller.
   - The last committed `formedRingAmount >= ringIndex`, or recovery mode has verified the same air-ring footprint.
3. Otherwise fail with a diagnostic that includes ring index, render state, cleared count, actual block, and expected
   normal candidates.

This means normal and air ring templates should not be separate structure definitions. The definition stays fixed; the
cell runtime decides whether normal block or renderer-owned air is acceptable.

## Renderer Lifecycle

Renderer operations are world mutations, not structure declarations.

Creation:

- Only run after a successful structure commit.
- Place or repair the render block.
- Set `renderActive = true`.
- Start a ring replacement task for `formedRingAmount`, not `desiredRingAmount`.
- Suppress event-driven rechecks during block replacement.
- When replacement finishes, set `clearedRingAmount` to the replaced ring count and mark those ring pieces dirty.

Destruction:

- Remove the render block.
- Start a ring restoration task for `clearedRingAmount`.
- Suppress event-driven rechecks during restoration.
- When restoration finishes, set `clearedRingAmount = 0`, set `renderActive = false`, and mark restored ring pieces
  dirty.

Load recovery:

- If `renderActive` is persisted but the render block is missing or invalid, set `renderActive = false` and restore or
  require physical rings according to current policy.
- If `renderActive` is false but `clearedRingAmount > 0` and the battery indicates the renderer should have been active,
  enter a recovery operation:
  - Verify `beam_shaft`.
  - Verify each cleared ring using renderer-owned-air rules without trusting a missing renderer blindly.
  - Repair the render block.
  - Commit `formedRingAmount = max(formedRingAmount, clearedRingAmount)` only after the check succeeds.

## Upgrade Flow

Upgrade completion should not immediately mutate formed structure state.

On `CD` or `END` unlock:

- Mark the Godforge structure policy dirty.
- Do not set `formedRingAmount`.
- Do not allow the new ring to count for module limits or milestones until a structure refresh/check succeeds.
- A GUI refresh, builder action, or normal structure check should use the new `desiredRingAmount`.

On respec or downgrade-like operations:

- Compute the lower `desiredRingAmount`.
- If `formedRingAmount` is now above `desiredRingAmount`, invalidate or recheck before continuing operation.
- If a renderer is active and the removed ring tier was cleared, restore the now-invalid ring before the next committed
  structure state.

Split-upgrade limits currently use `ringAmount`. After migration they should use `formedRingAmount`, because gameplay
benefits should depend on physically formed extensions, not merely unlocked desired extensions.

## Structure Check And Commit Flow

Initial formation and recheck should share the same operation path:

1. Build or retrieve the fixed `StructureDefinition`.
2. Build an operation request with:
   - orientation,
   - desired ring count,
   - render policy snapshot,
   - current formed metadata,
   - controller context.
3. Run the multi-piece check.
4. Produce a result containing:
   - matched pieces,
   - formed ring amount,
   - collected Godforge modules,
   - normal multiblock abilities,
   - channel values,
   - diagnostics.
5. Commit through the server-thread structure committer.
6. Publish Godforge-specific commit state after the generic commit accepts the part/ability set.

Godforge-specific commit state should include:

- `formedRingAmount`.
- `moduleHatches` derived from collected `GODFORGE_MODULE` ability parts and module-slot matches.
- `lastCommittedRenderPolicy` or enough metadata for air-ring validation.

Avoid calling extra ring template checks from `formStructure()`. `formStructure()` should consume the result of the
canonical check, not discover more structure.

## Module Attachment

`beam_shaft` module slots should be collected during the structure operation.

Rules:

- A module block in a `J` slot contributes `GODFORGE_MODULE`.
- A casing block in a `J` slot is a valid empty slot.
- Empty air should be allowed only in already formed beam-shaft reassembly if the old behavior must be preserved for
  brief hot-swap windows.
- `moduleHatches` is rebuilt from the committed result.
- `discoverModules()` should become a compatibility/debug helper or be removed after instrumentation proves commit
  collection is complete.

Connection/disconnection stays in Godforge tick policy:

- Structure commit only identifies eligible modules.
- The tick loop still decides whether modules connect based on battery, max module count, upgrade rules, and anti-cheese
  checks.

## Preview And Build Tools

Preview and construction must use the same ring policy inputs as checking.

Default preview:

- Show one ring by default.
- Expose structure-piece selection for `beam_shaft`, `first_ring`, `second_ring`, `third_ring`.
- If the controller item or live controller has upgrade context, show pieces up to `desiredRingAmount`.

Creative/survival build:

- Build only active pieces by default.
- Allow the structure-piece channel to build a specific ring piece.
- Never build air-ring variants. Build tools place physical ring blocks.
- Renderer replacement is a post-commit world mutation, not a build template.

## Diagnostics

Before changing matching behavior, add trace output around these points:

- Desired, formed, and cleared ring counts at check start and commit.
- Active piece list and skipped piece reasons.
- Ring cell failure: ring index, render state, cleared count, actual block, normal candidate summary.
- Renderer recovery: persisted render state, render block state, owner position, recovered pieces.
- Module collection: collected `GODFORGE_MODULE` count, module positions, ignored invalid slots.
- Ring replacement task completion: restore flag, target ring count, changed blocks, dirty pieces marked.

These logs should be rate-limited like the current structure failure and module connection logs.

## Migration Plan

1. Add instrumentation using the current implementation.
2. Introduce names and helper methods for desired, formed, and cleared ring counts without changing behavior.
3. Add Godforge-specific formed metadata or a typed operation-state key for formed ring amount and collected modules.
4. Convert ring predicates to context-aware normal-or-rendered-air predicates.
5. Add a fixed `createStructureDefinition()` for Godforge with four pieces.
6. Route initial formation through the multi-piece definition.
7. Remove extra ring template checks from `formStructure()` and consume committed metadata instead.
8. Rework renderer creation/destruction to mark pieces dirty after replacement and to use `formedRingAmount`.
9. Rework module discovery to consume commit results.
10. Remove the legacy `createStructurePattern()` and `createMultiPiecePattern()` Godforge paths after previews and build
    tools use the V3 definition.

## Acceptance Criteria

- A one-ring Godforge forms through the multi-piece definition.
- Unlocking `CD` does not grant second-ring benefits until the second ring is built and a structure check commits.
- Unlocking `END` does not grant third-ring benefits until the third ring is built and a structure check commits.
- Renderer replacement does not invalidate a formed structure when the render block is valid and owns the air rings.
- Air rings do not validate when the renderer is missing, owned by another controller, or the ring was never formed.
- Reload recovery handles persisted rendered structures without requiring a full physical ring rebuild.
- Module hotswap behavior is at least as permissive as the current implementation, with logs for any rejected slot.
- JEI/projector/builder views come from the same multi-piece definition and never from a separate merged legacy pattern.
