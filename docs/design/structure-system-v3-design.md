# Structure System V3 Design

**Implementation status snapshot:** 2026-06-12

## Goals

The V3 structure system keeps GregTech's existing multiblock domain model, but gives the structure checker a cleaner
library boundary inspired by StructureLib.

The goal is not to copy StructureLib and not to replace the current checker in one pass. The immediate goal is to make
the current behavior observable, move controller-owned structure state behind one runtime, and then redesign the element
execution boundary without breaking existing multiblocks.

The central technical debt is now clear: GregTech already has a new `IStructureElement` package, but new elements are
still forced through the legacy `TraceabilityPredicate` execution model. V3 must make structure elements the primary
runtime contract and keep `TraceabilityPredicate` as a compatibility adapter.

## What StructureLib Does Better

StructureLib has a stronger separation between structure declaration, structure execution, and user tools:

- `IStructureDefinition<T>` is a library-level contract. It can check, hint, build, survival-build, and iterate with the
  same shape definition.
- `IStructureElement<T>` owns cell-level behavior: match, hint, description, possible blocks, and placement.
- `ExtendedFacing` represents direction, rotation, and flip as one complete orientation state.
- `IConstructable` and `ISurvivalConstructable` let tools trigger hints and construction without knowing the machine
  implementation.
- `couldBeValid`, `getDescription`, hint particles, and instrumentation make bad structures diagnosable.
- Channel data can be configured on the trigger item and passed through the same build/hint path.

GregTech should absorb these boundaries, not copy the implementation directly.

## What GregTech Should Keep

GregTech already has structure capabilities that are more specific and more valuable than StructureLib's generic layer:

- Immutable `BlockPatternTemplate` / `PieceTemplate` plus per-controller `MultiblockState`.
- `TemplatePool` and `SoftTemplate` for shared cached definitions.
- `DeclarativePatternBuilder` with casing, hatch, ability, tier, and channel semantics.
- `StructureDefinition`, `MultiPiecePattern`, `PieceRuntime`, dirty-piece tracking, and snapshot checking.
- `PatternMatchContext`, `MultiblockAbility`, and formed metadata integrated with machine behavior.

V3 should make these concepts easier to reason about and less scattered.

## Current State And Gaps

GregTech already has part of the V3 shape:

- Controller runtime initialization now resolves a canonical `StructureDefinition<T>` first. Legacy
  `BlockPatternTemplate` and `MultiPiecePattern` hooks are adapted into definitions before runtime/checker code sees
  them.
- `FactoryBlockPattern`, `BlockPatternTemplate`, `BlockPattern`, and `MultiPiecePattern` are compatibility surfaces for
  old declarations and tools. New structure declarations should return one definition from `createStructureDefinition()`.
- `StructureRuntime` exists as the per-controller state holder skeleton and carries the resolved definition, single-piece
  template view, piece runtimes, formed metadata, channel values, and last failure trace.
- Formed metadata, formed channel values, missing abilities, and the last failure now have one canonical owner in
  `StructureRuntime`; `MultiblockControllerBase` no longer mirrors metadata and channels in separate fields.
- Successful definition checks now validate assembly/reassembly before publishing runtime state. Part-sharing rejection
  is recorded as a `COMMIT` failure and does not overwrite the last committed formed metadata.
- `StructureOperationEvaluator` is the thin operation boundary used by controller checks, previews, creative build
  tools, legacy `BlockPattern`, and structure iteration while delegating to the existing implementations.
- Controller-side orchestration has started moving into focused helpers:
  `MultiblockStructureCheckScheduler`, `MultiblockStructureAssembler`, `MultiblockStructureRegistration`,
  `MultiblockStructurePreviews`, `MultiblockStructureChannels`, and `MultiblockControllerClientHooks`. These helpers are
  transitional implementation boundaries, not new addon APIs.
- `gregtech.api.pattern.element.IStructureElement` exists and has context-aware methods for check, candidates, hints,
  creative placement, and survival placement.
