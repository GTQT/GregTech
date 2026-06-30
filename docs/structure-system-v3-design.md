# Structure System V3 Design

**Implementation snapshot:** 2026-06-30

**Scope:** `gregtech.api.pattern`, `gregtech.api.pattern.element`,
`gregtech.api.metatileentity.multiblock`, JEI/preview/tooling integration,
and GregTech's own multiblock controllers.

## 1. Current Boundary

Structure System V3 is the only structure declaration and runtime path in this
repository. Controllers declare structures through `StructureDefinition`; the
controller base resolves exactly that definition and builds runtime state from
the compiled `MultiPiecePattern`.

```text
StructureDefinition
  -> MultiPiecePattern / PieceTemplate / CompiledStructureElement
  -> StructureRuntime operation
  -> typed operation result
  -> MultiblockStructureCommitter
  -> StructureLifecycleState
```

Removed pre-V3 structure entry points must not be reintroduced in GregTech
controller, tooling, preview, JEI, scheduler, committer, async, registry, or
test code. External addons that need structure declarations must use the V3
types directly.

## 2. Canonical Types

Declaration and compilation:

- `StructureDefinition`
- `IStructurePiece`
- `StructureCompiler`
- `MultiPiecePattern`
- `PieceTemplate`
- `CompiledStructureElement`

Cell-level matching and dependency declaration:

- `IStructureElement`
- `ITypedStructureElement`
- `StructureEvaluationContext`
- `StructureContribution`
- `StructureDependency`

Runtime and lifecycle:

- `StructureRuntime`
- `StructureLifecycleState`
- `PieceRuntimeState`
- `PieceRuntimes`
- `CommittedStructureGraph`

Operation results:

- `StructureCheckResult`
- `StructureSnapshotResult`
- `StructureBuildResult`
- `StructureHintResult`
- `StructurePreviewResult`
- `StructureIterateResult`

Tooling and formed-state access:

- `FormedStructureView`
- `StructureElementPreviewEntry`
- typed candidates and typed preview metadata

## 3. Controller Path

`MultiblockControllerBase` creates one canonical structure definition:

```text
controller.reinitializeStructurePattern()
  -> resolveStructureDefinition()
  -> createStructureDefinition()
  -> StructureRuntime
```

Every GregTech multiblock controller must implement `createStructureDefinition()`.
Helpers should return V3 declarations or typed elements. They should not rebuild
parallel structure declaration systems or route internal behavior through
untyped state containers.

## 4. Runtime Ownership

Each controller instance owns a `StructureRuntime`. Shared objects such as
`StructureDefinition`, `MultiPiecePattern`, and `PieceTemplate` are immutable
compiled declarations. Per-instance data belongs in `StructureRuntime`,
`PieceRuntimeState`, `PieceRuntimes`, `StructureLifecycleState`, and committed
metadata.

Formation state is published only by `MultiblockStructureCommitter`:

```text
controller.checkStructurePattern()
  -> MultiblockStructureOperations.checkStructurePattern()
  -> StructureCommitToken.captureForCheck(controller)
  -> StructureRuntime.check(...)
  -> StructureCheckResult
  -> MultiblockStructureCommitter.applyCheckResult(...)
  -> StructureRuntime.publishLifecycleState(...)
  -> world index registration
```

Failure, stale results, and detached precheck results must not publish lifecycle
state directly.

## 5. Dependency Contract

Incremental eligibility is based on explicit typed dependency metadata:

- `StructureDependencyCompiler` reads element, condition, dynamic anchor,
  repeat group, and external dependency metadata.
- Direct structure elements must implement an explicit incremental support and
  dependency contract.
- Elements with unknown side effects must report opaque behavior so the checker
  uses the full active path.
- External state must be represented by `StructureExternalDependencyKey` and a
  matching snapshot.

When behavior is uncertain, add diagnostics first and let runtime logs decide
whether the element can safely become more specific.

## 6. Async Boundary

Detached workers may only consume immutable declarations, snapshots, compiled
element metadata, and copied block-state data. They must not access live
`World`, controller, tile entity, ability maps, inventories, or mutable runtime
owners.

Async work may produce precheck results. Server-thread code still owns commit,
aggregate folding, part registration, ability publication, world-index updates,
and lifecycle publication.

## 7. Preview And Tooling

JEI, projector, ghost rendering, and preview renderers consume
`StructureDefinition`, `MultiPiecePattern`, `StructureElementPreviewEntry`, and
typed candidate metadata.

Missing typed preview metadata should be logged at low frequency with enough
controller, piece, position, and channel detail to fix the declaration. Tooling
must not execute runtime-only validation logic just to discover display data.

Runtime-only pieces may participate in structure checks but are hidden from
normal preview, hint, projector, and build-all surfaces unless a tool explicitly
targets internal diagnostics.

## 8. Internal Rules

- GregTech controllers use `createStructureDefinition()`.
- Structure helper APIs return V3 declarations, pieces, conditions, or typed
  elements.
- `FormedStructureView` is the public read surface for committed formed data.
- Structure contribution builders are transaction scoped and rolled back on
  failure.
- Failure handling rolls back parts, abilities, channel values, metadata, and
  contribution state.
- Incremental eligibility is conservative by default.
- Preview and JEI surfaces are metadata driven.

## 9. Scan Targets

Use source scans to keep the V3 boundary tight:

```text
src/main/java/gregtech/common/metatileentities
src/main/java/gregtech/api/metatileentity/multiblock
src/main/java/gregtech/api/pattern
src/main/java/gregtech/integration/jei/multiblock
src/main/java/gregtech/client/renderer/handler
```

The scan should flag controller declarations that bypass `StructureDefinition`,
runtime code that publishes lifecycle state outside the committer, and tooling
that derives display data by running validation-only behavior.
