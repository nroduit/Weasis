# Hanging protocols

How a study is hung by a protocol instead of opened on its first series: the protocol model, the
series features it matches on, the engine that computes a plan, the applier that hangs it on live
viewers, the reading sessions that restore a display, and the exchange with DICOM. The user side
(the protocol bar, *Save as protocol*, the *why* panel, the settings) is described on the user
site; the command surface is `hp:` and `state:` (`weasis-cli help hp`).

## Where the pieces live

| Module | Package | Holds |
|---|---|---|
| `weasis-dicom-tools` (external) | `org.weasis.dicom.hp` | the DICOM Hanging Protocol model: `HangingProtocol`, image sets, display sets, selectors, comparators, definitions |
| `weasis-dicom-codec` | `org.weasis.dicom.codec.hp` | the engine and everything headless: features, feature selectors, the plan, the deriver, validation, the JSON form, the built-in library, DICOM exchange |
| `weasis-core` | `org.weasis.core.api.hp`, `org.weasis.core.ui.editor.image` | the layered documents of protocols and sessions; `HangingLayout` and `ViewState`, the typed display a viewer captures and restores |
| `weasis-dicom-explorer` | `org.weasis.dicom.explorer.hp` | the applier on live viewers, the active hanging, the opening path, sessions, corrections, the `hp:` and `state:` verbs |
| `weasis-dicom-viewer2d` | `org.weasis.dicom.viewer2d` | the protocol bar, the cell indicator, the *why* tool, *Save as protocol* |

## The protocol

A protocol is a DICOM Hanging Protocol (PS3.3 A.36) plus a Weasis extensions block. The DICOM
part says what to select (image sets: selectors on attributes), how to lay it out (display sets
per presentation group, a group being one reading step) and in which order (sorting operations,
scrolling and navigation groups). `WeasisExtensions` says what the IOD cannot: display state by
reference to the registries (preset, color map, measurement and masking profile ids), feature
selectors on the normalized series features, the viewer type of a display set (2D, MPR, 3D) and
its non-image cells, the placement of the viewers in the workspace, the loading strategy and the
UI profile of the protocol or of each step. Members a version does not know are kept and written
back unchanged.

A display set may derive its series instead of showing an acquired one: the Weasis extension
`derive` names a kind (`subtraction`, `mpr`), the image set the derivation starts from (its own by
default), the image set of a mask and the plane of a reformat; the engine plans the cell with the
source series and a `Derivation`, and `HangingPlanApplier` prepares the step off the event thread
(and asks the `VolumePrefetcher` extensions to build, in the background, the volumes the MPR,
volume-rendering and reformat cells of the other steps will need)
by asking the `DerivedSeriesProvider` of that kind to build the series before it is hung. A mask
that does not resolve leaves the acquired series in the cell, with the reason. With `fallback` the
derivation happens only when the display set's own image set resolves nothing, an acquired series
being preferred to a computed one: the built-in *CT* protocol fills its coronal and sagittal
cells with reformats of the axial soft-tissue series when the study has none (the cell indicator
shows *fallback*). A display set may also project its series through a slab instead of
showing its images: a `SLAB` Reformatting Operation Type with its Reformatting Thickness, or the
Weasis extension `projection` (mode `MIP`, `MINIP` or `MEAN`), gives the cell a
`ViewState.Projection` the 2D viewer realizes as a MIP pane. The built-in *MR breast* protocol
hangs the pre-contrast and post-contrast T1, their subtraction and a MIP of the subtraction this
way.

`ProtocolJson` is the working form: one entry `{id, schema, dicom, weasis}` where `dicom` is the
dataset by keyword (`DicomJson`). The entries are a layered document, `hp.protocols.json`
(`HangingProtocolDocuments`): the built-in library shipped by the codec (`BuiltInProtocols`, one
protocol per imaging type), the site folder `config/hanging-protocols/` (one protocol per file)
and the user document. A protocol of a higher layer overrides the one of the same id below it.

`ProtocolExchange` writes a protocol as a Hanging Protocol Storage object and reads one back; the
Weasis extensions travel in a private block the reader restores, an object without it imports with
its DICOM selectors alone.

## Series features and selectors

