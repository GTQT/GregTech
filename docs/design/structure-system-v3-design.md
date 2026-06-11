# Structure System V3 Design

## Goals

The V3 structure system keeps GregTech's existing multiblock domain model, but gives the structure checker a cleaner
library boundary inspired by StructureLib.

The immediate goal is not to replace the current checker in one pass. The first step is to introduce a thin runtime
layer and trace logging so failures can be observed in real worlds before deeper behavior changes are made.

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

GregTech should absorb those boundaries, not copy the implementation directly.

## What GregTech Should Keep

GregTech already has structure capabilities that are more specific and more valuable than StructureLib's generic layer:

- Immutable `BlockPatternTemplate` / `PieceTemplate` plus per-controller `MultiblockState`.
- `TemplatePool` and `SoftTemplate` for shared cached definitions.
- `DeclarativePatternBuilder` with casing, hatch, ability, tier, and channel semantics.
- `StructureDefinition`, `MultiPiecePattern`, `PieceRuntime`, dirty-piece tracking, and snapshot checking.
- `PatternMatchContext`, `MultiblockAbility`, and formed metadata integrated with machine behavior.

V3 should make these concepts easier to reason about and less scattered.

## Target Architecture

### 1. One Public Definition Shape

New multiblocks should expose a single `StructureDefinition`.

Single-piece templates, legacy `FactoryBlockPattern`, and `BlockPattern` remain as adapters. A single-piece structure is
just a `StructureDefinition` with one piece.

### 2. One Per-Controller Runtime

Each controller should own one `StructureRuntime`.

The runtime becomes the home for:

- The immutable definition/template references.
- The per-controller state (`MultiblockState`, `PieceRuntimes`).
- Formed metadata and channel values.
- Dirty flags and async/snapshot status.
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

### 4. Pure Element Semantics

Structure elements should separate pure matching from side effects.

Suggested element contract:

- `check(context)`
- `couldBeValid(context)`
- `describe(context)`
- `candidates(context)`
- `place(context)`
- `survivalPlace(context)`
- `collectRequirements(context)`

Ability collection, tier capture, part registration, and other side effects should flow through a collector owned by the
operation context, not arbitrary mutation inside a cell predicate.

### 5. Unified Orientation

V3 should introduce a `StructureOrientation` value object that represents:

- front direction
- up direction
- rotation
- flip
- allowed orientation limits

The existing `front/up/flipped` fields and `allowsExtendedFacing()` remain as compatibility surfaces while the internal
checker moves to a single orientation transform.

### 6. Runtime World Index

World block changes should mark runtimes or pieces dirty through a `StructureWorldIndex`.

The scheduler decides whether to run:

- no check
- dirty-piece check
- full main-thread check
- async snapshot check
- fallback polling check

The controller should not duplicate this policy.

## Trace Logging

Uncertain failures should first become observable. V3 adds trace logging behind `debugStructureTrace`.

Trace events should include:

- controller id and position
- formed state
- front/up/flipped orientation
- check path (`definition`, `legacy-template`, `multi-piece`, `async`)
- result (`formed`, `still-valid`, `failed`, `invalidated`)
- missing abilities
- pattern error position when available
- formed metadata and channel values where available

Later, `StructureFailureTrace` should store the last failure on the runtime for commands such as
`/gt_structure_trace <pos>`.

## Migration Plan

1. Add `StructureRuntime` as a thin wrapper over existing fields.
2. Add trace logging to controller lifecycle points without changing behavior.
3. Move formed metadata, missing abilities, channel values, and last failure into `StructureRuntime`.
4. Move check/build/preview entry points into runtime while delegating to existing implementations.
5. Convert controller subclasses to only override `createStructureDefinition()`.
6. Deprecate direct `BlockPattern` usage in new code.
7. Add diagnostic command and in-game structure trace view.

## Non-Goals For The First Pass

- No rewrite of `MultiblockState` matching.
- No behavior change to structure formation.
- No removal of legacy `FactoryBlockPattern`.
- No change to JEI preview format.
- No change to auto-build placement rules.
