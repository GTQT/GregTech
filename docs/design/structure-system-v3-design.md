# Structure System V3 Design

**Implementation status snapshot:** 2026-06-12

## Executive Summary

V3 keeps GregTech's existing multiblock domain model, but moves structure checking toward a cleaner library boundary
inspired by StructureLib:

- `StructureDefinition` is the canonical public declaration shape for new multiblocks.
- `StructureRuntime` is the canonical per-controller owner for V3 structure state.
- Structure operations should normalize into request, session, result, and server-thread commit phases.
- Direct `IStructureElement` implementations are the primary cell runtime. `TraceabilityPredicate` remains as a
  compatibility adapter, not the execution model for new elements.

The goal is not to copy StructureLib and not to replace the current checker in one pass. The current strategy is to
keep existing behavior observable, preserve addon compatibility, and tighten ownership boundaries until one traversal
engine can safely serve checks, previews, hints, creative build, survival build, iteration, and snapshot checks.

When matching behavior is uncertain, prefer adding trace output first and ask for the resulting logs before changing
logic.

## Design Drivers

### What StructureLib Does Better

StructureLib has a stronger separation between structure declaration, structure execution, and user tools:

- `IStructureDefinition<T>` is a library-level contract. It can check, hint, build, survival-build, and iterate with
  the same shape definition.
- `IStructureElement<T>` owns cell-level behavior: match, hint, description, possible blocks, and placement.
- `ExtendedFacing` represents direction, rotation, and flip as one complete orientation state.
- `IConstructable` and `ISurvivalConstructable` let tools trigger hints and construction without knowing the machine
  implementation.
- `couldBeValid`, `getDescription`, hint particles, and instrumentation make bad structures diagnosable.
- Channel data can be configured on the trigger item and passed through the same build/hint path.

GregTech should absorb these boundaries, not copy the implementation directly.

### What GregTech Should Keep

GregTech already has structure capabilities that are more specific and more valuable than StructureLib's generic layer:

- Immutable `BlockPatternTemplate` / `PieceTemplate` plus per-controller `MultiblockState`.
- `TemplatePool` and `SoftTemplate` for shared cached definitions.
- `DeclarativePatternBuilder` with casing, hatch, ability, tier, and channel semantics.
- `StructureDefinition`, `MultiPiecePattern`, `PieceRuntime`, dirty-piece tracking, and snapshot checking.
- `PatternMatchContext`, `MultiblockAbility`, and formed metadata integrated with machine behavior.

V3 should make these concepts easier to reason about and less scattered.

### Non-Goals For The First Pass

- No rewrite of `MultiblockState` matching.
- No behavior change to structure formation.
- No removal of legacy `FactoryBlockPattern`.
- No removal of `TraceabilityPredicate`.
- No change to JEI preview format.
- No change to auto-build placement rules.
- No broad controller refactor before trace logging exists.

## Terminology And Ownership

| Concept | Ownership | Role |
|---|---|---|
| `StructureDefinition` | Immutable, shared | Canonical public declaration for new structures. |
| `PieceTemplate` | Immutable, shared | Canonical compiled piece representation. |
| `BlockPatternTemplate` | Immutable, shared | Compatibility facade for single-piece APIs and legacy tools. |
| `MultiPiecePattern` | Immutable, shared | Compiled multi-piece structure shape. |
| `MultiblockState` | Per controller | Mutable single-piece checker state retained for compatibility. |
| `PieceRuntimes` / `PieceRuntime` | Per controller | Mutable multi-piece checker state, dirty flags, caches, and formed positions. |
| `StructureRuntime` | Per controller | V3 home for resolved definition, compatibility views, formed metadata, channels, missing abilities, evaluator, and last failure. |
| `StructureMatchSession` | Per operation attempt | Speculative state for branches, forks, checkpoints, collectors, and typed context. |
| `StructureOperationState` | Per operation result | Typed collected state for parts, active casing positions, counts, and requirements. |
| `StructureOrientation` | Immutable value | Captures controller front, structure front, up direction, flip, and flip policy for operation tokens and diagnostics. |
| `PatternMatchContext` | Compatibility view | Legacy string-keyed context. New state should move to typed keys or collectors. |
| `StructureCheckResult` | Immutable operation result | Normalized synchronous check result before assembly and commit. |
| `MultiblockStructureCommitter` | Server-thread commit helper | Validates prepared results and publishes controller/runtime state. |