The engine does not match on raw attributes only. `SeriesFeatureExtractor` derives once per series
a `SeriesFeatures` value: modality, role (localizer, image, derived, secondary capture...), plane,
coded anatomy, laterality, view, MR weighting, CT kernel family, slice thickness, temporal kind,
contrast, and so on, read from the tags by the rules of PS3.3; nothing is guessed from a missing
attribute and every vendor reading is marked where it happens. A `FeatureSelector` matches on
these features with three usages: `MATCH` and `NO_MATCH` are hard filters like the DICOM
selectors, `PREFER` is a weighted preference that ranks the candidates instead of excluding them,
so the engine proposes the best series when several pass. `DerivationTables` hold the vocabulary
the extractor maps descriptions and codes to.

## The engine and the plan

`HangingEngine` matches the enabled protocols of every layer against the series of a study, in the
three steps of the standard: protocol selection (a definition applies to a series of the study;
the protocol is scored by the specificity of its definition, the share of its image sets that
resolve and its level), image set resolution (filters, then preferences) and display set filling.
Time-based image sets bind to the priors of the patient (`PriorStudy`, ranked from the most
recent). The result is a `HangingPlan`: per reading step a `HangingLayout` (the grid, its cells,
the synchronization) and, per cell, the series chosen, how it matched (exact, preferred,
fallback, shared, nothing), why, and the alternatives. The plan is a pure value computed before
any view exists: `hp:match` returns it, the applier hangs it, the *why* panel explains it and the
tests assert on it. `DisplayOrientation` turns a Display Set Patient Orientation into the rotation
and flip of a cell.

`ValidationReport` runs the matching over a folder of studies (`hp:validate --corpus`), the
regression test of a library, and `ProtocolLint` checks a protocol file without any study.

## Hanging on live viewers

`HangingPlanApplier` hangs one step headlessly: the 2D cells of the step become one 2D container
that takes the step's layout, a display set whose viewer is MPR or 3D opens its own container; a
container of the same patient is reused when the placement says so. The plan stays attached to
the viewers it produced (`ActiveHanging`), which is what the protocol bar, `hp:sets`, `hp:group`
and the corrections read: another step, another candidate protocol, the same step again, each
re-hung in place. `HangingOpener` is the opening path: the series of a patient are collected while
they arrive and, once they stop for a quiet period, the best protocol is matched on the most
recent study and its first step hung; a series arriving later fills a cell left empty, never
replaces one shown. Hanging publishes the view context, so the tools, toolbars and UI profile of
the workspace follow it (see `weasis-core/docs/Workspace-Docking.md`).

A user who puts another series in a hung cell leaves a `Corrections` entry: what the protocol
chose, what the user chose, the features of both and the rule change that would have produced the
user's choice; the anonymous statistics of the log show which rules fail most.

## Save as protocol

`ProtocolDeriver` goes the other way: from the display of a study (a `HangingLayout` whose cells
show series of the study) to a protocol that would produce it again on similar studies. Per cell
it keeps the smallest set of feature selectors that singles the series out among the study, the
most specific stable features first and the description tokens last, and the presentation intent
of the cell (preset, color map, LUT shape, zoom mode, inversion, sort and direction, tiling); the
grid becomes display sets and the synchronization a scrolling group. The derivation is reported
per cell so the review can show the rule and which series match it now.

## Sessions

A `Session` is a reading state kept as it is: the workspace snapshot (windows, split tree, views
with their data and view parts, the shell), the studies it shows with the archive each came from,
the protocol that hung it with the bindings of its cells, a label and a pinned flag. It carries
study and series identifiers, never a name, a token or a URL with credentials, and lives in the
private user document `hp.sessions.json` (`SessionStore`), with a retention by number and by age.
Restoring loads nothing by itself: the studies must be loaded, then the workspace is rebuilt from
the snapshot. A series a plugin derived from loaded ones (a subtraction) is kept as its spec under
`derived`, and the plugin, a `DerivedSeriesProvider` extension, builds it again under the same
identifiers before the workspace is rebuilt. `SessionDicomExport` writes a session for a PACS or another viewer as a Basic
Structured Display object whose image boxes are the panes of the main window, each with a
Grayscale Softcopy Presentation State carrying the pane's window, rotation, flip, inversion,
magnification and graphics; what the IOD cannot carry is reported.

## Invariants

- The plan never depends on a view: everything up to `HangingPlan` is headless and the same code
  answers the verbs, the UI and the tests.
- A protocol of the built-in or site layer is never modified; a change goes to the user layer under
  the same id.
- A series the user put in a cell is never replaced by the engine.
- Patient data (sessions, corrections) stays in private user documents, outside settings packages
  and mirrors.