- New elements no longer require `toPredicate()`. `CompiledStructureElement` executes direct elements without routing
  them through a predicate; `LegacyElement` retains predicate execution for compatibility declarations.
- Block, air, any, self, chain, wrapper, hatch, casing, tiered casing, and coil declarations have direct runtime paths.
- `StructureMatchCollector` owns transactional ability collection, element counts, tier/channel capture, active casing
  positions, and deferred requirement validation over the current `PatternMatchContext`.
- `StructureDefinition` can describe single-piece and multi-piece structures.
- `StructureCompiler` can compile those definitions into the current multi-piece runtime.
- `DeclarativePatternBuilder` already carries GregTech-specific casing, hatch, tier, channel, tooltip, and ability-limit
  semantics.
- Fixed single-piece controllers are being migrated to return cached definitions directly. The completed batches cover
  the vacuum freezer, implosion compressor, coke oven, saw mill, steam grinder, steam oven, active transformer,
  battery accumulator, network switch, research station, primitive water pump, primitive blast furnace, pyrolyse oven,
  processing array, multi smelter, multi alloy furnace, electric blast furnace, cracking unit, large chemical reactor,
  multiblock tank, large boiler, large combustion engine, large turbine, large miner, fluid drill, and fusion reactor.
- Large turbine, large miner, fluid drill, and fusion reactor still expose their existing `buildTemplate()` and
  `register...Type(Supplier<BlockPatternTemplate>)` compatibility APIs, but controller runtime now consumes the
  registered structures through cached `StructureDefinition` adapters.
- The network switch now explicitly overrides `createStructureDefinition()`, preventing the canonical resolver from
  selecting the inherited data bank definition before reaching the switch's legacy template hook.
- `PieceTemplate` is the canonical compiled piece representation for new definitions. `BlockPatternTemplate` remains the
  public compatibility facade required by existing APIs and controller fields.

However, the migration is not complete yet:

- Legacy declarations and custom predicate alternatives still execute through `TraceabilityPredicate`.
- The remaining controller-owned legacy hooks are limited to the dynamically generated charcoal pile and the Godforge
  module/controller path, plus base compatibility surfaces for addons.
- `StructureMatchCollector` is backed by `PatternMatchContext`; collector state has not moved to a standalone operation
  result yet.
- The operation evaluator delegates to separate single-piece, multi-piece, preview, and build traversals. Those
  traversals have not yet converged on one implementation.
- Global ability policy and diagnostics still span session, controller, runtime, and legacy error objects.
- `MultiblockControllerBase` still coordinates formation, invalidation, part attachment, callbacks, and registration.
  Assembly is now prepared before runtime publication, but there is not yet one immutable operation result and reusable
  commit pipeline.
- Live-world and snapshot matching share more cell execution code, but snapshot safety is still implicit. A legacy
  predicate or element may still require `World` or perform a side effect that is unsafe on the async checker thread.
- Survival construction may call `check` to decide whether a block is already valid. If `check` mutates match state, the
  construction path can accidentally pollute formation state.

This means the structure element system needs an architectural boundary redesign. It does not mean the whole structure
system should be deleted and rewritten. The migration should keep current behavior observable and compatible while the
element runtime becomes the primary execution model.

## Target Architecture

### 1. One Public Definition Shape

New multiblocks should expose a single `StructureDefinition`.

Single-piece templates, legacy `FactoryBlockPattern`, and `BlockPattern` remain as adapters. A single-piece structure is
just a `StructureDefinition` with one piece.

Business code should not need to choose between `BlockPatternTemplate`, `BlockPattern`, and `MultiPiecePattern` for new
structures.

### 2. One Per-Controller Runtime

Each controller should own one `StructureRuntime`.

The runtime becomes the home for:

- Immutable definition/template references.
- Per-controller state (`MultiblockState`, `PieceRuntime` cache, dirty pieces).
- Formed flag and formed metadata.
- Channel values and ability counts.
- Missing ability information.
- Async/snapshot job status.
- Last check result and failure trace.

The controller should eventually call runtime operations instead of directly coordinating every checker branch.

### 3. One Operation Evaluator