The important rule is simple: immutable definitions may be shared, but mutable checker state and formed state must belong
to one controller runtime or one operation-local session.

## Current Implementation Snapshot

### Already Implemented

GregTech already has part of the V3 shape:

- Controller runtime initialization resolves a canonical `StructureDefinition<?>` first. Direct
  `createStructureDefinition()` wins; legacy `createMultiPiecePattern()` and `createStructureTemplate()` hooks are
  adapted into definitions before runtime/checker code sees them.
- `FactoryBlockPattern`, `BlockPatternTemplate`, `BlockPattern`, and `MultiPiecePattern` are compatibility surfaces for
  old declarations and tools. New structure declarations should return one definition from
  `createStructureDefinition()`.
- `StructureRuntime` exists as the per-controller state holder. It carries the resolved definition, single-template
  compatibility view, piece runtimes, formed metadata, channel values, missing abilities, evaluator, and last failure.
- Formed metadata, formed channel values, missing abilities, and last failure now have one canonical owner in
  `StructureRuntime`. `MultiblockControllerBase` no longer mirrors metadata and channels in separate fields.
- `StructureOperationEvaluator` is the thin operation boundary used by controller checks, previews, creative build
  tools, legacy `BlockPattern`, and structure iteration while delegating to the existing implementations.
- `StructureOperationRequest` exists as the first immutable request shape for runtime-routed operations. It currently
  backs controller checks, single-piece and multi-piece previews, creative build, survival build entry points,
  single-piece and multi-piece hints, and iteration. Survival build still delegates to the existing legacy auto-build
  placement semantics, but now carries `SURVIVAL_BUILD` as the operation token through the shared request/runtime
  boundary. Tool callers now create one build request that selects `CREATIVE_BUILD` or `SURVIVAL_BUILD` from the
  player mode; dynamic controller build hooks also receive that request and execute channel-generated definitions
  through disposable `StructureRuntime` instances. `StructureRuntime` now accepts these requests and keeps
  `StructureOperationEvaluator` as an internal delegating implementation detail for runtime-owned operations.
- Single-piece fixed-repetition traversal now has a shared cell walker for orientation-aware coordinate resolution.
  Creative build, single-piece preview, hints, and structure iteration use that walker where they touch fixed cells.
  Creative build keeps legacy candidate selection in an operation-local build adapter and updates a `CREATIVE_BUILD`
  cell context without collecting formation requirements; single-piece preview keeps the legacy JEI/projector array
  layout by using a preview-local orientation over the shared cell traversal.
- Synchronous definition and legacy-template checks are normalized into immutable `StructureCheckResult` values before
  controller assembly. Match context, channel values, operation state, and missing abilities are copied at this boundary.
- Initial formation and reassembly produce one `PreparedCommit` shape and pass through
  `MultiblockStructureCommitter`. Commit validation happens before controller/runtime state is published.
- Successful definition checks validate assembly/reassembly before publishing runtime state. Part-sharing rejection is
  recorded as a `COMMIT` failure and does not overwrite the last committed formed metadata.
- `StructureEvaluationContext`, `StructureMatchCollector`, `StructureOperationState`, and `StructureMatchSession` carry
  typed collector state through V3 checks, forks, checkpoints, result creation, and assembly. Legacy context-only
  traversals still use compatibility keys.
- `gregtech.api.pattern.element.IStructureElement` exists and has context-aware methods for check, candidates, hints,
  creative placement, survival placement, and deferred requirement collection.
- New elements no longer require `toPredicate()`. `CompiledStructureElement` executes direct elements without routing
  them through a predicate. `LegacyElement` retains predicate execution for compatibility declarations.
- Block, air, any, self, chain, wrapper, hatch, casing, tiered casing, coil, and related domain declarations have direct
  runtime paths.
- Compiled elements expose `StructureElementCapability`. Snapshot matching is opt-in; legacy predicates, tile-entity
  elements, wrappers with callbacks, and conditional pieces fall back to live checks when needed.
- Async snapshot tasks carry registration/runtime generations, orientation, and covered-chunk change revisions. Stale
  tasks and results are trace-rejected before they can change formed or failure state.
