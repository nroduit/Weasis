# Weasis

Weasis is a desktop DICOM viewer. `weasis-cli` sends one command to the running instance and prints
the result as JSON. You look at images by capturing them to a file and reading that file; you get
numbers (lengths, angles, areas, pixel statistics, attributes) from Weasis itself, never by
estimating them from a picture.

## Setup

Weasis must already be running on this machine. **Call `weasis-cli` plainly**: the installers and
the development wrapper put it on the `PATH`, so there is nothing to look for. Only when the shell
answers that the command is unknown, try the path of the platform:

| Platform | Path |
|---|---|
| Linux | `/opt/weasis/bin/weasis-cli` |
| macOS | `/Applications/Weasis.app/Contents/MacOS/weasis-cli` |
| Windows | `C:\Program Files\Weasis\weasis-cli.exe` |
| Development | `java -cp weasis-launcher.jar org.weasis.launcher.CommandClient` |

This instance runs Weasis @{applicationVersion} with command API @{apiVersion} and protocol
@{protocolVersion}, and answers these namespaces: @{namespaces}.

Check the connection first:

```
weasis-cli weasis:info
```

Exit status: `0` ok, `1` the command failed (the message is on stderr), `2` wrong usage, `3` no running
instance (`--launch` starts one), `4` timeout (`--timeout <s>`, default 30), `5` cannot connect,
`6` the namespace is not allowed remotely. When several instances run, pick one with
`--instance <id>`. Some commands would ask the user a yes/no question in Weasis (for example
whether to build an MPR volume with geometric rectification from few slices): pass `-y`
(`--assume-yes`) before the command to take the recommended answer without a dialog; the result
then reports what was decided (`warnings`, `rectified`).

## How commands work

```
weasis-cli <namespace>:<verb> [options]      # e.g. weasis-cli view2d:scroll --image 42
weasis-cli help                              # the namespaces, one line each
weasis-cli help view2d                       # the verbs of one namespace, one line each
weasis-cli help view2d:capture               # one verb: synopsis, parameters and options
weasis-cli help --markdown                   # the full reference, always current
weasis-cli help --json                       # the same as a JSON descriptor (commands.json)
weasis-cli --version                         # client version, command API and protocol versions
```

Each help level ends with the commands that go one level down or back, so drill down instead of
reading the whole reference: `help` to pick a namespace, `help <namespace>` to pick a verb,
`help <namespace>:<verb>` before running it. (In a terminal the same levels are browsed
interactively; from an agent they print.)

The command API is versioned (`major.minor`, @{apiVersion} here): new verbs and options only add, and a
verb or option is never removed within a major. Verbs marked *since 5.0* need Weasis 5;
`weasis:info` reports `commandApiVersion` when you have to check. The spellings of Weasis 4 were
dropped by this major, except the six lines a hospital portal writes (`dicom:get`, `dicom:rs`,
`dicom:close`, `image:get`, `image:close`, `weasis:info`), which still work and answer with a
line naming the current spelling. Use the current one everywhere.

- Output is JSON by default, on stdout. **An error is one JSON object on stderr**: `code`, the
  `message` to relay, and what helps you fix it — `usage` (the synopsis of the verb), `option`,
  `producedBy` (the verb that hands out the handle you got wrong), `policy` (the key that refused),
  `suggestions`, and `notices` such as a deprecated spelling. Read the `code` rather than the
  sentence: `usage`, `invalid-value`, `unknown-handle`, `unknown-command`, `not-applicable`
  (the verb is right, the state is not), `refused-by-policy`, `failed`, `timeout`. One retry
  should be enough; if it is not, tell the user what the body says.
- Ids are handles: view ids come from `workspace:list`, series UIDs from `dicom:list`, graphic ids
  from `view2d:measure` and `view2d:graphics`. Never invent one.
