# MigLayoutModel Best Practices Guide

Conventions for describing view grids with `MigLayoutModel` and `ConstraintSpec`
(`org.weasis.core.api.gui.layout`): weights, grow/shrink priorities and size bounds.

## Core concepts

A `MigLayoutModel` is the layout of a viewer or editor plugin: a MigLayout grid made of

- a **layout constraint** string (e.g. `wrap 2, ins 0, gap 5`),
- a **column constraint** and a **row constraint** string, one `[...]` group per column or row,
- a list of `MigCell` (component type, position, span, cell constraints).

It is built from a row and column count (`new MigLayoutModel(id, title, rows, cols, defaultClass)`,
optionally with merged cells), from explicit strings, or from a `.properties` definition such as
`weasis-dicom/weasis-dicom-viewer2d/src/main/resources/config/layoutModel.properties`:

| Key | Content |
|---|---|
| `layout.name`, `layout.icon` | Display name and icon |
| `layout.constraints` | MigLayout layout constraints |
| `layout.columns`, `layout.rows` | Column and row constraint strings |
| `cell.<n>.type` | Component class |
| `cell.<n>.x`, `.y`, `.spanX`, `.spanY` | Position and span |
| `cell.<n>.constraints` | Cell constraints (default `grow`) |

Default grids use `[grow,fill]` for every column and row.

## Weight system

Each column or row is described by a `ConstraintSpec`, a record of
`(growWeight, shrinkWeight, fill, minSize, maxSize)`. `toConstraintString()` renders it as
`[grow g,shrink s,fill,min::max]`; `parse` / `parseAll` read constraint strings back.

- **Grow weight**: share of the extra space when the container expands. `0` = does not grow.
- **Shrink weight**: share of the lost space when the container shrinks. `0` = does not shrink
  below its size. Independent from the grow weight.
- **Fill**: whether the component fills its cell. Containers and viewers fill.
- **Size bounds**: minimum and maximum in pixels, `null` for none. There is no preferred size.

A weight given without a value (`grow`, `shrink`) is read as 100.

### Weights are percentage shares

When the user drags a divider between cells, `GridMouseHandler` derives each column's or row's
grow weight from its actual size as a percentage of the grid, and writes the specs back with
`setColumnConstraintSpecs` / `setRowConstraintSpecs`. `ConstraintSpec.parseAll` likewise
defaults missing specs to `100 / count`. Author grow weights on the same scale, as shares that sum
to 100 across a column or row set (e.g. `[grow 25][grow 50][grow 25]`), so an authored layout and
a resized one mean the same thing.

## Constraint specifications

| Factory | Result |
|---|---|
| `ConstraintSpec.fixed(size)` | grow 0, shrink 0, min = max = `size` |
| `ConstraintSpec.withBounds(min, max)` | grow 100, shrink 100, fill, bounded |
| `new ConstraintSpec(grow)` | given grow, shrink 100, fill |
| `new ConstraintSpec(grow, shrink)` | given grow and shrink, fill |
| `withGrowWeight`, `withShrinkWeight`, `withFill`, `withSizeBounds` | Copy with one field changed |

`setColumnConstraintSpecs` and `setRowConstraintSpecs` require exactly one spec per column or row
of `getGridSize()`; an array of another length is ignored. `applyConstraintsToLayout(panel)`
updates a live MigLayout panel without rebuilding it; `applyLayout(panel, provider)` rebuilds it.

```java
MigLayoutModel model =
    new MigLayoutModel("sidebar", "Sidebar", 1, 3, DefaultView2d.class.getName());
model.setColumnConstraintSpecs(
    new ConstraintSpec[] {
      ConstraintSpec.fixed(200),          // fixed sidebar
      new ConstraintSpec(100, 100),       // flexible content
      ConstraintSpec.withBounds(150, 300) // bounded panel
    });
```

## Best practices summary

Do:

- Use simple proportional weights and document non-obvious ratios in a comment.
- Use fixed sizes for toolbars, headers and sidebars (`ConstraintSpec.fixed`).
- Set the shrink weight to 0, or a minimum size, for content that must stay usable.
- Prefer the factory methods over the canonical constructor for readability.
- Test resizing in both directions, including very small and very large windows.

Don't:

- Use arbitrary large weights.
- Set every shrink weight to 0: the layout becomes rigid.
- Overuse maximum sizes, or mix weights and fixed sizes without testing.
- Rely on a preferred size: `ConstraintSpec` carries only minimum and maximum.
