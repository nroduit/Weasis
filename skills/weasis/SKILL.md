---
name: weasis
description: Drive the running Weasis DICOM viewer with the weasis-cli command - list and open studies, navigate slices, set window/level, capture what is displayed as an image, draw measurements and read pixel statistics or DICOM attributes. Use when the user asks to look at, analyze, measure, annotate or arrange medical images that are open in Weasis.
license: EPL-2.0 OR Apache-2.0
---

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

This instance runs Weasis development with command API 2.4 and protocol
1, and answers these namespaces: config, dicom, explorer, hp, image, redact, script, state, view2d, view3d, weasis, workspace.

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

The command API is versioned (`major.minor`, 2.4 here): new verbs and options only add, and a
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
- Values go on options, so nothing needs quoting tricks and no `--` is ever required:
  `view2d:pan --dx 10 --dy -5`, `view2d:window-level --window-by 200 --level-by -100`,
  `view2d:scroll --by -3`, `view2d:reset --tool window-level --tool zoom`.

## Workflow

1. **Discover** what is open: `weasis-cli workspace:list` (viewers) and `weasis-cli dicom:list`
   (patients, studies, series with UIDs and image counts).
   `explorer:state` says what the explorer panel itself shows (selected patient, filter, which
   series are visible, selected or not downloaded, the objects of each study);
   `explorer:facets` lists the values the filter can take with their counts (modalities, objects,
   images of interest, download states); `explorer:filter --modality CT --objects seg` narrows it, `explorer:select --series <uid>`
   selects as a click would, `explorer:reveal --series <uid>` scrolls to it and selects it (`visible: false` when the filter hides it), and `explorer:mode list` switches to the compact list (`auto` lets the explorer choose from the
   content and the panel width; the state says `modeSource`).
2. **Open** a series when needed: `weasis-cli dicom:open --series <uid>` returns the view id, and
   `--view <id> --pane <n>` puts it in a chosen pane of a view you already have.
   `dicom:info` lists the `viewerTypes` able to display the series; `weasis-cli -y dicom:open
   --series <uid> --viewer-type mpr --wait` opens it in a new view of that type (`2d`, `mpr`,
   `3d`, …) and returns once the volume is built, so you can capture right away.
   `dicom:load --path <path>` loads files from disk and says what it queued; add
   `--wait first-series` to answer once one series is ready, or `--wait all` for everything.
3. **Look**: `weasis-cli view2d:capture --out /tmp/weasis/view.png`, then read the PNG. The result
   also gives `scale`, `crop`, the canvas size and `imageCorners` (see Coordinates). Use
   `--crop x,y,width,height` to zoom into a region of the canvas.
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
   - `view2d:annotate --at x,y --text "..."` points at a place with an arrow, and
     `view2d:annotate --label-at x,y --text "..."` writes a caption with nothing else drawn;
     `view2d:clear-ai` removes everything you drew.
6. **Read metadata**: `dicom:info --series <uid>` (modality, geometry, spacing, counts) and
   `dicom:tag --series <uid> --tag SliceLocation` (any standard attribute, by keyword or
   `(gggg,eeee)`; `--image <n>` selects the image, counting from 1). `dicom:objects --study <uid>`
   lists the non-image objects of a study (key object selections, presentation states,
   segmentations, RT objects) with their kind, label, creator and the series they reference;
   `--series <uid>` keeps the objects that reference one series, `--type seg` one kind.
   `dicom:interest --study <uid> --images` lists the images of interest (referenced by a KO, an
   annotated PR or a SEG, or drawn on) with their sources; `explorer:filter --interest any` keeps
   only the series that have some; `dicom:open --series <uid> --interest` opens a series on those
   images alone.
7. **Arrange** for comparison: `workspace:split --view <a> --anchor <b> --side right`,
   `workspace:stack`, `workspace:maximize`, `workspace:save --out layout.json` and
   `workspace:load --file layout.json` (a closed viewer is created again with its series, grid
   and display when the series are still loaded; MPR and 3D viewers come back with their volume,
   crosshair, preset and camera). `workspace:list` shows, for each view, its grid, its
   synchronization and what every pane holds. To hang several series at once,
   `view2d:hang --grid 2x2 --cell 1=<uid> --cell 2=<uid> --sync stack` fills the cells of one
   viewer in a single call (`--tiled 1=2x2` tiles a cell, `--file` replays a saved layout,
   no argument returns the current one); `view2d:viewport --tiled 2x2` shows successive images
   of one series on tiles, `--stack` goes back to one image. The tool panels around the
   workspace are slots: `workspace:panel` shows which tool fills each slot (explorer and tools
   sides, top and bottom), `--side tools --slot bottom --tool measure` opens a tool there,
   `--collapse` / `--expand` hide or show a side; `workspace:save` keeps the shell too.
   Windows: `workspace:detach --view <id> --screen diagnostic-1` floats a view in a viewer
   window filling the screen of that role (`workspace:screens` lists the roles control,
   diagnostic-1, diagnostic-2, color, portrait with the monitor each resolves to; `--bounds`
   gives pixels instead; `--id <screen> --role <role>` gives a monitor a role for good, `--auto`
   takes it back). The toolbar row follows the active window, a new viewer opens in the
   window of the selected view, and `workspace:list` names that view under `selected`;
   `hp:apply --screen control` hangs a reading step in a viewer window on that screen. A
   saved layout remembers each window's screen role and state: `workspace:load` puts a window
   back on the monitor of its role, and folds into the main window one whose monitor is
   missing (listed under `folded`). `workspace:fullscreen [--view <id>]` puts a window on the
   whole monitor without decorations (`--off` ends it; kept by `workspace:save`), and
   `--canvas` shows the selected pane alone on the monitor, as F11 does, hiding the menu, the
   toolbar and the side panels until `--canvas --off`.