All structure actions should go through one traversal/evaluation path:

- `CHECK`
- `DIAGNOSE`
- `PREVIEW`
- `HINTS`
- `CREATIVE_BUILD`
- `SURVIVAL_BUILD`
- `SNAPSHOT_CHECK`
- `ITERATE`

This removes drift between JEI preview, auto-build, async check, and main-thread formation checks.

### 4. Element Runtime Boundary

V3 element execution should be a primary runtime contract, not a wrapper around `TraceabilityPredicate`.

The new element contract should cover:

- `check(context)`
- `couldBeValid(context)`
- `describe(context)`
- `candidates(context)`
- `place(context)`
- `survivalPlace(context)`
- `collectRequirements(context)`

`toPredicate()` must not be required for new elements. Legacy predicates should enter the system through a
`LegacyElement` or equivalent adapter. New elements may optionally expose a predicate adapter for old callers, but the
operation evaluator must not depend on that adapter.

### 5. Pure Matching And Side-Effect Collection

Structure elements should separate pure matching from side effects.

`check(context)` should read the world or snapshot and the element's immutable configuration. It should not directly
mutate controller state, formed metadata, hatch lists, count maps, or failure state.

Ability collection, hatch registration, tier capture, coil capture, channel values, count limits, and diagnostic errors
should flow through a collector owned by the operation context. Suggested collector responsibilities:

- Record matched multiblock parts and abilities.
- Track global and per-layer counts.
- Capture channel values and tier choices.
- Validate uniformity constraints such as coils.
- Accumulate formed metadata.
- Record the first meaningful failure with expected and actual values.

This is the most important tightening point in the current system.

### 6. Unified Orientation

V3 should introduce a `StructureOrientation` value object that represents:

- Front direction.
- Up direction.
- Rotation.
- Flip.
- Allowed orientation limits.

The existing `front/up/flipped` fields and `allowsExtendedFacing()` remain as compatibility surfaces while the internal
checker moves to a single orientation transform.

### 7. Runtime World Index

World block changes should mark runtimes or pieces dirty through a `StructureWorldIndex`.

The scheduler decides whether to run:

- No check.
- Dirty-piece check.
- Full main-thread check.
- Async snapshot check.
- Fallback polling check.

The controller should not duplicate this policy.

### 8. Tooling And Channel Data

GregTech should keep `DeclarativePatternBuilder` as the domain-facing DSL, but its output should converge on
`StructureDefinition`.

`buildTemplate()` should become a compatibility API. New code should prefer `buildDefinition()` /
`buildStructureDefinition()`.

GregTech-specific DSL features remain first-class:

- `casing()`
- `tieredCasing()`
- hatch and ability declarations
- channel declarations
- automatic tooltips
- global ability limits

StructureLib's channel-trigger tool chain can be adapted later as a GregTech channel item or GUI, but that is a tooling
feature on top of the runtime boundary, not the foundation of the redesign.

## Architecture Invariants

The migration should preserve the following invariants even while old and new declarations coexist:

1. A `StructureDefinition` and its compiled templates are immutable and may be shared by every controller instance.
2. Mutable caches, dirty flags, formed metadata, and last-operation state belong to one controller runtime.
3. Every check or build attempt owns an operation-local session. Failed alternatives, failed orientations, previews, and
   build probes must not leak state into another attempt.
4. Background work may read immutable definitions and snapshots, but it must not mutate controllers, tile entities,
   world state, or controller-owned runtimes.
5. Controller state changes occur only when a completed result is committed on the server thread.
6. A failed operation preserves the last successfully committed formed state until the controller explicitly decides to
   invalidate it. Evaluation and invalidation are separate decisions.
7. Compatibility adapters may preserve legacy behavior, but new runtime code must not depend on legacy predicate
   internals or string-keyed context conventions.
8. Preview, hints, creative build, survival build, live checks, and snapshot checks must use the same orientation and
   piece-position rules.

These invariants are more important than class names. Transitional classes may change as long as the ownership and
commit boundaries become stricter.

## Operation Model

### Operation Request

