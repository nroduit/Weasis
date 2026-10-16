# Weasis commands

Command API 2.2, protocol 1, application development.

## config

Site configuration documents and declared settings.

### config:catalog

The declared settings with their type and scope.

Usage: `config:catalog [-h] [--json] [--out=<output-path>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --out | output-path |  | File to write the catalog into instead of printing it. |

### config:export

Converts the legacy XML documents of a site package into JSON.

Usage: `config:export [-h] [--force] [--json] [--source=<folder>] [--target=<folder>]`

Needs: file read, file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --source | path |  | Root of the resources package holding the XML documents (default: the current one). |
| --target | path |  | Folder to write the JSON documents into (default: the config folder of the preference directory). |
| --force | boolean |  | Overwrite a JSON document that exists. |

### config:list

The config folder of the resources package, the documents it holds, and the user and machine documents of this installation.

Usage: `config:list [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### config:pack

Writes the settings and documents of a layer into a package, to move them to another machine or to give them to Viewer Hub.

Usage: `config:pack [-h] [--json] [--with-machine] [--layer=<layer>] [--out=<output-path>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --layer | string | user | user (default), machine, or site (the config folder of a resources package). |
| --with-machine | boolean |  | With a user package, also the settings and documents of this workstation. |
| --out | output-path |  | The package to write (default: a dated name in the current folder). |

### config:reload

Re-reads every site and user document without restarting Weasis.

Usage: `config:reload [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### config:setting

The value of a declared setting, where it comes from and whether it is locked.

Usage: `config:setting [-h] [--json] <key>`

| Parameter | Type | Description |
|---|---|---|
| <key> | string | Key of the setting, as listed by config:catalog. |

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### config:sync

Mirrors the user settings and documents with the preference service: sends what changed, and with --pull reads and merges the service's copy first.

Usage: `config:sync [-h] [--json] [--pull]`

Needs: network read, network write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pull | boolean |  | Read and merge the service's copy before sending. |

### config:unpack

Imports a settings package after a backup of the current settings; --dry-run shows what would change.

Usage: `config:unpack [-h] [--dry-run] [--json] [--replace] --file=<input-path>`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --file | input-path |  | The package to import. |
| --replace | boolean |  | Replace each document and settings group of the package whole, instead of merging. |
| --dry-run | boolean |  | Show what would change, change nothing. |


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

### dicom:interest (since 5.0)

Images of interest of the loaded series: those a key object selection, an annotated presentation state or a segmentation points at, and those drawn on in Weasis, with their sources. Filter by study or by series.

Usage: `dicom:interest [-h] [--images] [--json] [--series=<series-uid>] [--study=<study-uid>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --study | study-uid |  | Study Instance UID from 'dicom:list'; default: every loaded study. |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list': this series only. |
| --images | boolean |  | Also list the SOP Instance UIDs of the images of interest. |

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

### dicom:objects (since 5.0)

Non-image objects of the loaded studies (key object selections, presentation states, segmentations, RT objects): kind, label, date, creator and the series they reference. Filter by study, by referenced series or by kind. Labels follow the identity policy.

Usage: `dicom:objects [-h] [--json] [--series=<series-uid>] [--study=<study-uid>] [--type=<kind>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --study | study-uid |  | Study Instance UID from 'dicom:list'; default: every loaded study. |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list': only the objects that reference this series. |
| --type | string |  | Only this kind: ko, rejection, pr, seg, rtstruct, rtplan, rtdose, other. |

### dicom:open (since 5.0)

Opens a series: in the view given by --view, and in its pane given by --pane, in a new view of the type given by --viewer-type, or in the default viewer of its type. Returns the view id and what the viewer decided while opening (e.g. 'rectified', 'warnings'); the client option -y answers its questions instead of showing them.

Usage: `dicom:open [-h] [--interest] [--json] [--wait[=<wait-level>]] [--pane=<pane-index>] --series=<series-uid> [--view=<view-id> | --viewer-type=<viewer-type>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list'. |
| --view | view-id |  | Viewer id from 'workspace:list' that receives the series. |
| --viewer-type | string |  | Opens a new view of this type, by id from 'dicom:info' (e.g. 'mpr'), or by the name the interface shows. (since 5.0) |
| --pane | pane-index |  | Pane of --view that receives the series, from 1 (default: the selected one). (since 5.0) |
| --interest | boolean |  | Show the images of interest only (referenced by a KO, an annotated PR or a SEG, or drawn on); the whole series when it has none. (since 5.0) |
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


## explorer

The explorer panel: its filter facets, its selection and what it shows. 'dicom:' reads the data, 'explorer:' the view of it.

### explorer:filter (since 5.0)

Sets the facets of the series filter of the DICOM explorer, combined with AND: text, study, modalities, kinds of objects referencing the series, download state. Without option, prints the current filter. Returns the filter and how many series of the selected patient pass it.

Usage: `explorer:filter [-h] [--clear] [--json] [--text=<text>] [--interest=<interest-source>[,<interest-source>...]]... [--modality=<modality>[,<modality>...]]... [--objects=<kind>[,<kind>...]]... [--state=<download-state>[, <download-state>...]]... [--study=<study-uid>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --text | string |  | Free text over series and study attributes, anatomy and its region group. |
| --study | study-uid | [] | Study Instance UID from 'dicom:list': only its series; repeatable, as a selection on the timeline strip. |
| --modality | string | [] | Modalities to keep (repeatable, or comma-separated). |
| --objects | string | [] | Keep the series referenced by an object of one of these kinds: ko, pr, seg, rtstruct, rtplan, rtdose, rejection, other (repeatable, or comma-separated). |
| --state | string | [] | Download states to keep: complete, pending, downloading. |
| --interest | string | [] | Keep the series with images of interest: any, or pointed at by one of ko, graphics, pr, seg (repeatable, or comma-separated). |
| --clear | boolean |  | Clears every facet first. |

### explorer:facets (since 5.0)

The values the series filter can take for the selected patient, with the number of its series each value keeps: modalities, kinds of objects referencing the series, sources of images of interest, download states. The menu of the filter button shows the same; lets an agent choose a filter before 'explorer:filter'.

Usage: `explorer:facets [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### explorer:mode (since 5.0)

Sets how the DICOM explorer draws the selected patient: 'thumbnails' (the column of thumbnails) or 'list' (one row per series and per object, studies as group rows). Without argument, prints the current mode.

Usage: `explorer:mode [-h] [--json] [<explorer-mode>]`

| Parameter | Type | Description |
|---|---|---|
| <explorer-mode> | string | thumbnails, list, or auto (forget the choice, let the rules decide). |

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### explorer:select (since 5.0)

Selects series in the DICOM explorer (and the patient they belong to), as a click would; the next 'dicom:open' without --series opens them. Returns the state.

Usage: `explorer:select [-h] [--json] --series=<series-uid> [--series=<series-uid>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid | [] | Series Instance UID (or series id) from 'dicom:list'; repeatable. |

### explorer:reveal (since 5.0)

Scrolls the DICOM explorer to a series and selects it, as a reader pointing at it: selects its patient, unfolds its study in the compact list and brings its thumbnail or row into view. Reports 'visible': false when the filter hides it (the filter is not changed).

Usage: `explorer:reveal [-h] [--json] --series=<series-uid>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list'. |

### explorer:state (since 5.0)

What the DICOM explorer shows: the selected patient, the filter and its counts, the studies with their series (visible or filtered out, download state, selected) and their non-image objects. Identifiers follow the identity policy.

Usage: `explorer:state [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |


## hp

Hanging protocols: the protocols of every layer, the plan the engine computes for a loaded study, and the validation of the protocols over a folder of studies.

### hp:list (since 5.0)

The protocols of every layer (built-in, site, user) with their level, whether they are enabled, and the number of image sets and display sets they declare.

Usage: `hp:list [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### hp:match (since 5.0)

The hanging plan of a loaded study: the protocols that apply, best first, and the plan of the best one (or of --protocol): per reading step the layout to hang and, per cell, the series chosen, how it matched, why, and the alternatives. The study is --study, else the one of the series selected in the explorer, else the only loaded one.

Usage: `hp:match [-h] [--json] [--protocol=<protocol-id>] [--protocols=<input-path>] [--study=<study-uid>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --study | study-uid |  | Study Instance UID from 'dicom:list'. |
| --protocol | string |  | Plans this protocol from 'list' even when another one scores better. |
| --protocols | input-path |  | A JSON file of protocol entries matched instead of the layers, to try a draft. |

### hp:validate (since 5.0)

Runs the matching over a folder of studies without a viewer and reports, per protocol, how many studies it applies to and how its cells matched (exact, preferred, fallback, shared, empty) and, per study, the protocol chosen: the regression test of a protocol library. The protocols are those of every layer, or the entries of --protocols.

Usage: `hp:validate [-h] [--json] --corpus=<input-path> [--protocol=<protocol-id>] [--protocols=<input-path>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --corpus | input-path |  | A folder of DICOM studies, walked recursively. |
| --protocols | input-path |  | A JSON file holding protocol entries (an array, or an object with a 'protocols' array) validated instead of the layers. |
| --protocol | string |  | Only this protocol. |


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

### redact:attest (since 5.0)

Records that a person reviewed the pixels of the selected image or series and found no burned-in identity, so it may be captured or exported without a redaction; --withdraw takes it back. Held until the study is unloaded.

Usage: `redact:attest [-h] [--json] [--withdraw] [--by=<name>] [--pane=<pane-index>] [--reason=<text>] [--scope=<scope>] [--view=<view-id>]`

Needs: pixel attest

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --scope | enum | IMAGE | Where it applies: IMAGE, SERIES. |
| --by | string |  | Who reviewed the pixels; required unless --withdraw. |
| --reason | string |  | What the review was, such as the catalog and the date it was recorded. |
| --withdraw | boolean |  | Takes back the attestation of that scope. |

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

Renders a view as displayed (window/level, annotations, identity masking) into a PNG or JPEG file and returns how image pixels map to the capture; --frame image renders the image alone at its own size, for exact comparisons.

Usage: `view2d:capture [-h] [--force] [--json] [--annotations=<on|off>] [--crop=<canvas-rect>] [--frame=<frame>] [--max-pixels=<n>] --out=<output-path> [--pane=<pane-index>] [--view=<view-id>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --out | output-path |  | File to write; the extension selects PNG or JPEG (.jpg). |
| --force | boolean |  | Replace the file when it exists and was not written by this session. (since 5.0) |
| --crop | canvas-rect |  | Crop to this region of the canvas before scaling. (since 5.0) |
| --max-pixels | integer |  | Downscale so the file has at most this many pixels (default: 2000000 for the canvas, none for the image frame). |
| --frame | enum | canvas | canvas (default): the pane as displayed, its size and zoom; image: one pixel per displayed image pixel, whatever the pane, the zoom and the pan. (since 5.0) |
| --annotations | enum |  | Text, scale, orientation, drawings and cross-lines: on or off (default: as displayed for the canvas, off for the image frame). (since 5.0) |

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

Usage: `view2d:grid [-h] [--json] [--view=<view-id>] [--panes=<n> | --grid=<grid-id> | --best-for=<n>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --panes | integer |  | Applies the best grid for this number of panes. |
| --grid | grid-id |  | Applies this grid, by id from the bare verb. |
| --best-for | integer |  | Returns the best grid for this number of panes without applying it. (since 5.0) |

### view2d:hang (since 5.0)

Hangs series in the cells of a viewer in one call: --grid picks the grid, each --cell puts a series in a cell (an empty value clears it), --tiled tiles a cell, --sync and --select set the synchronization and the selected cell; --file applies a layout saved by 'workspace:save'. The state of the viewer is returned. With no argument the current layout is returned, in the form --file takes.

Usage: `view2d:hang [-h] [--json] [--file=<input-path>] [--grid=<grid-id>] [--select=<cell>] [--sync=<sync-mode>] [--view=<view-id>] [--cell=<cell>=<series-uid>]... [--tiled=<cell>=<cols>x<rows>]...`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --grid | grid-id |  | Grid to apply, by id from 'grid'. |
| --cell | string |  | Series shown in that cell, from 1; an empty value clears the cell. |
| --tiled | string |  | Tiles of that cell; 1x1 returns it to a single image. |
| --sync | enum |  | none, stack or tile, among the modes the viewer offers. |
| --select | integer |  | Cell to select, from 1. |
| --file | input-path |  | A layout written by 'workspace:save' or by this verb without argument. |

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

### view2d:viewport (since 5.0)

Viewport of a pane: --tiled shows its series on a grid of tiles, --stack returns it to one image; the state of the viewer, with its cells, is returned. With no argument the state is returned unchanged.

Usage: `view2d:viewport [-h] [--json] [--pane=<pane-index>] [--view=<view-id>] [--tiled=<cols>x<rows> | --stack]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --tiled | string |  | Shows the series of the pane on this grid of tiles, successive images. (since 5.0) |
| --stack | boolean |  | Returns the pane to a single image. (since 5.0) |

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

Restores a layout captured by 'workspace:save': open views are reused, closed viewers are created again with their series and presentation when the series are still loaded; the shell (explorer and tool slots) follows when the snapshot has one.

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

### workspace:panel (since 5.0)

Shell slots around the workspace: the explorer side (leading) and the tools side (trailing), two stacked slots each, and the central strip. Without option it returns the shell state; --side selects a side, then --slot with --tool or --clear fills or empties a slot, --collapse / --expand hide or show the side, --width and --ratio size it. Tool ids are stable across viewer types; an id the side does not offer now fails and lists the ones it does.

Usage: `workspace:panel [-h] [--clear] [--collapse] [--expand] [--json] [--no-strip] [--ratio=<split-ratio>] [--side=<side>] [--slot=<slot>] [--strip=<tool-id>] [--tool=<tool-id>] [--width=<pixels>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --side | enum |  | Side by role: EXPLORER, TOOLS. |
| --slot | enum |  | Slot of the side: TOP, BOTTOM. |
| --tool | tool-id |  | Opens this tool in --slot of --side, replacing what was there. |
| --clear | boolean |  | Empties --slot of --side. |
| --collapse | boolean |  | Hides both slots of --side (tools remembered). |
| --expand | boolean |  | Shows the remembered tools of --side again. |
| --width | integer |  | Content width of --side, in pixels. |
| --ratio | split-ratio |  | Share of the top slot of --side when both slots are shown. |
| --strip | tool-id |  | Keeps the central strip on this tool (fails when another tool is shown). |
| --no-strip | boolean |  | Empties the central strip. |

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


