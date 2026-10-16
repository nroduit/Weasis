# Weasis commands

Command API 2.0, protocol 1, application development.

## acquire

Image acquisition (Dicomizer): patient context.

### acquire:patient

Loads the patient context of the acquisition and returns the context it set, masked for a remote caller.

Usage: `acquire:patient [-h] [--json] (--url=<remote-url> | --xml=<text> | --xml-gzip-urlsafe=<base64> | --xml-gzip=<base64>)`

Needs: network read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --url | remote-url |  | URL of an XML file holding the DICOM tags. (since 5.0) |
| --xml | string |  | XML text (UTF-8) holding the DICOM tags. (since 5.0) |
| --xml-gzip-urlsafe | string |  | XML encoded as GZIP then URL-safe Base64. (since 5.0) |
| --xml-gzip | string |  | XML encoded as GZIP then Base64. (since 5.0) |


## dicom

The DICOM explorer: loading, listing, opening and closing studies, and reading attributes.

### dicom:info (since 5.0)

Technical description of a series: modality, body part, image count, geometry and pixel spacing. Identifiers follow the identity policy.

Usage: `dicom:info [-h] [--json] (--series=<series-uid> | --view=<view-id>)`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list'. |
| --view | view-id |  | Viewer id from 'workspace:list'; its selected pane gives the series. |

### dicom:list (since 5.0)

Patients, studies and series loaded in the explorer, as JSON. Identifiers follow the identity policy; UIDs are the handles for the other verbs.