- `StructureOrientation` exists as the initial unified orientation value object. Async check tokens, failure trace
  construction, controller-facing evaluator check/iteration entry points, definition/check-state/AABB entry points,
  `StructurePiece` center/snapshot entry points, `StructureCompiler` snapshot closures, template AABB/predicate facades,
  repeat-group live/snapshot slice, backtracking, axis-line, and auto-build metadata paths, multi-piece dirty/full check
  entry points, `MultiblockState` exact live/snapshot/axis-line traversal loops, and creative-build evaluator paths use
  it. The remaining
  `front/up/flipped` arguments are compatibility facades, low-level `RelativeDirection` inputs, preview/hint placement
  internals, and addon-facing legacy auto-build adapters that still read orientation from controller state.
- Controller-side orchestration has started moving into focused helpers:
  `MultiblockStructureCheckScheduler`, `MultiblockStructureAssembler`, `MultiblockStructureRegistration`,
  `MultiblockStructureCommitter`, `MultiblockStructurePreviews`, `MultiblockStructureChannels`, and
  `MultiblockControllerClientHooks`. These helpers are transitional implementation boundaries, not new addon APIs.
- `PieceTemplate` is the canonical compiled piece representation for new definitions. `BlockPatternTemplate` remains the
  public compatibility facade required by existing APIs and controller fields.

### Controller Migration Snapshot

Fixed single-piece and multi-piece core controllers are being migrated to return cached definitions directly. Completed
or already definition-backed examples include the vacuum freezer, implosion compressor, coke oven, saw mill, steam
grinder, steam oven, active transformer, battery accumulator, network switch, research station, primitive water pump,
primitive blast furnace, pyrolyse oven, processing array, multi smelter, multi alloy furnace, electric blast furnace,
cracking unit, large chemical reactor, multiblock tank, large boiler, large combustion engine, large turbine, large
miner, fluid drill, fusion reactor, assembly line, distillation tower, data bank, HPCA, power substation, central
monitor, and cleanroom.

Some controllers intentionally still build definitions from compatibility pieces:

- Cleanroom returns a `StructureDefinition`, but still builds dynamic `FactoryBlockPattern` instances internally because
  its dimensions are discovered at runtime. Its tool-triggered dynamic build path wraps those generated templates in a
  disposable `StructureRuntime` instead of bypassing the operation request boundary.
- Large turbine, large miner, fluid drill, and fusion reactor still expose existing `buildTemplate()` and registration
  compatibility APIs, but controller runtime consumes registered structures through cached `StructureDefinition`
  adapters.
- The network switch explicitly overrides `createStructureDefinition()`, preventing the canonical resolver from
  selecting the inherited data bank definition before reaching the switch structure.

The remaining controller-owned legacy hooks are mainly the dynamically generated charcoal-pile check/preview path and
the Godforge controller/module path, plus base compatibility surfaces for addons.

### Known Gaps

The migration is not complete yet:

- Legacy declarations and custom predicate alternatives can still execute through `TraceabilityPredicate`.
- Context-only legacy traversals still use `PatternMatchContext` collector keys as an adapter. Session-backed V3 checks
  carry typed collector state through `StructureCheckResult` and only materialize legacy keys for compatibility
  callbacks.
- `StructureOperationEvaluator` delegates to separate single-piece, multi-piece, preview, build, hint, and iteration
  traversals. Creative build, single-piece preview, hints, and iteration now share the fixed-repetition coordinate
  walker where they touch single-piece cells, but matching, snapshot checks, multi-piece preview assembly, and survival
  build have not yet converged on one implementation.
- Survival build and hints have request/runtime entry points, but they are not fully routed through the same
  session/result model as checks. Survival build still uses the legacy candidate-selection and placement side effects;
  hints now have trigger-aware tool callers and multi-piece/dynamic-piece traversal, while visible hint behavior still
  depends on each element's hint implementation.
- Global ability policy and diagnostics still span session, controller, runtime, and legacy error objects.
- `MultiblockControllerBase` still owns lifecycle policy such as invalidation and exposes the final `formStructure()`
  callback. Synchronous check-result publication, part attachment, runtime publication, and registration now pass
  through `MultiblockStructureCommitter`; direct async-result publication remains future work.
- Snapshot capability is explicit for compiled elements, but legacy predicates, tile-entity elements, and conditional
  piece activation remain live-world fallbacks until immutable snapshot inputs cover those cases.
- Survival construction may call `check` to decide whether a block is already valid. If `check` mutates match state, the
  construction path can accidentally pollute formation state.

