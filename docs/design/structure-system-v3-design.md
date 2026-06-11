# Structure System V3 Design

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
- `StructureOperationEvaluator` is the thin operation boundary used by controller checks, previews, creative build
  tools, legacy `BlockPattern`, and structure iteration while delegating to the existing implementations.
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

However, the migration is not complete yet:

- Legacy declarations and custom predicate alternatives still execute through `TraceabilityPredicate`.
- `StructureMatchCollector` is backed by `PatternMatchContext`; collector state has not moved to a standalone operation
  result yet.
- The operation evaluator delegates to separate single-piece, multi-piece, preview, and build traversals. Those
  traversals have not yet converged on one implementation.
- Formed metadata, global ability policy, diagnostics, and failure reporting still span session, controller, runtime,
  and legacy error objects.
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

## Migration Plan

1. Add `debugStructureTrace` and lightweight trace events around existing controller lifecycle points without changing
   behavior.
2. Add `StructureFailureTrace` and store the last observable failure where existing code already knows the error.
3. Add `StructureRuntime` as a thin wrapper over existing controller fields.
4. Move formed metadata, missing abilities, channel values, and last failure into `StructureRuntime`.
5. Introduce an operation context and collector backed by the current `PatternMatchContext`.
6. Split the element boundary so new elements no longer require `toPredicate()`.
7. Keep `TraceabilityPredicate` support through a legacy adapter element.
8. Change `CompiledStructureElement` so new elements execute directly and only legacy adapters execute predicates.
9. Convert simple elements first: block, air, any, self, chain, and wrapper.
10. Convert GregTech domain elements next: hatch, casing, tiered casing, coil, and ability-limited elements.
11. Move check/build/preview/hint entry points into the operation evaluator while delegating to existing implementations.
12. Convert controller subclasses to only override `createStructureDefinition()` where practical.
13. Deprecate direct `BlockPattern` usage in new code.
14. Add diagnostic command and in-game structure trace view.

## Non-Goals For The First Pass

- No rewrite of `MultiblockState` matching.
- No behavior change to structure formation.
- No removal of legacy `FactoryBlockPattern`.
- No removal of `TraceabilityPredicate`.
- No change to JEI preview format.
- No change to auto-build placement rules.
- No broad controller refactor before trace logging exists.