Usage: `dicom:list [-h] [--json] [--after=<n>] [--limit=<n>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --limit | integer |  | At most this many entries (default: all of them). (since 5.0) |
| --after | integer |  | Skip the first n entries, as the previous answer's 'next' says. (since 5.0) |

### dicom:load

Loads DICOM files from local paths, URLs, manifests, ZIP archives or the portable directories, and returns what was queued and how far the loading went.

Usage: `dicom:load [-h] [--json] [--portable] [--wait[=<wait-level>]] [--timeout=<seconds>] [--manifest=<remote-url>]... [--manifest-gzip=<data>]... [--path=<input-path>]... [--url=<remote-url>]... [--zip=<remote-url>]...`

Needs: file read, network read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --path | input-path | [] | A DICOM file or a directory (recursive). (since 5.0) |
| --url | remote-url | [] | A DICOM file URL. (since 5.0) |
| --manifest | remote-url | [] | An XML manifest (WADO). (since 5.0) |
| --zip | remote-url | [] | A ZIP archive of DICOM files. |
| --manifest-gzip | string | [] | An XML manifest encoded as GZIP then Base64. (since 5.0) |
| --portable | boolean |  | The DICOM directories configured next to the executable. |
| --wait | enum | none | Answer when the loading reaches this point: none, first-series or all (default: none, or all when the option carries no value). (since 5.0) |
| --timeout | integer | 600 | Give up waiting after this long (default: 600). (since 5.0) |

### dicom:load-dicomweb

Loads DICOM studies through a DICOMweb service (QIDO/WADO-RS) and returns what was queued and how far the loading went.

Usage: `dicom:load-dicomweb [-h] [--json] [--show-whole-study] [--wait[=<wait-level>]] [--accept-ext=<ext>] [--auth-uid=<auth-uid>] [--oidc-issuer=<issuer>] [--oidc-user=<login>] [--query-ext=<ext>] [--retrieve-ext=<ext>] [--timeout=<seconds>] [--url=<remote-url>] [-H=<header>]... [--query-header=<header>]... [--request=<query>]... [--retrieve-header=<header>]...`

Needs: network read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --url | remote-url |  | URL of the DICOMweb service. |
| --request | string | [] | Query parameters of the URL, see weasis-pacs-connector. |
| -H, --header | string | [] | Custom header for every request. |
| --query-header | string | [] | Custom header for the query requests (QIDO). |
| --retrieve-header | string | [] | Custom header for the retrieve requests (WADO). |
| --query-ext | string |  | Additional query URL parameters (QIDO). |
| --retrieve-ext | string |  | Additional retrieve URL parameters (WADO). |
| --accept-ext | string | transfer-syntax=* | Parameters of the multipart/related Accept header (default: transfer-syntax=*). |
| --auth-uid | string |  | UID of the Weasis authentication method. |
| --oidc-issuer | string |  | Issuer identifier of the OpenID Connect authentication request. (since 5.0) |
| --oidc-user | string |  | Login hint of the end user (OpenID Connect). (since 5.0) |
| --show-whole-study | boolean |  | When downloading a series, list the other series of the study, ready for download. |
| --wait | enum | none | Answer when the loading reaches this point: none, first-series or all (default: none, or all when the option carries no value). (since 5.0) |
| --timeout | integer | 600 | Give up waiting after this long (default: 600). (since 5.0) |

### dicom:open (since 5.0)

Opens a series: in the view given by --view, and in its pane given by --pane, in a new view of the type given by --viewer-type, or in the default viewer of its type. Returns the view id and what the viewer decided while opening (e.g. 'rectified', 'warnings'); the client option -y answers its questions instead of showing them.

Usage: `dicom:open [-h] [--json] [--wait[=<wait-level>]] [--pane=<pane-index>] --series=<series-uid> [--view=<view-id> | --viewer-type=<viewer-type>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list'. |
| --view | view-id |  | Viewer id from 'workspace:list' that receives the series. |
| --viewer-type | string |  | Opens a new view of this type, by id from 'dicom:info' (e.g. 'mpr'), or by the name the interface shows. (since 5.0) |
| --pane | pane-index |  | Pane of --view that receives the series, from 1 (default: the selected one). (since 5.0) |
| --wait | enum | none | Answer once the viewer has finished building its content, e.g. the MPR volume: none or all, since a viewer builds in one step (default: none, or all when the option carries no value). |

### dicom:tag (since 5.0)

Value of a DICOM attribute of an image of the series, by keyword or (gggg,eeee). Identity attributes follow the identity policy; private tags are not exposed.

Usage: `dicom:tag [-h] [--json] [--image=<image-index>] [--tag=<dicom-keyword>] (--series=<series-uid> | --view=<view-id>)`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list'. |
| --view | view-id |  | Viewer id from 'workspace:list'; its selected pane gives the series. |
| --tag | string |  | DICOM keyword (e.g. SliceLocation) or hexadecimal tag (gggg,eeee). (since 5.0) |
| --image | image-index | 1 | Image index in the series, from 1 (default: 1). |

### dicom:unload

Takes patients, studies or series out of the explorer and returns what was unloaded. Every handle is resolved first: an unknown one unloads nothing. A patient is named by the identifier 'dicom:list' returned, which is the masked one under a masking policy.

Usage: `dicom:unload [-h] [--all] [--json] [--patient=<patient-handle>]... [--series=<series-uid>]... [--study=<study-uid>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --all | boolean |  | Unload every patient. |
| --patient | patient-handle | [] | Patient identifier to unload, as 'dicom:list' returns it. |
| --study | study-uid | [] | Study Instance UID to unload. |
| --series | series-uid | [] | Series Instance UID, or series id, to unload. |


## image

Loading and closing of plain images (non-DICOM) in the default viewer.

### image:compare (since 5.0)

Compares two image files pixel by pixel and returns how far apart they are: the number of pixels that differ, their share, and the largest and mean channel difference.

Usage: `image:compare [-h] [--json] --a=<input-path> --b=<input-path> [--tolerance=<n>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --a | input-path |  | First image. (since 5.0) |
| --b | input-path |  | Second image. (since 5.0) |
| --tolerance | integer | 0 | Channel difference below which two pixels count as equal, 0 to 255 (default: 0). (since 5.0) |

### image:list (since 5.0)

The groups and series of images opened in the default viewer, as JSON.

Usage: `image:list [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### image:load

Opens images from files or URLs and returns the series it opened.

Usage: `image:load [-h] [--json] [--file=<input-path>]... [--url=<remote-url>]...`

Needs: file read, network read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --file | input-path | [] | Image file to open. |
| --url | remote-url | [] | Image URL to download and open. |

### image:unload

Closes groups or images and returns what was closed; an unknown handle closes nothing.

Usage: `image:unload [-h] [--all] [--json] [--group=<group>]... [--series=<series>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --all | boolean |  | Close every group. |
| --group | string | [] | Group to close, as 'image:load' returns it. |
| --series | string | [] | Series or image to close, as 'image:load' returns it. |


## redact

Regions burned over the pixels of a view, and the device library they can come from.

### redact:add (since 5.0)

Hides an area of the selected view.

Usage: `redact:add [-h] [--json] [--pane=<pane-index>] [--scope=<scope>] [--view=<view-id>] (--rect=<image-rect> | --ellipse=<image-rect> | --polygon=<image-polygon>)`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --rect | image-rect |  | Axis-aligned rectangle, in image pixels. |
| --ellipse | image-rect |  | Ellipse inscribed in this rectangle. |
| --polygon | image-polygon |  | Closed outline as x,y pairs (three at least). |
| --scope | enum | IMAGE | Where it applies: IMAGE, SERIES. |

### redact:apply (since 5.0)

Hides the regions of a device entry on the selected view, all of them: a chosen entry is applied as it was drawn, where 'match' proposes only what the profile in force does not keep.

Usage: `redact:apply [-h] [--json] --entry=<entry-id> [--pane=<pane-index>] [--scope=<scope>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --entry | entry-id |  | Device entry from 'redact:library'. |
| --scope | enum | IMAGE | Where it applies: IMAGE, SERIES. |

### redact:clear (since 5.0)

Removes the regions of the selected image or series.

Usage: `redact:clear [-h] [--json] [--pane=<pane-index>] [--scope=<scope>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --scope | enum | IMAGE | Where it applies: IMAGE, SERIES. |

### redact:delete (since 5.0)

Removes a device entry from the user library.

Usage: `redact:delete [-h] [--json] --entry=<entry-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --entry | entry-id |  | Device entry from 'redact:library'. |

### redact:export (since 5.0)

Writes the device library to a file.

Usage: `redact:export [-h] [--complete] [--force] [--json] [--karnak] [--masking-profile=<masking-profile-id>] --out=<output-path>`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --out | output-path |  | File to write. |
| --force | boolean |  | Replace the file when it exists and was not written by this session. (since 5.0) |
| --karnak | boolean |  | Write the masks block of a Karnak profile. |
| --complete | boolean |  | Write a whole Karnak profile, not only its masks block. |
| --masking-profile | masking-profile-id |  | Masking profile of a Karnak export (default: the session one). (since 5.0) |

### redact:import (since 5.0)

Adds the device entries of a file to the user library.

Usage: `redact:import [-h] [--json] [--karnak] --file=<input-path>`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --file | input-path |  | Document to read. |
| --karnak | boolean |  | Read the masks block of a Karnak profile. |

### redact:library (since 5.0)

The device entries of the masking document.

Usage: `redact:library [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### redact:list (since 5.0)

The regions hidden on the selected view.

Usage: `redact:list [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |

### redact:match (since 5.0)

The device entries that fit the selected view, and why the others do not.

Usage: `redact:match [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |

### redact:save (since 5.0)

Saves the regions of the selected view as a device entry of the library.

Usage: `redact:save [-h] [--json] [--entry=<entry-id>] [--name=<name>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --name | string |  | Name of the entry (default: the station name of the series). |
| --entry | entry-id |  | Entry to replace (default: a new one). |


## script

Runs a list of commands from a file, with variables and expectations.

### script:check (since 5.0)

Parses every step against the command models without running anything, and answers what would fail.

Usage: `script:check [-h] [--json] --file=<input-path>`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --file | input-path |  | The script to read, one command per line. (since 5.0) |

### script:pause (since 5.0)

Waits, to pace a demonstration inside a script.

Usage: `script:pause [-h] [--json] --seconds=<s>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --seconds | number | 0.0 | How long to wait. (since 5.0) |

### script:run (since 5.0)

Runs every command of the file and answers one JSON object per step, then a summary.

Usage: `script:run [-h] [--brief] [--json] [--keep-going] [--delay=<ms>] --file=<input-path> [--from=<n>] [--report=<output-path>] [--timeout=<s>] [--to=<n>] [--set=<name=value>]...`

Needs: file read, file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --file | input-path |  | The script to read, one command per line. (since 5.0) |
| --set | string | {} | A parameter the script reads as null. (since 5.0) |
| --keep-going | boolean |  | Run the remaining steps after a failing one. (since 5.0) |
| --delay | integer | 0 | Wait this long between steps (default: 0). (since 5.0) |
| --from | integer | 1 | First step to run, counting from 1. (since 5.0) |
| --to | integer |  | Last step to run (default: the last one). (since 5.0) |
| --timeout | integer | 0 | Give up after this long, 0 to wait as long as it takes. (since 5.0) |
| --brief | boolean |  | Leave the body of each step out of the answer. (since 5.0) |
| --report | output-path |  | Write a JUnit XML report of the run to this file. (since 5.0) |


## view2d

Display of a 2D DICOM view: zoom, window/level, pan, scrolling, grid, mouse action, and the agent verbs capture, stats and measure. Every verb works on the pane given by --view and --pane, the selected one by default, and returns its display state.

### view2d:annotate (since 5.0)

Adds a text annotation on the AI layer of a pane: with --at it points at that place with an arrow, with --label-at alone it is a caption, text where you put it and nothing else drawn.

Usage: `view2d:annotate [-h] [--canvas] [--json] [--at=<image-point>] [--label-at=<image-point>] [--pane=<pane-index>] --text=<text> [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --at | image-point |  | Place the annotation points at; without it the text stands alone. |
| --text | string |  | Text to show. |
| --label-at | image-point |  | Position of the text itself (default: above and right of --at). |
| --canvas | boolean |  | Coordinates are canvas pixels (as in a capture) instead of image pixels. |

### view2d:capture (since 5.0)

Renders a view as displayed (window/level, annotations, identity masking) into a PNG or JPEG file and returns how image pixels map to the capture.

Usage: `view2d:capture [-h] [--force] [--json] [--crop=<canvas-rect>] [--max-pixels=<n>] --out=<output-path> [--pane=<pane-index>] [--view=<view-id>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --out | output-path |  | File to write; the extension selects PNG or JPEG (.jpg). |
| --force | boolean |  | Replace the file when it exists and was not written by this session. (since 5.0) |
| --crop | canvas-rect |  | Crop to this region of the canvas before scaling. (since 5.0) |
| --max-pixels | integer | 2000000 | Downscale so the file has at most this many pixels (default: 2000000). |

### view2d:clear-ai (since 5.0)

Removes every graphic drawn by the agent in the view.

Usage: `view2d:clear-ai [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |

### view2d:flip (since 5.0)

Mirrors the image of a pane and returns its display state; with no argument the state is returned unchanged. A vertical flip is the horizontal one turned by 180°, the form DICOM presentation states use.

Usage: `view2d:flip [-h] [--json] [--axis=<flip-axis>] [--pane=<pane-index>] [--view=<view-id>] [on|off]`

| Parameter | Type | Description |
|---|---|---|
| on\|off | enum | on, off. |

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --axis | enum | horizontal | horizontal or vertical (default: horizontal). (since 5.0) |

### view2d:graphics (since 5.0)

Lists the graphics of the view: id, type, layer, vertices and measurements, so measured values come from the viewer rather than from a picture.

Usage: `view2d:graphics [-h] [--json] [--no-measurements] [--after=<n>] [--layer=<layer-name>] [--limit=<n>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --layer | layer-name |  | Only the graphics of this layer name, e.g. AI. |
| --no-measurements | boolean |  | Skip the measurement values. |
| --limit | integer |  | At most this many entries (default: all of them). (since 5.0) |
| --after | integer |  | Skip the first n entries, as the previous answer's 'next' says. (since 5.0) |

### view2d:grid

Grid of panes of a viewer: with no argument it lists the grids it offers, --panes and --grid apply one and return the state of the viewer, --best-for answers without changing anything. An unknown grid id fails.

Usage: `view2d:grid [-h] [--json] [--view=<view-id>] [--best-for=<n> | --panes=<n> | --grid=<grid-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --best-for | integer |  | Returns the best grid for this number of panes without applying it. (since 5.0) |
| --panes | integer |  | Applies the best grid for this number of panes. |
| --grid | grid-id |  | Applies this grid, by id from the bare verb. |

### view2d:measure (since 5.0)

Draws a measurement on the AI layer of the view and returns its values, in the spatial unit of the viewer (mm when the image is calibrated).

Usage: `view2d:measure [-h] [--canvas] [--json] [--pane=<pane-index>] --points=<image-polygon> --type=<type> [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --type | enum |  | LINE, POLYLINE, RECTANGLE, ELLIPSE, CIRCLE, POLYGON, ANGLE. |
| --points | image-polygon |  | Vertices as x,y pairs; rectangle and ellipse take two opposite corners. |
| --canvas | boolean |  | Coordinates are canvas pixels (as in a capture) instead of image pixels. |

### view2d:mouse-action

Binds an action to a mouse button and returns the bindings; with no action the current ones are returned unchanged.

Usage: `view2d:mouse-action [-h] [--json] [--action=<mouse-action-type>] [--button=<mouse-button>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --button | enum | left | left, middle, right or wheel (default: left). (since 5.0) |
| --action | enum |  | sequence, window-level, zoom, pan, rotation, crosshair, measure, draw, context-menu or none. (since 5.0) |

### view2d:pan

Moves the image inside a pane by an offset in canvas pixels and returns its display state; with no argument the state is returned unchanged.

Usage: `view2d:pan [-h] [--json] [--dx=<canvas-pixels>] [--dy=<canvas-pixels>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --dx | integer |  | Horizontal offset, positive to the right. (since 5.0) |
| --dy | integer |  | Vertical offset, positive downwards. (since 5.0) |

### view2d:preset (since 5.0)

Applies a window/level preset to a pane and returns its display state, or lists the presets of its image when none is given. The presets belong to the image, so the pane given by --view and --pane is selected first.

Usage: `view2d:preset [-h] [--json] [--pane=<pane-index>] [--preset=<preset-id>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --preset | preset-id |  | Preset id, or its name as the viewer shows it. (since 5.0) |

### view2d:reset

Puts the display of a pane back as the image opened, whole or tool by tool, and returns its state; with no argument the state is returned unchanged.

Usage: `view2d:reset [-h] [--all] [--json] [--pane=<pane-index>] [--view=<view-id>] [--tool=<reset-tool>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --all | boolean |  | Reset everything to the original display. |
| --tool | enum | [] | window-level, zoom, pan or rotation; repeat it for several. (since 5.0) |

### view2d:rotate (since 5.0)

Turns the image of a pane clockwise and returns its display state; with no argument the state is returned unchanged.

Usage: `view2d:rotate [-h] [--json] [--by=<degrees>] [--degrees=<degrees>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --degrees | degrees |  | Absolute angle, clockwise; any integer, reduced to 0-359. |
| --by | integer |  | Turns by this many degrees, negative counter-clockwise. (since 5.0) |

### view2d:scroll

Moves through the images of the series a pane shows; with no argument the current position is returned unchanged.

Usage: `view2d:scroll [-h] [--json] [--pane=<pane-index>] [--view=<view-id>] [--by=<images> | --image=<image-index>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --by | integer |  | Moves by this many images, negative towards the first one. |
| --image | image-index |  | Image to show, from 1. |

### view2d:stats (since 5.0)

Pixel statistics of a region (min, max, mean, median, standard deviation, ...) in the image value unit, e.g. HU, plus the region geometry in the spatial unit.

Usage: `view2d:stats [-h] [--canvas] [--json] [--pane=<pane-index>] [--view=<view-id>] (--rect=<image-rect> | --ellipse=<image-rect> | --polygon=<image-polygon> | --graphic=<graphic-id>)`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --rect | image-rect |  | Axis-aligned rectangle, in image pixels unless --canvas is given. |
| --ellipse | image-rect |  | Ellipse inscribed in this rectangle. |
| --polygon | image-polygon |  | Polygon vertices as x,y pairs (three at least). |
| --graphic | graphic-id |  | Id of an existing area graphic of the view, from 'graphics'. |
| --canvas | boolean |  | Coordinates are canvas pixels (as in a capture) instead of image pixels. |

### view2d:sync

Sets how the panes of a viewer follow each other and returns its state: none turns the synchronization off, another mode turns it on; with no argument the state is returned unchanged.

Usage: `view2d:sync [-h] [--json] [--mode=<sync-mode>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --mode | enum |  | none, stack or tile, among the modes the viewer offers. (since 5.0) |

### view2d:window-level

Window and level of a pane: absolute with --window and --level, relative with --window-by and --level-by in slider steps (range 4096). With no argument the state is returned unchanged.

Usage: `view2d:window-level [-h] [--json] [--level-by=<steps>] [--pane=<pane-index>] [--view=<view-id>] [--window-by=<steps>] [--window=<window-width> --level=<window-center>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --window | window-width |  | Width of the window, in the unit of the pixels. (since 5.0) |
| --level | window-center |  | Centre of the window, in the unit of the pixels. (since 5.0) |
| --window-by | integer |  | Widens the window by this many slider steps, negative to narrow it. (since 5.0) |
| --level-by | integer |  | Raises the level by this many slider steps, negative to lower it. (since 5.0) |

### view2d:zoom

Changes the zoom of a pane and returns its display state; with no argument the state is returned unchanged.

Usage: `view2d:zoom [-h] [--json] [--pane=<pane-index>] [--view=<view-id>] [--set=<magnification> | --fit=<zoom-fit> | --by=<steps>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --set | magnification |  | Magnification, 1 being one image pixel per canvas pixel. |
| --fit | enum |  | default, real-size or best-fit. |
| --by | integer |  | Moves the zoom slider by this many steps, negative to zoom out. |


## view3d

Volume rendering: the capabilities of the renderer on this machine.

### view3d:info (since 5.0)

Reports whether volume rendering is available here, the binding and the context it runs on, the graphics adapter and the limits that decide the rendering path.

Usage: `view3d:info [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |


## weasis

The application itself: version, installation and the main window.

### weasis:info

Version and installation details, the version being one of the keys. The 4.x option -v still answers with the version alone.

Usage: `weasis:info [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### weasis:minimize (since 5.0)

Minimizes the main window and returns its state.

Usage: `weasis:minimize [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### weasis:quit (since 5.0)

Shuts Weasis down and returns once the reply is written; a remote caller confirms with -y, and the instance may forbid it.

Usage: `weasis:quit [-h] [--json]`

Needs: shutdown

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### weasis:show (since 5.0)

Brings the main window to the front and returns its state.

Usage: `weasis:show [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |


## workspace

Layout of the viewer workspace: windows, tab groups and views. View ids come from 'workspace:list'; verbs that change the layout return the new state with --json.

### workspace:close

Closes a view (fails when the view is not closable).

Usage: `workspace:close [-h] [--json] --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |

### workspace:detach

Floats a view in its own window.

Usage: `workspace:detach [-h] [--json] [--bounds=<screen-rect>] --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |
| --bounds | screen-rect |  | Screen bounds of the new window, as x,y,width,height. |

### workspace:list

Windows, tab groups and views as JSON.

Usage: `workspace:list [-h] [--json] [--after=<n>] [--limit=<n>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --limit | integer |  | At most this many entries (default: all of them). (since 5.0) |
| --after | integer |  | Skip the first n entries, as the previous answer's 'next' says. (since 5.0) |

### workspace:load

Restores a layout captured by 'workspace:save': open views are reused, closed viewers are created again with their series and presentation when the series are still loaded.

Usage: `workspace:load [-h] [--json] --file=<input-path>`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --file | input-path |  | Snapshot file to restore. |

### workspace:maximize

Shows a view alone in its window.

Usage: `workspace:maximize [-h] [--json] --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |

### workspace:save

Captures the layout as JSON, inline or to a file.

Usage: `workspace:save [-h] [--force] [--json] [--out=<output-path>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --out | output-path |  | Write the snapshot to this file and report its path. |
| --force | boolean |  | Replace the file when it exists and was not written by this session. (since 5.0) |

### workspace:select

Brings a view to the front of its tab group.

Usage: `workspace:select [-h] [--json] --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |

### workspace:split

Places a view next to another view in a new split.

Usage: `workspace:split [-h] [--json] --anchor=<view-id> [--ratio=<split-ratio>] --side=<side> --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |
| --anchor | view-id |  | View whose group is split. |
| --side | enum |  | Side of the anchor for the new group: TOP, BOTTOM, LEFT, RIGHT. |
| --ratio | split-ratio | 0.5 | Share of the space given to the anchor (default: 0.5). |

### workspace:stack

Moves a view into the tab group of another view.

Usage: `workspace:stack [-h] [--json] --anchor=<view-id> --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |
| --anchor | view-id |  | View whose tab group receives the view. |

### workspace:unmaximize

Ends the maximized state of a view.

Usage: `workspace:unmaximize [-h] [--json] --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |


