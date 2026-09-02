# Identity Masking

Identity masking hides patient identity in what Weasis **renders**: the application window and
every image produced from it, such as screenshots, prints and image exports. It serves demos,
teaching and screen sharing with real studies.

It is a display-time substitution, **not a de-identification**. Stored data never changes, and
DICOM files exported or sent keep their original attributes. Conformant de-identification of
DICOM data is the job of [Karnak](https://karnak.weasis.org/).

## Table of Contents

- [Concepts](#concepts)
- [Resolution](#resolution)
- [Session and scoped masks](#session-and-scoped-masks)
- [Configuration](#configuration)
- [Surfaces](#surfaces)
- [Burned-in pixels](#burned-in-pixels)
- [Invariants](#invariants)
- [Extension points](#extension-points)

## Concepts

```
TagW ──category──▶ TagCategory ──profile──▶ AnonymizationAction ──mask──▶ displayed value
```

| Abstraction | Responsibility |
|---|---|
| `TagCategory` | What kind of information a tag carries: direct identifier, patient characteristic, date, birth date, institution, device, descriptor, free text, other. Held by each `TagW` (`getCategory()`). |
| `AnonymizationAction` | What happens to a value, named after the DICOM PS3.15 Annex E action codes: keep, pseudonymize, clear, remove, shift. |
| `MaskingProfile` | One use case (display, teaching, publication, AI request): an action per category, optional per-tag overrides, and whether dates are shifted. |
| `IdentityMask` | A profile plus a pseudonymizer and a date offset, applied to values while it is in force. |
| `MaskingModel` / `MaskingModelRegistry` | The JSON documents that define the classification and the profiles, and their merged result. |

Classification and decision are separate on purpose: a tag is classified once, and every profile
handles it. Adding a tag to a category changes the behavior of all profiles at once.

## Resolution

Values are masked where they are turned into text, not where they are stored.

- `TagView` is the single resolution point for everything built from display tags: viewer
  annotations, explorer labels (through `MediaSeriesGroupNode`), the attribute summary. It walks its
  candidate tags in order and returns the first non-empty rendering. A *remove* action skips the tag
  and falls through to the next candidate; a *clear* action stops with an empty value.
- Code that renders a raw value outside a `TagView` (a tooltip, the attribute dump, a report or ECG
  header) passes it through `IdentityMask.maskValue` or `IdentityMask.maskText`.
- Pseudonyms are deterministic within a run: a salted hash of the value, so the same patient gets
  the same pseudonym in every view, tab and export, and a new run gives new pseudonyms. A person
  name keeps the PN structure (`ANONYMOUS^…`).
- Profiles that shift dates move them by one offset for the whole run, so intervals between studies
  are preserved.

## Session and scoped masks

A mask is in force in one of two ways, and `IdentityMask.active()` returns whichever applies.

| Kind | Set by | Lifetime | Used for |
|---|---|---|---|
| **Scoped** | `runMasked` / `callMasked` | The dynamic extent of the call, on the calling thread only (a `ScopedValue`) | Rendering an output: screenshot, clipboard copy, print, image export |
| **Session** | `IdentityMask.setSessionMasking` | Until switched off | Masking the whole application window for a demo |

- A scoped mask always wins over the session mask, so an output can use a stricter profile than the
  screen, and the screen never changes while an output is rendered.
- A scoped binding unwinds with the stack: an exception during a capture cannot leave masking stuck
  on, and nothing has to be restored.
- The session profile is the one the user chose when turning masking on (`IdentityMask.sessionProfile()`),
  or the configured default. Listeners registered with `IdentityMask.addChangeListener` are notified
  when session masking or its profile changes.

## Configuration

The model is data, merged by `MaskingModelRegistry` from several JSON documents, a later one
overriding an earlier one for the same tag or profile id:

1. the bundled `identityMasking.json` of weasis-core (profiles, Weasis-internal tags);
2. documents contributed by other bundles, such as the DICOM tag classification of the DICOM codec
   (`identityMasking-dicom.json`, applied by `DicomMediaUtils.classifyTags`);
3. a site document given by the launcher preference `weasis.masking.config` (path or URL);
4. a user document `identityMasking.json` in the preference directory, ignored when the site
   document is locked.

A tag is named by DICOM keyword, by hexadecimal tag (with its private creator for a private tag), or
as `weasis:<keyword>` for an internal tag. A profile that keeps direct identifiers, or a reference
to a profile that does not exist, is refused and the earlier definition stays, so a configuration
mistake cannot switch masking off. The format is described in the Javadoc of `MaskingModel`.

## Surfaces

Most surfaces repaint from the model and follow the mask with no extra work. Surfaces that cache a
string (a docking tab title, a titled border, a rendered report) register a change listener and
rebuild it; `ViewerPlugin.setPluginName(Supplier)` does this for plugin titles.

- The session is switched from *Tools > Anonymize displayed data*, which asks for the profile.
  While it is on, `MaskingIndicator` shows a badge in the menu bar and the window title carries a
  suffix.
- Dialogs that produce an output offer `MaskingProfileSelector`, which binds the chosen profile
  around the rendering only. Printing and image export run the rendering on the thread that binds
  the mask.

## Burned-in pixels

Text printed into the pixels by the modality is out of reach of tag masking.

- `PixelPrivacy` gives a verdict from Burned In Annotation (0028,0301) and Recognizable Visual
  Features (0028,0302). Both are Type 3 and often absent, so silence is *unknown*, never *clean*.
- `Redaction` stores the regions to hide on an image or a series (`TagW.RedactionMask`); an image
  mask overrides the series mask, and a series mask is never copied onto frames. Regions are closed
  shapes in image coordinates, drawn by the user and applied with `RedactionToolBar`.
- `RedactionRenderer` fills each shape as drawn with the median color of the pixels just around it,
  on any depth and channel count. `RedactionOp` applies it in the 2D view pipeline before fusion,
  zoom and rotation, so screenshots, prints and exports copied from the view carry the same
  regions; explorer thumbnails and image exports add the same operation.
- Regions live in memory for the session and are never written to the file.

## Invariants

- Masking never modifies stored data or DICOM attributes.
- The live screen changes only through the session mask; outputs are masked only through a scoped
  binding on the thread that renders them.
- Every profile hides direct identifiers.
- One run uses one salt and one date offset, shared by all profiles, so pseudonyms and shifted dates
  agree across the screen and every output.

## Extension points

- **New identifying tags**: classify them in a masking document (site or user) rather than in code.
- **Plugins rendering patient data**: resolve text through `TagView`, or pass raw values through
  `IdentityMask.maskValue`; cache derived labels only with a change listener.
- **New outputs** (an exporter, an outbound request): bind a mask with `IdentityMask.forProfile(…)`
  and `runMasked` / `callMasked` around the code that renders, on the thread that renders.