- Every index counts from 1: panes, images, frames.
- Every result bound to a view opens with `target`: the `view` and `pane` it resolved, the
  `viewerType`, the `series` shown there, the `image` index and the `imageCount`. Read it back
  instead of assuming which pane a default-target command acted on.
- Measured values are objects `{key, name, value, unit}`: `key` is the stable name of the
  measurement (`length`, `area`, `stats.mean`), `name` is English whatever the language of the
  interface, and `unit` is a UCUM code (`mm`, `mm2`, `deg`, `[hnsf'U]` for Hounsfield,
  `{pixel}`). DICOM attributes use their PS3.6 keyword (`patientID`, `studyInstanceUID`,
  `seriesNumber`), dates and times are ISO 8601.
- Verbs of `view2d:` act on the selected view and its selected pane unless you give `--view <id>`
  (and `--pane <n>`), and they **return the display state of that pane**: zoom, window/level,
  rotation, flip, pan, image, grid and synchronization. Called with no argument at all, they
  return that state without changing anything, which is how you read the display.
- A grid size is always **rows then columns**, `<rows>x<cols>`: `2x3` is two rows of three, for
  the grid ids of `view2d:grid`, the tiles of `--tiled` and the cells of `print:page --grid`. A
  size in pixels is the other way round, width then height. When unsure, read the `rows` and
  `cols` fields of the JSON answer instead of the text form.
- Values go on options, so nothing needs quoting tricks and no `--` is ever required:
  `view2d:pan --dx 10 --dy -5`, `view2d:window-level --window-by 200 --level-by -100`,
  `view2d:scroll --by -3`, `view2d:reset --tool window-level --tool zoom`.

## Workflow

1. **Discover** what is open: `weasis-cli workspace:list` (viewers) and `weasis-cli dicom:list`
   (patients, studies, series with UIDs and image counts).
2. **Open** a series when needed: `weasis-cli dicom:open --series <uid>` returns the view id, and
   `--view <id> --pane <n>` puts it in a chosen pane of a view you already have.
   `dicom:info` lists the `viewerTypes` able to display the series; `weasis-cli -y dicom:open
   --series <uid> --viewer-type mpr --wait` opens it in a new view of that type (`2d`, `mpr`,
   `3d`, …) and returns once the volume is built, so you can capture right away.
   `dicom:load --path <path>` loads files from disk and says what it queued; add
   `--wait first-series` to answer once one series is ready, or `--wait all` for everything.
3. **Look**: `weasis-cli view2d:capture --out /tmp/weasis/view.png`, then read the PNG. The result
   also gives `scale`, `crop`, the canvas size and `imageCorners` (see Coordinates). Use
   `--crop x,y,width,height` to zoom into a region of the canvas; `--scope layout` captures the
   whole layout of the view and `--scope window` the application window as displayed.
4. **Navigate**: `view2d:scroll --image <n>` (or `--by 1`), `view2d:window-level --window 400
   --level 40` (or `--window-by` / `--level-by` in slider steps), `view2d:preset` (lists the
   presets with their ids) then `--preset <id>`,
   `view2d:zoom --set 2` or `--fit best-fit`, `view2d:rotate --degrees 90`, `view2d:flip on`,
   `view2d:grid --panes 2`. Each of them returns the new state, so a capture is only needed to
   *see* the image, not to check what you changed.
5. **Measure** with the viewer, not with your eyes:
   - `view2d:measure --type line --points x1,y1,x2,y2` draws and returns the length;
     types: line, polyline, rectangle, ellipse (two opposite corners), circle (three points), polygon,
     angle (three points).
   - `view2d:stats --rect x,y,width,height` (or `--ellipse`, `--polygon`, `--graphic <id>`) returns
     `statistics` (`stats.min`, `stats.max`, `stats.mean`, `stats.median`, `stats.stdev`, …) in the
     unit of the image values (`[hnsf'U]` for CT) and the `geometry` of the region.
   - `view2d:graphics` lists every graphic with its measurements, including the user's own.
   - `measure:tools` lists every tool of the registry with the measurements it computes;
     `view2d:measure --tool-key <key> --points …` draws one `--type` does not name (bidirectional,
     CTR, arrow…). `measure:export --format csv|xml|sr|pr --out <file>` writes the graphics of the
     pane (`--format ?` lists what this instance writes); `measure:calibrate --graphic <id>
     --length <n> --unit mm` calibrates an uncalibrated image from a line of known length.
   - `view2d:annotate --at x,y --text "..."` points at a place with an arrow, and
     `view2d:annotate --label-at x,y --text "..."` writes a caption with nothing else drawn;
     `view2d:clear-ai` removes everything you drew.