9. **Hanging protocols** (engine and built-in library, no UI yet): a study loaded by any verb
   opens already hung by its best protocol (setting `weasis.hp.open`, `PROTOCOL` by default,
   `FIRST_SERIES` for the old behaviour). `hp:list` shows the protocols of every layer, the
   built-in ones cover CT, MR, radiography, mammography, ultrasound, angiography, PET and a
   last-resort one cell per series;
   `hp:match [--study <uid>]` returns the protocols that apply to a loaded study, best first,
   and the plan of the best one: per reading step the layout to hang and, per cell, the series
   chosen, how it matched (exact, preferred, fallback, shared, empty), the reasons and the
   alternatives; `--protocol <id>` plans another one, `--protocols <file>` tries a draft file.
   `hp:apply [--study <uid>] [--protocol <id>] [--step <n>] [--new-tab]` hangs one reading step
   of that plan: the 2D cells in one viewer (re-hung when a viewer of the patient is open) and
   each MPR or 3D cell in its own, and returns the cells with the view ids they went to, so the
   `view2d:` verbs can address them; empty cells are listed under `unmatched`.
   `hp:sets [--view <id>]` is the active hanging of a viewer (protocol, step of how many, cells
   with view ids, candidates of the study); `hp:group --next | --previous | --index <n> |
   --protocol <id>` shows another reading step or hangs another candidate on the same viewer,
   what the protocol bar of the viewer does; `hp:explain [--cell <n>]` gives the rules, score and
   alternatives behind each cell; `hp:show --id <protocol-id>` prints a protocol as JSON and
   `hp:validate --file <file>` checks a protocol file for rule errors before trying it.
   `hp:save --name <text> [--dry-run]` is *Save as protocol*: it derives a user protocol from
   the display of the selected 2D viewer (per cell the smallest rule that singles its series
   out among the study, plus preset, inversion, sort, tiling and synchronization), reports the
   rules and which loaded studies it applies to, and stores it in the user layer unless
   `--dry-run`; `hp:delete --id <protocol-id>` removes a user protocol.
   Each hung cell shows an indicator of how its series matched (exact, preferred, fallback,
   shared, nothing matched, corrected) and the *Why* tool of the bottom slot (`workspace:panel
   --side tools --slot bottom --tool hp-why`) lists the rules and the alternatives of the
   selected cell. `hp:correct --cell <n> --series <uid> [--apply]` puts another series of the
   study in a cell, records the correction with the rule that would have chosen it, and
   `--apply` writes that rule into a user protocol; `hp:corrections` lists the log.
10. **Reading states**: `state:keep [--label <text>] [--pin]` keeps the whole display as a
   session of the user (windows, viewers, every pane's series and presentation, the shell, the
   protocol and step that hung it) and returns its id; `state:list` lists the sessions, most
   recently used first; `state:restore --session <id> | --study <uid>` restores one exactly
   when its studies are loaded (a study not loaded is reported: load it first); `state:delete
   --session <id>` removes one. Sessions hold study and series identifiers, never credentials,
   and are purged by the retention settings `weasis.hp.sessions.max` and
   `weasis.hp.sessions.max.days`.
   `hp:validate --corpus <dir> [--protocols <file>] [--expected <file>]` runs the matching over
   a folder of studies without any viewer and reports the counts per protocol, the protocol and
   first-step cells of each study and, with an expectations file, which studies hang as expected.
   `hp:features [--study <uid> | --corpus <dir>]` lists the series features the selectors test
   (role, orientation, anatomy, view, laterality, MR weighting, CT kernel family, thickness,
   temporal...), the way to see why a series matched or not.
11. **Before proposing volume rendering**, `view3d:info` says whether this machine can do it at
   all, on which graphics adapter and with which limits. A machine can run Weasis perfectly well
   and still have no usable 3D.

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
(`==`, `!=`, `<`, `<=`, `>`, `>=`, `~ <n> +-<tolerance>`, `matches <regex>`). The answer is one
JSON object per step, then a summary; a failing step stops the run unless `--keep-going`.
`script:check --file <file>` parses a plan without running it, which is the cheapest way to
validate one before proposing it.

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
- A capture is refused when the image may carry burned-in identification and no redaction is set.
  Tell the user, and hide the burned-in text before capturing only when they ask: `redact:match`
  proposes the device entries that fit the view, `redact:apply --entry <id>` hides one of them, and
  `redact:add --rect x,y,width,height` hides an area you located. `redact:list` echoes what is hidden and
  `redact:clear` removes it. Never work around the refusal by other means.
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

## Reference

`weasis-cli help --markdown` prints every verb with its options, `weasis-cli help --json` the same
as a JSON descriptor. A skill folder may ship them as `reference.md` and `commands.json`; the
running instance is what counts.
