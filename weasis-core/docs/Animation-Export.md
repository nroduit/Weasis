# Animation Export

Animation export writes a sequence of rendered frames to one file: an animated image (**APNG** or
**GIF**) or a **DICOM multi-frame Secondary Capture**. It serves three kinds of animation:

- **3D rotation** — a turn of the volume-rendered view.
- **Cine loop** — the frames of a multi-frame series played out, as displayed.
- **Recording** — what happens in a 2D or MPR layout while the user works.

Unlike `ScreenshotDialog`, which produces a single image, every frame goes through the same display
pipeline, overlays and identity masking as the view on screen.

## Table of Contents

- [Concepts](#concepts)
- [Data flow](#data-flow)
- [Capture scope](#capture-scope)
- [Output formats](#output-formats)
- [Module layering](#module-layering)
- [Invariants](#invariants)
- [Extension points](#extension-points)

## Concepts

Two independent axes: what advances the animation, and what consumes the frames. Any source
composes with any output.

| Abstraction | Responsibility |
|---|---|
| `FrameSourceBuilder` | One kind of animation in the dialog: its own rows in the `AnimationForm`, its frame count and default file name, and how the export runs (`run`). |
| `PullSourceBuilder` | The common case: builds a `FrameSource` and hands it to `AnimationExportTask`. |
| `FrameSource` | A deterministic animation: frame count, show frame *i*, report how far its rendering has come, capture it, duration of frame *i*. |
| `FrameSink` | Consumes frames and produces one file (`open` / `write` / `close`, `abort` on cancel or failure). |
| `FrameSinkFactory` | One output format in the dialog: title, extension, one-line advice, whether it buffers raw frames, and the sink for a file and a masking profile. |
| `FrameGrabber` | Turns a `CaptureScope` into pixels, with masking, downscale, an optional pointer overlay and a frame size fixed for the whole export; frames land in reused buffers. |
| `AnimationExportTask` | Drives a `FrameSource` into a `FrameSink` with progress and cancellation. |
| `AnimationExportDialog` | Common parameters (scope, downscale, image area only for a 2D view, rate, format, masking profile, destination) plus the builder's own options. |

Concrete sources:

| Source | Classes | Module |
|---|---|---|
| 3D rotation | `RotationFrameSource`, `RotationSourceBuilder` | `weasis-dicom-3d/viewer3d` |
| Cine loop | `CineFrameSource`, `CineSourceBuilder` | `weasis-core` |
| Recording | `AnimationRecorder`, `FrameDeduplicator`, `RecorderSourceBuilder`, `RecordingState` | `weasis-core` |

Rotation and cine are **pull-based**: the task asks for frame *i*, the source sets the state, and
the frame is captured once its rendering is complete. The recorder is **push-based**: it samples the scope on a timer while the
user works and writes to the sink directly, so it is not a `FrameSource`; its builder implements
`run` itself.

## Data flow

```
AnimationExportDialog ──▶ FrameSourceBuilder.run(grabber, rate, sink)
                              │
             ┌────────────────┴────────────────┐
     PullSourceBuilder                  RecorderSourceBuilder
             │                                  │
   AnimationExportTask                  AnimationRecorder (timer)
   frame i: FrameSource (EDT)                   │
             └────────────▶ FrameGrabber ◀──────┘
                                  │  BufferedImage + duration
                                  ▼
                              FrameSink ──▶ file
```

Frames are rendered on the EDT and encoded off it, so the UI and the progress dialog stay live.
Between showing a frame and capturing it, the task polls the source's render progress from the
worker thread and leaves the EDT free, so a rendering that completes over several repaints can
finish first. The recorder likewise grabs on the EDT and hands each sample to its own encoder
thread, which merges a sample identical to the previous frame into that frame's duration; when
the encoder falls behind, a sample is skipped and the frame on screen lasts longer.
Durations are carried per frame: a recording's real sampling intervals survive into the output.

## Capture scope

`CaptureScope` sets the area each frame covers:

| Scope | Covers |
|---|---|
| `VIEW` | the selected view |
| `CONTAINER` | every view of the current layout, in its arrangement |
| `APPLICATION_WINDOW` | the whole application window, including toolbars, explorer and tool panels |

A recording follows the selection: the view and layout scopes capture whichever view and viewer
tab are selected at each sample (read from `UICore.getSelectedPlugin()`), fitted into the frame
size fixed at the start. A pull-based export stays on the view it was started from.

A single 2D view is re-rendered through `ViewTransferHandler.createComponentImage`, the path
`ScreenshotDialog` uses, optionally cut to the image area so the canvas around the image is left
out. What that re-rendered view shows is a `DisplayProfile`, the same choices as the Display tool
(image with its DICOM overlay, shutter and pixel padding, annotation items, drawings,
cross-lines), chosen with `DisplayProfileSelector`: as displayed, the image alone, or a custom
profile pre-filled from the view. It is applied to the off-screen `ExportImage` only, never to the
live view; the screenshot dialog uses the same selector. A view that cannot be re-rendered, the 3D
view, implements `LiveCaptureView`: it offers the items of its own Display tool, and applies the
profile for the time of the one synchronous paint of a capture (through
`AbstractInfoLayer.overrideForCapture`), restoring the display before anything repaints. Every
other case paints the live component, which is the only way to
include OpenGL content (the 3D view) and the window chrome. A 3D view is never re-rendered as a 2D
image.

Painting the window rather than reading the screen means captures are unaffected by other
windows on top and need no screen-capture permission. It also means menus and tooltips shown as
separate native windows are not captured.

## Output formats

| Format | Sink | Character |
|---|---|---|
| APNG | `AnimatedImageSink` | lossless, true colour, stores only what changes between frames |
| GIF | `AnimatedImageSink` | 256-colour palette, widely accepted (journals, presentations) |
| DICOM SC | `DicomScFrameSink` | Multi-frame True Color Secondary Capture in the source study; plays as a cine |

`AnimationFormat` lists the two animated-image formats. Each source preselects the format that
suits its content: colour and mostly static content favour APNG, grayscale cine favours GIF.

The animated-image encoder needs every frame at once, so `AnimatedImageSink` holds the frames and
caps both their count and their resident bytes; the dialog shows the projected footprint, which
depends mostly on the capture scope. `DicomScFrameSink` streams each frame to disk and has no such
limit, which makes it the output for long sequences. The DICOM file takes the patient and study
of the source series in a new series, with Cine module timing, the equipment and derivation
attributes of a Secondary Capture, and the lossy-compression indication of its frame codec.

## Module layering

The whole pipeline lives in `weasis-core` (`org.weasis.core.ui.editor.image.export`) and knows
nothing about DICOM. Viewers add to it:

- `weasis-dicom-codec` (`org.weasis.dicom.codec.export`) provides `DicomScFrameSink` and
  `DicomScSinkFactory`, which assembles the format list a DICOM viewer offers.
- `weasis-dicom-3d/viewer3d` (`org.weasis.dicom.viewer3d.export`) provides the rotation source,
  reached from the 3D view's export actions.
- `View2d.getExportActions()` offers the screenshot, the cine export for a multi-frame series and
  the recorder; `View3d.getExportActions()` a screenshot of the view as displayed (`ScreenshotDialog`
  without the original-image option), the rotation export and the recorder. Each is built
  through `CaptureAction` (screenshot, animation, record), which gives it its icon, its kind and
  the accelerator of its configurable shortcut. The containers surface them through their
  delegation, the `CaptureButton` in the menu bar lists the selected viewer's export actions, and
  the common viewer shortcuts run the action of their kind on the selected view, so every capture
  starts from one place whatever the viewer type.

Formats reach the dialog as the list of `FrameSinkFactory` its caller passes in: no registry, no
service lookup.

## Invariants

- **Cancellation restores state.** A source puts back what it changed (camera, series frame) and
  the sink deletes its partial output.
- **The rendering is not altered for export.** A rotation changes only the camera; rendering
  quality stays as the user set it, so exported frames look like the display.
- **Frames are captured complete.** A 3D rotation starts only once the volume and its segmentation
  have finished loading, and with path tracing each frame waits for its progressive average to
  reach the number of samples chosen in the dialog. The on-screen convergence message is kept out
  of the frames.
- **Frame size is fixed once** per export, rounded to even dimensions.
- **Masking follows the capture, header included.** The selected masking profile applies to every
  frame; the application-window scope additionally turns session masking on for the duration of
  the capture, because tab titles and explorer labels are not resolved per paint. The DICOM sink
  builds the identity it inherits from the source under the same profile, so the file never says
  more than its pixels, and states whether identity remains burned in. Burned-in pixel annotations
  are not masked. See [Identity-Masking.md](Identity-Masking.md).
- **One recording at a time, visibly.** `RecordingState` holds the recording in progress; the
  `CaptureButton` in the menu bar turns into a badge that shows its elapsed time against the
  maximum duration and stops it, the same way the masking indicator shows session masking. The
  recording ends at that duration; the frame cap is only a memory bound, since merged identical
  samples mean an idle screen writes almost no frames. A second recording is
  refused while one runs, and the end of a recording is reported with its frame count and
  duration.
- **Memory.** Buffered frames are released in one place on success and on failure; see
  [Memory-Management.md](Memory-Management.md).

## Extension points

- A new kind of animation: implement `FrameSourceBuilder`, usually as a `PullSourceBuilder` over a
  new `FrameSource`, and add an export action to the viewer.
- A new output format: implement `FrameSink` and its `FrameSinkFactory`, and add the factory to
  the list the viewer passes to the dialog.
