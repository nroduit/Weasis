# Display Op Chain

A 2D view does not paint the decoded image. It paints the result of a chain of image operations
(window/level, filter, color map, shutter, overlay, redaction, fusion, zoom and rotation) applied to
the source image of the view. The chain is also used to render exports and lens views, and to
preprocess images before they are cached.

## Table of Contents

- [Concepts](#concepts)
- [A pass](#a-pass)
- [What makes a node run](#what-makes-a-node-run)
- [Images and their release](#images-and-their-release)
- [Invariants](#invariants)
- [Extension points](#extension-points)

## Concepts

```
source ──▶ node 1 ──▶ node 2 ──▶ … ──▶ node n ──▶ display image ──▶ RenderedImageLayer
```

| Abstraction | Responsibility |
|---|---|
| `ImageOpNode` | One operation. Holds its parameters, its input image and its output image in one map (`op.input.img`, `op.output.img`). |
| `AbstractOp` | Base of every operation: the parameter map, enabled state and name, and the tracking of parameter changes (the revision). |
| `SimpleOpManager` | The ordered chain. `process()` wires the output of each node into the input of the next and returns the output of the last node. |
| `RenderedImageLayer` | Owns the display chain of a view, sets its source with `setFirstNode` and triggers a pass with `updateDisplayOperations()`; keeps the Java2D copy of the display image for painting. |

The DICOM view orders its chain as Window/Presets → Filter → PseudoColor → Shutter → Overlay →
Redaction → Fusion → AffineTransform. Geometry comes last, so panning, zooming and rotating touch
only the last node.

## A pass

A pass is triggered by any change of what the view shows: window/level, color map, filter, zoom,
pan, rotation, presentation state, fusion, redaction, or a new image. Callers do not need to know
which node is affected: they set parameters on the nodes, then ask for a pass.

During a pass, a node either **runs** or is **skipped**. A skipped node keeps the output of its last
run. A disabled node passes its input through. A node whose `process()` throws passes its input
through and runs again on the next pass.

`SimpleOpManager.isLastPassReused()` tells whether the last pass ran no node at all; the layer then
keeps its paint buffer.

## What makes a node run

A node runs when one of these changed since its last successful run:

- **its input image**, compared by identity: operations never write into their input, so the same
  object means the same pixels;
- **its revision** (`ImageOpNode.getRevision()`), which moves when a parameter that can change the
  output changes, or when the operation calls `invalidate()`.

A parameter write counts as a change unless the old and the new value are equal **immutable**
values (boxed numbers, strings, enums, colors, records, `List.of` lists of such values). Setting
the same mutable object again is a change: it is how a caller says the object was modified.

A node that passes its input through returns the same object, so the nodes below it see an
unchanged input and are skipped as well.

Some operations depend on more than their parameters and check it themselves: the overlay color
preference (`OverlayOp`), the identity mask in force (`RedactionOp`), a PET overlay still loading
(`FusionOp`, which runs on every pass until the overlay is complete).

The setting `weasis.display.op.tracking` turns skipping off: every node then runs on every pass.

## Images and their release

The chain keeps the input and output of every node between passes; that is what a skipped node
reuses. When a node runs again, its previous output may be released at once instead of waiting for
the garbage collector, if:

- the operation declares that it owns its output (`AbstractOp.ownsOutput()`: the output is either
  its input or a new image that no cache holds);
- the image is not its own input, not referenced anymore by any node of the chain, not the source,
  and not the image returned by the previous pass;
- it is not the output of the last node: that image belongs to the layer and to whoever read it.

The setting `weasis.display.op.release` turns this off. See [Memory Management](Memory-Management.md)
for the native memory budget these images count against.

## Invariants

- An operation never modifies its input image.
- An operation that reads something outside its parameters makes the change visible through its
  revision (`invalidate()`, or a `getRevision()` override); otherwise it keeps showing the old
  result.
- Code that modifies an image **in place** (the source, or an input of a node) calls `invalidate()`
  on the node that reads it; asking for a pass is not enough, because the input object is the same.
- A copy of a chain (`SimpleOpManager.copy()`, used by exports and lens views) starts with no image
  and reuses nothing.
- A chain is used by one thread at a time.

## Extension points

A plugin adds an operation by extending `AbstractOp` and inserting it in the chain of a view.

- Read parameters with `getParam`, write them with `setParam`, or through the `params` map: both
  are tracked.
- Return either the input image or a new image from `process()`, and store it with
  `params.put(Param.OUTPUT_IMG, …)`.
- Call `invalidate()` when an input the operation reads outside its parameters changes (a
  preference, a cache, an external document), or return -1 from `getRevision()` to run on every
  pass.
- Override `ownsOutput()` to return true only if the output is never an image the operation or
  someone else keeps.

An operation that implements `ImageOpNode` directly, without `AbstractOp`, has no revision and runs
on every pass.