Every public structure action should be normalized into one immutable request before traversal begins. The request
should contain:

- Operation kind.
- Controller reference or controller identity when the operation is detached.
- `StructureDefinition` and controller-owned `StructureRuntime`.
- World access or immutable snapshot access.
- Controller position and `StructureOrientation`.
- Channel/trigger configuration.
- Player, item source, and placement environment for build operations.
- Limits such as placement budget, diagnostic depth, and whether hatch placement is allowed.

The evaluator should not infer missing operation data from global state halfway through traversal. A request should
either be valid at construction time or fail before visiting cells.

### Operation Session

One request creates one root `StructureMatchSession`. The session owns all speculative mutable data:

- Match context and typed values.
- Element, layer, piece, and global counts.
- Collected parts and abilities.
- Channel and tier selections.
- Repeat counts and resolved piece centers.
- Visited positions and dirty-piece information.
- Diagnostic candidates and failure details.

Alternative branches use `fork()` or checkpoints. A branch commits only after its complete cell and deferred
requirements succeed. This rule applies to:

- Predicate/element chains.
- Flip and orientation attempts.
- Repeat-count searches.
- Conditional and dynamic pieces.
- Survival-build "already valid" probes.

`PatternMatchContext` remains the compatibility storage during migration. New V3 state should use typed
`StructureSessionKey<T>` values or dedicated collector fields so unrelated features cannot collide on string keys.

### Cell Evaluation

The target cell sequence is:

1. Resolve the piece-local coordinate to one world coordinate through `StructureOrientation`.
2. Update a reusable `StructureEvaluationContext` with read-only request data and the operation-local collector.
3. Register deferred requirements for the element.
4. Evaluate the element against live world access or snapshot access.
5. Record only operation-local effects.
6. Commit the cell branch or restore its checkpoint.

Matching is "pure" relative to the controller and world. It may record effects in the operation-local collector, but it
must not attach parts, update machine fields, place blocks, mark the runtime formed, or write a failure directly to the
controller.

### Operation Result

Traversal should produce one immutable result shape for every operation. The exact Java type may evolve, but it must be
able to carry:

- Outcome: success, mismatch, missing requirements, unsupported operation, stale snapshot, partial placement, or error.
- Resolved orientation and flip.
- Formed metadata, repeat counts, piece centers, channels, and tier values.
- Collected parts and ability counts.
- Visited positions for world-index registration.
- A structured `StructureFailureTrace`.
- Placement progress and consumed/required item information for survival build.

Legacy `PatternMatchContext`, `PatternError`, and boolean return values should be views of this result, not independent
sources of truth.

### Commit Phase

The controller or runtime commits a successful check result in one server-thread phase:

1. Reject the result if the controller, definition generation, orientation, or world snapshot version is stale.
2. Validate part sharing and controller-specific ability filters.
3. Diff old and new parts and abilities.
4. Attach/detach parts.
5. Publish formed metadata and channels.
6. Update dirty-piece/runtime caches and world-index registration.
7. Invoke `formStructure()` or the soft-reassembly callback.
8. Clear the last failure only after commit succeeds.

Failure commit stores diagnostics and missing requirements. Whether an already formed controller is invalidated remains
a controller lifecycle policy, not an element or traversal side effect.

## Operation Semantics

The operation kind controls which effects are legal:

| Operation | Reads world/snapshot | Collects formation state | Mutates world | Commits controller state |
|---|---:|---:|---:|---:|
| `CHECK` | Yes | Yes | No | On success/failure policy |
| `DIAGNOSE` | Yes | Isolated | No | Last diagnostic only |
| `PREVIEW` | No | No | No | No |
| `HINTS` | Yes | No | Hint particles only | No |
| `CREATIVE_BUILD` | Yes | No | Yes | No |
| `SURVIVAL_BUILD` | Yes | No | Yes, budgeted | No |
| `SNAPSHOT_CHECK` | Snapshot only | Yes | No | Later, on server thread |
| `ITERATE` | Optional | No | No | No |