These gaps mean the element runtime needs a tighter architectural boundary. They do not mean the whole structure system
should be deleted and rewritten.

## Target Architecture

### 1. One Public Definition Shape

New multiblocks should expose a single `StructureDefinition`.

Single-piece templates, legacy `FactoryBlockPattern`, and legacy `BlockPattern` remain as adapters. A single-piece
structure is just a `StructureDefinition` with one piece. Business code should not need to choose between
`BlockPatternTemplate`, `BlockPattern`, and `MultiPiecePattern` for new structures.

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

### 4. Direct Element Runtime Boundary

V3 element execution should be a primary runtime contract, not a wrapper around `TraceabilityPredicate`.

The new element contract should cover:

- `check(context)`
- `couldBeValid(context)`
- `describe(context)`
- `candidates(context)`
- `place(context)`
- `survivalPlace(context)`
- `collectRequirements(context)`

`toPredicate()` must not be required for new elements. Legacy predicates should enter the system through `LegacyElement`
or an equivalent adapter. New elements may optionally expose a predicate adapter for old callers, but the operation
evaluator must not depend on that adapter.

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

V3 has introduced a `StructureOrientation` value object that represents:

- Front direction.
- Structure front direction.
- Up direction.
- Flip.
- Allowed orientation limits.

The existing `front/up/flipped` fields, low-level method arguments, and `allowsExtendedFacing()` remain as compatibility
surfaces while the internal checker moves to a single orientation transform.

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

`buildTemplate()` should become a compatibility API. New code should prefer `buildDefinition()` or
`buildStructureDefinition()`.

GregTech-specific DSL features remain first-class:

- `casing()`
- `tieredCasing()`
- hatch and ability declarations
- channel declarations
- automatic tooltips
- global ability limits

StructureLib's channel-trigger tool chain can be adapted later as a GregTech channel item or GUI. That is a tooling
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

Alternative branches use forks or checkpoints. A branch commits only after its complete cell and deferred requirements
succeed. This rule applies to:

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

Matching is pure relative to the controller and world. It may record effects in the operation-local collector, but it
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

The synchronous implementation now enters this phase through `MultiblockStructureCommitter`. It validates a
`PreparedCommit` before mutating controller state and is shared by initial formation and soft reassembly. Step 1 remains
future work for direct async-result publication; the current async checker only uses snapshot matches to request a fresh
main-thread check.

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

Each compiled element should advertise operation capabilities, including:

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

## Failure Diagnostics And Trace Logging

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

Trace logging exists to make uncertain failures observable. V3 adds trace logging behind `debugStructureTrace`.

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

Trace logging and stored failure state serve different purposes:

- Debug trace is chronological and may contain many lifecycle events.
- `StructureFailureTrace` is one stable, user-facing summary of the latest relevant failure.
- A successful preview or build must not clear the last formation failure.
- A successful committed structure check clears the last formation failure.
- Async stale-result rejection should be traceable but should not replace a more useful current mismatch.

When behavior is uncertain, add trace output before changing matching logic.

## Migration Roadmap

Status labels describe the code in the 2026-06-12 implementation snapshot.

### Done

1. Add `debugStructureTrace` and lightweight lifecycle trace events without changing matching behavior.
2. Add `StructureFailureTrace` and store observable failures where the existing checker knows them.
3. Add `StructureRuntime` and a thin `StructureOperationEvaluator`.
4. Introduce `StructureEvaluationContext` and `StructureMatchCollector` backed by `PatternMatchContext`.
5. Make direct elements independent of mandatory `toPredicate()` execution.
6. Keep `TraceabilityPredicate` support through `LegacyElement`.
7. Execute direct elements through `CompiledStructureElement`.
8. Convert block, air, any, self, chain, wrapper, hatch, casing, tiered casing, coil, and related domain elements to
   direct execution paths.
9. Normalize synchronous checks into `StructureCheckResult`.
10. Share the validation-before-publication commit phase for initial assembly and reassembly through
    `MultiblockStructureCommitter`.
11. Carry collector-owned parts, counts, active positions, and requirements through session forks, checkpoints,
    immutable check results, and assembly for session-backed V3 checks.
12. Advertise compiled element capabilities and reject stale async snapshot tasks/results before they can change runtime
    state.
