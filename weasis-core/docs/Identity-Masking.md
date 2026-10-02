# Identity Masking

Identity masking hides patient identity in what Weasis **renders**: the application window and
every output produced from it. It serves demos, teaching and screen sharing with real studies.

What it covers, and what it does not:

| Output | Masked |
|---|---|
| The application window: viewer annotations, tab titles, explorer labels and tooltips, attribute panel, report and ECG headers | Yes, while session masking is on |
| Rendered outputs: screenshot, clipboard copy, print | Yes, with the profile chosen in the dialog |
| Image export (non-DICOM formats), including the folder names built from patient, study and series labels | Yes, with the profile chosen in the dialog |
| DICOM export and DICOM send | No, by design: the attributes and the pixels are written as they are |

It is a display-time substitution, **not a de-identification**. Stored data never changes.
Conformant de-identification of DICOM data is a different job, with its own rules (PS3.15
profiles, UID mapping, consistency across a study) and belongs to
[Karnak](https://karnak.weasis.org/).

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

The model is data, merged by `MaskingModelRegistry` from several JSON documents by the rules of
`LayeredEntries`, a later one overriding an earlier one for the same tag, profile or mask id:

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

The user document is also what the preference page writes: *Preferences > General > Identity
masking* edits the profiles (an action per category, whether the profile is offered when producing
an image, whether it shifts dates) and the classification (a tag, a category), and chooses which
profile session masking and remote requests use. Built-in, plugin and site entries are shown with
their origin and are read-only; a built-in profile is changed by cloning it under a new id, which
the merge then overrides by id. Changes are written to the user document on *Apply*, and the
registry reloads, so every cached label follows without reopening anything. A locked site document
leaves the page read-only. The whole user document can be exported and imported as JSON, which is
the escape hatch for what the page does not express, such as a per-tag action inside a profile.

## Surfaces

Most surfaces repaint from the model and follow the mask with no extra work. Surfaces that cache a
string (a docking tab title, a titled border, a rendered report) register a change listener and
rebuild it; `ViewerPlugin.setPluginName(Supplier)` does this for plugin titles.

- The session is switched from *Tools > Anonymize displayed data*, which asks for the profile.
  While it is on, `MaskingIndicator` shows a badge in the menu bar and the window title carries a
  suffix.
- Dialogs that produce an output offer `MaskingProfileSelector`, which binds the chosen profile
  around the rendering only. Printing and image export run the rendering on the thread that binds
  the mask, so the labels the export turns into folder names follow the same mask as the pixels.
  The selector appears only on the outputs Weasis renders; the DICOM formats of the export dialog
  do not offer it.

## Burned-in pixels

Text printed into the pixels by the modality is out of reach of tag masking.

- `PixelPrivacy` gives a verdict from Burned In Annotation (0028,0301) and Recognizable Visual
  Features (0028,0302). Both are Type 3 and often absent, so silence is *unknown*, never *clean*.
- `Redaction` stores the regions to hide on an image or a series (`TagW.RedactionMask`); an image
  mask overrides the series mask, and a series mask is never copied onto frames. Regions are closed
  shapes in image coordinates, drawn by the user and applied with `RedactionToolBar`. An empty mask
  hides nothing and keeps the device library (below) from proposing anything.
- The two scopes stay independent, which decides what a change does to the other one. Clearing a
  scope drops its regions, and writes an empty mask only when something below would show again at
  once — the series mask under an image, an entry of the library; an empty mask left behind would
  otherwise outrank every series mask stored afterwards. Applying regions to a series frees its
  frames of what the series then answers for: their empty masks, which exist only to hide what was
  proposed below them, and a copy of the same device entry applied to one frame beforehand, which
  would otherwise survive a removal made on the series. Regions drawn on a frame are an edit: they
  are left alone and keep winning.
- `RedactionRenderer` fills each shape as drawn with the median color of the pixels just around it,
  on any depth and channel count. `RedactionOp` applies it in the 2D view pipeline before fusion,
  zoom and rotation, so screenshots, prints and exports copied from the view carry the same
  regions; explorer thumbnails and image exports add the same operation. The operation resolves the
  regions again at each pass, so an output rendered under a stricter profile hides what that
  profile hides.
- Regions drawn on an image or a series live in memory for the session and are never written to the
  file.

### The device library

Identity printed by a machine is in the same place on every image it produces, so regions can be
kept per device instead of being redrawn:

- A `PixelMask` of the masking document names the device it applies to (modality, station,
  manufacturer, model, institution, matched after case folding, with a trailing `*` as a prefix),
  the frame size its regions were drawn on, and the regions themselves, normalized to that frame.
- `PixelMaskMatcher` picks the entry for a series: the most specific key wins, the user document
  before the site one, and an image whose aspect ratio differs from the reference is refused rather
  than stretched — a banner sits in a band of the layout, not at a fixed offset.
- Each region carries a `TagCategory`, so the profile in force decides: a region is burned only
  when its category is not kept. With no mask in force, nothing is burned and normal reading shows
  the pixels as they are. An entry may also restrict itself to named profiles.
- What a view hides is announced in the redaction toolbar with the name of the entry. Editing the
  regions copies them onto the image or the series, leaving the entry alone; clearing them
  suppresses the entry for the session. *Save for this device* turns what is displayed into a new
  entry of the user document, and *Device masks* in the preferences lists the entries, says which
  one the open series uses and why the others do not.
- The same toolbar also picks an entry rather than waiting for it to be proposed: its menu offers
  the entries whose key and reference shape accept the image in view, marks the one in force, and
  burns every region of the one chosen, whatever the mask in force keeps — the choice is the user's,
  where the categories only govern what the library proposes on its own. The regions are then
  stored on the image or the series like drawn ones, in the scope of the toolbar, and taken back
  with the same Clear.
- `PixelPrivacy` drives one more thing: the dialogs that produce an image warn when the pixels may
  carry identity and nothing is burned over them. The warning never blocks.
- Entries convert to and from the `masks:` block of a Karnak profile. The conversion is lossy in
  both directions and says so: Karnak matches on the station name only, takes rectangles, and
  **overwrites** the pixels of the instances it forwards, where Weasis only hides them on screen and
  in what it renders.

## Invariants

- Masking never modifies stored data or DICOM attributes: it acts between the model and what is
  drawn or written as an image.
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