Preview and construction may use disposable sessions for channel selection and element branching, but those sessions
must never become formation sessions. In particular, an "already valid" probe during survival construction must run in
a child session whose effects are always discarded.

## Snapshot And Concurrency Rules

Snapshot checking is valid only when every active piece and element can execute from `IBlockAccess` data captured by the
snapshot. V3 should make this explicit rather than relying on a null `World` failure.

Each compiled element should eventually advertise operation capabilities, including:

- Live-world match support.
- Snapshot match support.
- Preview support.
- Hint support.
- Creative placement support.
- Survival placement support.

An unsupported capability does not mean "mismatch". It means the evaluator must select a safe fallback, usually a
main-thread live check.

Async results need a generation token containing at least the definition/runtime generation, orientation, and snapshot
or world-index version. The server-thread commit phase must discard stale results without changing formed state.

World block callbacks should only mark a controller or piece dirty. They must not run a nested structure check. The
scheduler decides whether the next action is a dirty-piece check, full live check, snapshot check, or polling fallback.

## Compatibility Boundary

Compatibility should be directional:

```text
legacy declaration -> LegacyElement/definition adapter -> V3 evaluator
new definition      -> direct compiled element          -> V3 evaluator
```

The V3 evaluator must not convert direct elements back into predicates to perform matching. Predicate views may remain
for legacy preview, tooltip, and addon APIs until those callers migrate.

`LegacyElement` may continue to expose legacy side effects through an isolated `PatternMatchContext`, but it must obey
session checkpoint/restore semantics. Legacy elements that cannot safely run against snapshots must force a live-world
fallback.

Controller compatibility hooks follow the same rule:

- `createStructureDefinition()` is canonical.
- `createMultiPiecePattern()`, `createStructureTemplate()`, and `createStructurePattern()` are input adapters.
- `BlockPattern`, `BlockPatternTemplate`, and legacy error accessors are output facades.
- No new core controller should add logic that is reachable only through a legacy hook.

## Failure Selection

Diagnostics must be deterministic. The evaluator should retain the failure from the branch that made the most progress,
with stable tie-breaking by definition order. A useful failure record contains:

- Piece name, repeat index, and local coordinate.
- World position and orientation.
- Element description and candidates.
- Actual block/tile snapshot.
- Deferred requirement deficits or excess counts.
- Whether the failure came from matching, capability support, assembly, stale async data, or commit validation.

When both non-flipped and flipped checks fail, missing required abilities should not be hidden by a less useful generic
cell mismatch from the other branch. The selection policy belongs in the evaluator and should be covered by tests.

## Trace Logging

Uncertain failures should first become observable. V3 adds trace logging behind `debugStructureTrace`.

Trace events should include:

- Controller id and position.
- Formed state.
- Orientation (`front`, `up`, `flipped`, and later `StructureOrientation`).
- Check path (`definition`, `legacy-template`, `multi-piece`, `async`, `runtime`).
- Operation (`CHECK`, `SURVIVAL_BUILD`, `SNAPSHOT_CHECK`, etc.).
- Result (`formed`, `still-valid`, `failed`, `invalidated`).
- Piece name and local structure coordinates when available.
- Expected element description and actual block/tile when available.
- Missing abilities.
- Pattern error position when available.
- Formed metadata and channel values where available.

`StructureFailureTrace` should store the last failure on the runtime for commands such as
`/gt_structure_trace <pos>`.

When behavior is uncertain, prefer adding trace output before changing matching logic.

Trace logging and stored failure state serve different purposes:

- Debug trace is chronological and may contain many lifecycle events.
- `StructureFailureTrace` is one stable, user-facing summary of the latest relevant failure.
- A successful preview or build must not clear the last formation failure.
- A successful committed structure check clears the last formation failure.
- Async stale-result rejection should be traceable but should not replace a more useful current mismatch.

## Migration Plan

Status labels describe the code in the 2026-06-12 implementation snapshot.