13. Add the initial `StructureOrientation` value object and use it for async tokens, failure trace construction,
    controller-facing evaluator check/iteration entry points, definition/check-state/AABB entry points,
    `StructurePiece` center/snapshot entry points, `StructureCompiler` snapshot closures, template AABB/predicate
    facades, repeat-group live/snapshot slice, backtracking, axis-line, and auto-build metadata paths, multi-piece
    dirty/full check entry points, and `MultiblockState` exact live/snapshot/axis-line traversal loops.
14. Add orientation-native creative-build entry points through `StructureOperationEvaluator`, `MultiPiecePattern`,
    repeat-group construction, and `MultiblockState` placement traversal while keeping legacy build signatures as
    compatibility adapters.
15. Add the initial immutable `StructureOperationRequest` and route controller checks, single-piece previews, creative
    build, and iteration through request-backed evaluator methods while preserving the old public entry points.
16. Route controller, preview-helper, projector, and multiblock-builder runtime operations through
    `StructureRuntime` request methods instead of exposing evaluator calls at those runtime-owned call sites. The
    structure projector and legacy multiblock builder no longer fall back to direct `MultiPiecePattern`,
    `PieceRuntimes`, or `MultiblockState.autoBuild` placement paths; they submit runtime-owned build requests.
17. Introduce the single-piece fixed-repetition cell walker in `MultiblockState` and route creative-build placement and
    formed-block iteration through it. Creative build now carries operation-local build state and updates a
    `CREATIVE_BUILD` cell context while preserving the existing candidate-selection and placement behavior.
18. Add a hint request path from the structure projector and legacy multiblock builder through `StructureRuntime`.
    Single-piece hints use the fixed cell walker; multi-piece hints traverse active pieces, repeat groups, and dynamic
    offsets using the same piece-center metadata rules as build/preview paths.
19. Route single-piece preview coordinate projection through the fixed-repetition cell walker while preserving the
    existing JEI/projector `BlockInfo[][][]` layout and preview candidate-selection semantics.
20. Route tool-triggered build requests through one runtime build dispatcher. The projector and legacy builder now
    create `CREATIVE_BUILD` or `SURVIVAL_BUILD` requests from player mode, single-piece and multi-piece builds share the
    runtime dispatch surface, and Cleanroom/charcoal-pile dynamic builds wrap their channel-generated templates in
    disposable `StructureRuntime` instances instead of bypassing the request boundary. The old controller
    `autoBuildStructure(player, channels, skipHatches)` hook remains only as an addon compatibility bridge.

### Mostly Done

1. Formed metadata, channels, missing abilities, and last failure are runtime-owned. Checker caches, formed flag,
   attached parts, and ability instances still live in controller/piece state.
2. Fixed core controllers are largely converted to `createStructureDefinition()`. Dynamic charcoal-pile and Godforge
   paths remain intentional migration cases; addon compatibility hooks remain.

### In Progress

1. Continue expanding request/session/result coverage for operation types that still have partial routing, especially
   survival build, hint result reporting, and diagnostics, while preserving existing traversals and legacy evaluator
   facades.
2. Promote the fixed-repetition cell walker from a creative-build/iteration helper into the common coordinate layer for
   multi-piece preview assembly and survival build once each caller's side effects are explicit.
3. Finish giving survival build and hints the same session/result model as checks. Request/runtime routing now exists
   for tool-triggered fixed, multi-piece, and dynamic build paths; remaining work is session/result collection,
   placement budgets, item-source accounting, and hint result reporting.
4. Extract controller orchestration into scheduler, assembly, registration, preview, channel, and client-hook helpers.

### Planned

1. Converge single-piece, multi-piece, live, and snapshot checks on one traversal implementation.
2. Converge multi-piece preview assembly with the fixed-repetition coordinate layer without changing JEI/projector
   layout.
3. Add a survival-build request/session/result shape with placement budgets, item-source accounting, and rollback-safe
   consumed/required item reporting.
4. Add structured hint results once hint rendering/particle effects are explicit per element.
5. Retire the remaining creative-build candidate-selection adapter once direct element placement and predicate fallback
   have one operation-local selection path.
6. Retire remaining `front/up/flipped` compatibility facades once template iteration, preview/hint placement, and
   addon-facing legacy hooks have orientation-native callers.
7. Add `StructureWorldIndex` or equivalent runtime dirty-index boundary.
8. Add diagnostic command and in-game structure trace view.

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