6. **Read metadata**: `dicom:info --series <uid>` (modality, geometry, spacing, counts) and
   `dicom:tag --series <uid> --tag SliceLocation` (any standard attribute, by keyword or
   `(gggg,eeee)`; `--image <n>` selects the image, counting from 1).
7. **Arrange** for comparison: `workspace:split --view <a> --anchor <b> --side right`,
   `workspace:stack`, `workspace:maximize`, `workspace:save --out layout.json` and
   `workspace:load --file layout.json`. `workspace:list` shows, for each view, its grid, its
   synchronization and what every pane holds.
8. **Print** a layout, a pane or a series: `print:page` (the selected viewer, or `--view <id>`,
   `--pane 2` for one pane; `--series --grid 3x4 --step 2 --frames 1-40` for the frames of the
   pane's series, a page per grid; `--to <destination>`, `--paper A4 --orientation landscape
   --text normal --background white --header on`) returns a job handle, the number of pages, the
   page in millimeters and the figures of a cell (size, image pixels per inch of paper, magnified
   or reduced). `print:render --job <h> --page 2 --out /tmp/weasis/page.png --dpi 150` writes a
   page as the printer would receive it, to look at it before anything is printed.
   `print:destinations` lists the system printers, the DICOM printers and the PDF file;
   `print:send --job <h> --to <destination>` prints (`--to file:pdf --out /tmp/weasis/pages.pdf`
   writes a PDF), when the policy of the instance allows sending.
<!--if:subtraction-->
9. **Subtract** a pre-contrast series from its post-contrast acquisition. `subtraction:preview`
   on the pane of the post-contrast series shows the difference live, slice by slice, without
   building anything (`--mask <uid>` chooses the mask, else the series without contrast agent is
   proposed; `--factor 0.8` keeps part of the anatomy; `--off` puts the plain series back).
   `subtraction:create` builds what the pane previews, or `--series <uid> --mask <uid>` given
   explicitly, as a new series of the study (same grid only, real values, black where nothing
   enhances) and opens it in the pane; `--signed` opens with negative differences visible,
   `--no-open` only builds. A dynamic series stored as one series has phases:
   `subtraction:phases --series <uid>` lists them, `--phase 2 --mask-phase 1` subtracts one from
   another, `--all-phases` builds every phase minus the mask phase, and `--mip` opens the result
   as a maximum intensity projection, the breast overview. `subtraction:list` and
   `subtraction:info --series <uid>` describe what was built, `subtraction:delete` removes it.
   The result is a series: scroll, window, measure and capture it like any other.
<!--endif-->
<!--if:wsi-->
9. **Read a whole slide** (modality SM) in its own viewer: `dicom:open --series <uid>` opens it
   there. `wsi:info` describes the slide (size in base pixels, pixel spacing, objective
   magnification, pyramid levels) and what the pane shows (zoom, center, level, tiles loaded).
   `wsi:goto --at x,y` centers the view on a base pixel, `--objective 20` shows it at 20× and
   `--zoom 0.5` at a scale, `--focal-plane 2` and `--optical-path DAPI` pick the plane of a
   Z-stack and the channel of a multi-path slide (`wsi:info` lists them), `--complete` answers
   once every tile of the view is loaded, so the next `view2d:capture` is sharp. Measurements on
   a slide are in µm; the `view2d:` verbs work as on an image, with base pixels as image
   coordinates. `wsi:annotations` lists the bulk annotation groups of the slide (nuclei, cells,
   regions of a DICOM Microscopy Bulk Simple Annotations object loaded with the study), drawn
   over the tiles up to a cap; `--group 1 --hide` or `--show` chooses what is drawn.
   `wsi:export --out <file>` writes the measurements and drawings of the pane as a DICOM
   Structured Report (TID 1500, regions on the base level in µm); a report of a slide loaded with
   the study, by `dicom:load`, is drawn over the slide.
<!--endif-->
<!--if:view3d-->
10. **Before proposing volume rendering**, `view3d:info` says whether this machine can do it at
   all, on which graphics adapter and with which limits. A machine can run Weasis perfectly well
   and still have no usable 3D. `view3d:capture --out <file>` renders a 3D view as displayed, with
   the same `--scope` and `--max-pixels` as the 2D capture.
<!--endif-->
11. **Animations are verbs too.** `view2d:export-cine --out <file> [--first n --last m --step k
   --rate fps --scope --scale]` writes the images of a pane as the pane shows them, frame by frame;
   `view3d:export-rotation --out <file> [--frames n --axis x|y|z --sweep deg --ping-pong --rate]`
   writes a turn of the volume; `weasis:record start --out <file> [--scope layout|window --rate
   --max-seconds]` samples the display until `weasis:record stop`, which answers the frames written.
   The extension of `--out` picks the format (`.apng` or `.png`, `.gif`, `.dcm` for a DICOM
   Secondary Capture file of the cine or the rotation); every frame is masked as a capture from
   the same caller would be, and a pane whose pixels need a review is refused the same way.
12. **Anatomy is one notation.** `dicom:info` answers an `anatomy` object (the coded region, its
   `groups` such as `CHEST` or `HEAD`, the Body Part Examined it stands for, the laterality) and
   `dicom:list --anatomy <notation>` keeps the series of a region group (`CHEST`), a coded value
   (`SCT:816094009`) or a Body Part Examined term (`THORAX`); the hanging-protocol features and the
   window presets use the same words.

## Several commands in one call

`script:run --file <file>` runs a list of commands in one connection, which saves a turn per step
and keeps the handles of one step available to the next:

```
$open = dicom:open --series ${series} --wait
$line = view2d:measure --view ${open.target.view} --type line --points 316,272,332,288
@expect ${line.measurements[key=length].unit} == mm
```

One command per line, `#` comments, `$name = <command>` keeps the body of a step, `${name.path}`
reads inside it (`[2]` indexes an array, `[key=length]` picks the entry whose `key` is `length`),
`--set name=value` passes a parameter, and `@expect <value> <op> <value>` checks a value
(`==`, `!=`, `<`, `<=`, `>`, `>=`, `~ <n> +-<tolerance>`, `matches <regex>`). `$bad = @fails
<command>` expects a refusal: the step passes when the command fails and `${bad.code}`,
`${bad.message}` read its error. The answer is one JSON object per step, then a summary; a failing
step stops the run unless `--keep-going`. `script:check --file <file>` parses a plan without running
it (unknown verbs and options, unterminated quotes, a reference used before its step) and lists the
parameters it expects, which is the cheapest way to validate one before proposing it.

## Watching for changes

Do not poll `workspace:list` or `dicom:list` to find out what the user changed. The instance
publishes every change of state as an event, named like the commands (`dicom:study.opened`,
`workspace:view.selected`, `view2d:windowLevel.changed`, `graphic:added`), and three ways read them:

```
weasis-cli events --filter 'dicom:*,workspace:view.selected' --count 1 --timeout 60   # wait for the next one
weasis-cli events --since 1200 --filter 'view2d:*'          # replay what happened after seq 1200, then follow
weasis-cli events:recent --last 20                          # poll, when you cannot hold a stream
weasis-cli events:list                                      # the catalog: names, payload fields, since
```

The stream is one JSON object per line: `seq`, `name`, `time`, `origin` (`app`, or `remote` when a
command caused it) and the payload with the same keys and handles as the command results. Keep the
last `seq` you saw and pass it as `--since` to miss nothing. In a script, `@wait <event-glob>
[--timeout s] [--where key=value]` blocks a step until the event, and `${event.<key>}` reads its
payload in the next steps. Patient fields in the events are masked exactly as in the results.

## Coordinates

- Image coordinates are pixels of the DICOM image: origin at the top-left pixel, x to the right, y
  down. This is the default for `--points`, `--rect`, `--at` and the values in `graphics`.
- A capture shows the canvas after zoom, pan, rotation and flip. To act on something you located
  in a capture, convert the capture pixel `(px, py)` to canvas pixels and pass `--canvas`:
  `canvas_x = crop.x + px / scale`, `canvas_y = crop.y + py / scale` (`crop` is `0,0` when you did
  not use `--crop`). Weasis then applies the view transform for you.
- `imageCorners` gives the four image corners `(0,0)`, `(W,0)`, `(W,H)`, `(0,H)` in capture pixels,
  so you can also map a capture pixel to image pixels with a linear interpolation.

## Rules

- Lengths, angles, areas and densities come from `measure`, `stats` and `graphics`. Report the unit
  the result gives, written the way a person reads it: `mm`, `mm²`, `°`, `HU`.
- Patient identifiers, names and dates are masked by Weasis according to its identity policy. Do
  not try to recover or guess them, and do not ask for a way to disable the masking: there is none.
<!--if:redact-->
- A capture is refused when the image may carry burned-in identification and no redaction is set.
  Tell the user, and hide the burned-in text before capturing only when they ask: `redact:match`
  proposes the device entries that fit the view, `redact:apply --entry <id>` hides one of them, and
  `redact:add --rect x,y,width,height` hides an area you located. `redact:list` echoes what is hidden and
  `redact:clear` removes it. Never work around the refusal by other means.
<!--endif-->
- Captures and snapshots are files on this machine, written under the places the instance allows:
  its temporary directory and the user's home. A file that was already there is not replaced
  unless you pass `--force`, so write under a name of your own. Do not send a file anywhere
  unless the user asks.
- The network is closed by default: an option that fetches a URL (`dicom:load --url`,
  `--manifest`, `--zip`, `dicom:load-dicomweb`, `image:load --url`) is refused with exit status 6
  and a line naming the policy. Report that to the user instead of looking for another way; only
  the instance can open it.
- A refusal by the policy is final. `-y` answers the questions a viewer asks about its own work,
  such as rectifying a volume; it never lifts a refusal. `help <namespace>:<verb>` shows what a
  verb needs (`Needs: file write, network read`) before you try.
- `weasis:quit` stops the application for everyone using it. Ask the user first, and pass `-y` to
  confirm; without it the command is refused. Some instances forbid it outright.
- Never ask Weasis to open its query/retrieve page or its DICOMweb search: they show real
  identity and are closed to you. If you need a study you cannot name, ask the user for its
  identifier or its path.
- Remove what you drew with `view2d:clear-ai` when the user does not want to keep it.
- Loading and unloading studies (`dicom:load`, `dicom:unload`, `image:load`) changes what the user
  sees: do it only when asked.

## Vocabulary

One word per concept, in option names, JSON keys and what you say to the user. `help glossary`
prints the definitions, `help types` the value types with the verbs that hand them out, and
`help types view-id` one of them.

@{glossary}

## Reference

`weasis-cli help --markdown` prints every verb with its options, `weasis-cli help --json` the same
as a JSON descriptor, with the `types` and `glossary` sections at the end. A skill folder may ship them as `reference.md` and `commands.json`; the
running instance is what counts.