1. **Done:** Add `debugStructureTrace` and lightweight lifecycle trace events without changing matching behavior.
2. **Done:** Add `StructureFailureTrace` and store observable failures where the existing checker knows them.
3. **Done:** Add `StructureRuntime` and a thin `StructureOperationEvaluator`.
4. **Mostly done:** Formed metadata, channels, missing abilities, and last failure are runtime-owned. Checker caches,
   formed flag, attached parts, and ability instances still live in controller/piece state.
5. **Done, transitional:** Introduce `StructureEvaluationContext` and `StructureMatchCollector` backed by
   `PatternMatchContext`.
6. **Done:** Make direct elements independent of mandatory `toPredicate()` execution.
7. **Done:** Keep `TraceabilityPredicate` support through `LegacyElement`.
8. **Done:** Execute direct elements through `CompiledStructureElement`.
9. **Done:** Convert block, air, any, self, chain, and wrapper elements.
10. **Done:** Convert hatch, casing, tiered casing, coil, and related domain elements to direct execution paths.
11. **In progress:** Route check, preview, creative build, and iteration through the evaluator while preserving existing
    traversals.
12. **In progress:** Route survival build and hints through the same operation request/session/result model.
13. **Mostly done:** Convert fixed core controllers to `createStructureDefinition()`. Dynamic charcoal-pile and Godforge
    paths remain intentional migration cases; addon compatibility hooks remain.
14. **In progress:** Extract controller orchestration into scheduler, assembly, registration, preview, channel, and
    client-hook helpers.
15. **In progress:** Assembly/reassembly is validated before runtime state publication and commit rejection is
    observable. The remaining work is an immutable operation result plus one reusable server-thread commit phase.
16. **Planned:** Move collector data out of string-keyed `PatternMatchContext` into typed operation state.
17. **Planned:** Add explicit element operation capabilities and stale async-result rejection.
18. **Planned:** Converge single-piece, multi-piece, live, and snapshot checks on one traversal implementation.
19. **Planned:** Converge preview, hints, creative build, survival build, and iteration on the same coordinate traversal.
20. **Planned:** Add diagnostic command and in-game structure trace view.

## Verification Matrix

Each migration step should preserve behavior across this matrix:

- Single fixed piece and repeatable single piece.
- Fixed multi-piece, conditional piece, dynamic-offset piece, and repeat group.
- Normal and flipped orientation for every supported front/up pair.
- Live-world and snapshot check equivalence.
- Initial formation, still-valid recheck, soft reassembly, and invalidation.
- Event-driven dirty check, periodic polling, async check, and async fallback.
- Required ability, optional ability, grouped ability, minimum/maximum counts, and shared parts.
- Uniform and non-uniform tier/channel capture.
- Preview and build with default, minimum, maximum, and explicit channel values.
- Creative build, survival build with insufficient items, and survival resume after partial placement.
- Legacy `FactoryBlockPattern`, legacy custom predicate, direct element, and mixed chain.

High-risk transactional tests should deliberately fail after recording a part, count, channel, or tier and assert that
the failed branch leaves no state behind.

## V3 Completion Criteria

V3 is the primary runtime when all of the following are true:

- Controller lifecycle code submits requests and commits results instead of coordinating traversal branches.
- One traversal engine handles live and snapshot matching for both single-piece and multi-piece definitions.
- Preview, hints, creative build, survival build, and iteration share the same coordinate/orientation walker.
- Direct elements never require predicate conversion for execution.
- Formation effects are operation-local until a server-thread commit succeeds.
- Failed branches and non-check operations cannot pollute formation state.
- Snapshot capability and stale-result handling are explicit and tested.
- `StructureFailureTrace` can identify the failing piece/cell or deferred requirement without enabling broad debug logs.
- New core multiblocks only declare `StructureDefinition`; legacy hooks are compatibility-only.
- Removing a compatibility facade would require addon migration, but would not require redesigning the V3 evaluator.

## Non-Goals For The First Pass

- No rewrite of `MultiblockState` matching.
- No behavior change to structure formation.
- No removal of legacy `FactoryBlockPattern`.
- No removal of `TraceabilityPredicate`.
- No change to JEI preview format.
- No change to auto-build placement rules.
- No broad controller refactor before trace logging exists.
