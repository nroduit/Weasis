# Weasis commands

Command API 2.29, protocol 2, application development.

## color

Color management of the display: switch, display profile, view conversion.

### color:set

Switches color management or sets the display profile, then prints the status.

Usage: `color:set [-h] [--json] [--enabled=<true|false>] [--display-profile=<icc-file> | --srgb]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --enabled | boolean |  | Convert color images to the display profile. |
| --display-profile | input-path |  | ICC profile file (.icc, .icm) of the display of this workstation. |
| --srgb | boolean |  | No display profile: the display is sRGB. |

### color:status

Whether color management is on, the display profile, and the profile and conversion of a view.

Usage: `color:status [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |


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

### dicom:echo (since 5.0)

C-ECHO to a configured DICOM node or printer, with the TLS of its document: the readiness probe of an archive before a query, a retrieve or a send. The answer gives the round trip; a failure names what failed (an untrusted or expired certificate, no protocol in common, a closed port) instead of a generic connection error.

Usage: `dicom:echo [-h] [--json] [--aet=<aet>] --node=<node> [--timeout=<seconds>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --node | string |  | The node: its id, its description or its AE title, from the DICOM nodes. |
| --aet | string |  | Calling AE title (default: weasis.aet, else the first calling node, else WEASIS_AE). |
| --timeout | integer | 10 | Connect and accept time-out (default: 10). |

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

### Events of dicom:

| Event | Since | Payload | Description |
|---|---|---|---|
| `dicom:loading.progress` (coalesced) | 5.0 | series: series-uid, images: integer | Images arrived in a series; one event per series per tick. |
| `dicom:loading.started` | 5.0 | source: string, series: series-uid | A load, download or export task started. |
| `dicom:loading.stopped` | 5.0 | source: string, series: series-uid, canceled: boolean | A load, download or export task ended. |
| `dicom:object.added` | 5.0 | type: string, sopInstanceUID: string, seriesInstanceUID: series-uid, provenanceKind: string, provenanceSource: string | A non-image object (KO, PR, SR, SEG, RT) is available. |
| `dicom:patient.added` | 5.0 | patientID: patient-handle, patientName: string, patientBirthDate: string | A patient entered the explorer. |
| `dicom:patient.removed` | 5.0 | patientID: patient-handle | A patient and everything under it left the explorer. |
| `dicom:series.added` | 5.0 | id: series-uid, seriesInstanceUID: series-uid, studyInstanceUID: study-uid, modality: string, seriesDescription: string, imageCount: integer | A series entered the explorer; its images may still be loading. |
| `dicom:series.removed` | 5.0 | id: series-uid, studyInstanceUID: study-uid | A series left the explorer. |
| `dicom:study.added` | 5.0 | studyInstanceUID: study-uid, patientID: patient-handle, studyDate: string, studyDescription: string, accessionNumber: string | A study entered the explorer. |
| `dicom:study.closed` | 5.0 | studyInstanceUID: study-uid | No pane of any viewer shows this study anymore. |
| `dicom:study.opened` | 5.0 | studyInstanceUID: study-uid, view: string, series: series-uid | A first pane shows a series of this study. |
| `dicom:study.removed` | 5.0 | studyInstanceUID: study-uid | A study and its series left the explorer. |


## events

The events this instance publishes: the catalog, the recent ones, and the subscribers. 'weasis-cli events' streams them live.

### events:list (since 5.0)

The events this instance may publish, by namespace, with the fields of their payload.

Usage: `events:list [-h] [--json] [--markdown] [--namespace=<namespace>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --namespace | string |  | Only the events of this namespace. (since 5.0) |
| --markdown | boolean |  | A Markdown table, for a reference page. (since 5.0) |

### events:recent (since 5.0)

The last events the bus kept, oldest first: a poll for a caller that cannot hold a stream. Pass the 'lastSeq' of the previous answer as --since to read only what is new.

Usage: `events:recent [-h] [--json] [--filter=<event-glob>] [--last=<n>] [--since=<seq>] [--where=<key=value>]...`

Needs: events

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --since | integer | 0 | Only the events after this sequence number. (since 5.0) |
| --last | integer | 50 | At most this many events, the newest ones (default: 50). (since 5.0) |
| --filter | string |  | Event names to keep, with '*' as a wildcard: 'dicom:*,workspace:view.*'. (since 5.0) |
| --where | string | [] | A payload value an event must carry; repeatable. (since 5.0) |

### events:subscribers (since 5.0)

The active subscriptions: id, owner, filter, delivery, dropped events, and whether the owner component is gone (dangling).

Usage: `events:subscribers [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |


## explorer

The explorer panel: its filter facets, its selection and what it shows. 'dicom:' reads the data, 'explorer:' the view of it.

### explorer:facets (since 5.0)

The values the series filter can take for the selected patient, with the number of its series each value keeps: modalities, kinds of objects referencing the series, sources of images of interest, download states. The menu of the filter button shows the same; lets an agent choose a filter before 'explorer:filter'.

Usage: `explorer:facets [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

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

### explorer:mode (since 5.0)

Sets how the DICOM explorer draws the selected patient: 'thumbnails' (the column of thumbnails) or 'list' (one row per series and per object, studies as group rows). Without argument, prints the current mode.

Usage: `explorer:mode [-h] [--json] [<explorer-mode>]`

| Parameter | Type | Description |
|---|---|---|
| <explorer-mode> | string | thumbnails, list, or auto (forget the choice, let the rules decide). |

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### explorer:reveal (since 5.0)

Scrolls the DICOM explorer to a series and selects it, as a reader pointing at it: selects its patient, unfolds its study in the compact list and brings its thumbnail or row into view. Reports 'visible': false when the filter hides it (the filter is not changed).

Usage: `explorer:reveal [-h] [--json] --series=<series-uid>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list'. |

### explorer:select (since 5.0)

Selects series in the DICOM explorer (and the patient they belong to), as a click would; the next 'dicom:open' without --series opens them. Returns the state.

Usage: `explorer:select [-h] [--json] --series=<series-uid> [--series=<series-uid>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid | [] | Series Instance UID (or series id) from 'dicom:list'; repeatable. |

### explorer:state (since 5.0)

What the DICOM explorer shows: the selected patient, the filter and its counts, the studies with their series (visible or filtered out, download state, selected) and their non-image objects. Identifiers follow the identity policy.

Usage: `explorer:state [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |


## hp

Hanging protocols: the protocols of every layer, the plan the engine computes for a loaded study, its hanging on live viewers, the active hanging of a viewer, and the validation of the protocols over a folder of studies.

### hp:apply (since 5.0)

Hangs a protocol on a loaded study: computes its plan as 'match' does and opens the viewers of one reading step, the 2D cells in one viewer and each MPR or 3D cell in its own. A 2D viewer of the same patient is re-hung unless --new-tab. Returns the cells with the view ids they went to, so 'view2d:' verbs can address them.

Usage: `hp:apply [-h] [--json] [--new-tab] [--protocol=<protocol-id>] [--protocols=<input-path>] [--screen=<screen-role>] [--step=<step>] [--study=<study-uid>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --study | study-uid |  | Study Instance UID from 'dicom:list'. |
| --protocol | string |  | Hangs this protocol from 'list' even when another one scores better. |
| --protocols | input-path |  | A JSON file of protocol entries used instead of the layers, to try a draft. |
| --step | integer |  | The reading step to hang, from 1 (default: the first). |
| --new-tab | boolean |  | Opens new viewers instead of re-hanging the one of the same patient. |
| --screen | screen-role |  | Opens the viewers of the step in a viewer window on the screen of this role (control, diagnostic-1, diagnostic-2, color, portrait, see 'workspace:screens'). |

### hp:compare (since 5.0)

Hangs a study against the priors you name: the protocol (the best comparison one, or --protocol) is matched on --current with --prior as its only priors, and its first step is hung. Every study must be loaded.

Usage: `hp:compare [-h] [--json] --current=<study-uid> [--protocol=<protocol-id>] --prior=<study-uid> [--prior=<study-uid>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --current | study-uid |  | The current study, from 'dicom:list'. |
| --prior | study-uid |  | A prior study of the same patient; repeatable. |
| --protocol | string |  | Hangs this protocol from 'list' even when another one scores better. |

### hp:correct (since 5.0)

Puts another series of the study in a cell of the active hanging: the cell is re-hung with it, the correction is recorded with the rule that would have produced the choice, and --apply writes that rule into the protocol when it is a user one.

Usage: `hp:correct [-h] [--apply] [--json] --cell=<cell> --series=<series-uid> [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --cell | integer |  | The cell to correct, from 1. |
| --series | string |  | Series Instance UID of the study, from 'dicom:list' or 'explain'. |
| --apply | boolean |  | Writes the proposed rule into the protocol (user protocols only). |

### hp:corrections (since 5.0)

The correction log: what users put in the cells instead of what the protocols chose, with the rule each correction proposes; --stats gives the anonymous counts per protocol, cell, display set and proposed rule (no study or series), the figures an administrator or the Viewer Hub collects to see which rules fail most.

Usage: `hp:corrections [-h] [--json] [--stats] [--protocol=<protocol-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --protocol | string |  | Only the corrections of this protocol. |
| --stats | boolean |  | The anonymous statistics instead of the log. |

### hp:delete (since 5.0)

Deletes a protocol of the user layer, one saved with 'save'; a built-in or site protocol cannot be deleted, only disabled.

Usage: `hp:delete [-h] [--json] --id=<protocol-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --id | string |  | Protocol id from 'list'. |

### hp:explain (since 5.0)

Why the cells of the active hanging show what they show: per cell the rules that selected the series, its score and the alternatives; the why of a hanging.

Usage: `hp:explain [-h] [--json] [--cell=<cell>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --cell | integer |  | Only this cell, from 1. |

| Result | Type | Description |
|---|---|---|
| protocol | string | the protocol that hung the viewer |
| step | integer | its step, 1-based |
| reasons | array | why the protocol was chosen, one line each |
| cells | array | per cell: the series, the rules that selected it and the 'evidence' of each rule (feature, value, provenance), the alternatives |

### hp:export (since 5.0)

Writes a protocol of any layer to a file: its JSON entry, or with --dicom a DICOM Hanging Protocol Storage object (also when the file ends with .dcm). The Weasis extensions (feature selectors, viewer types, display by reference, UI profile) travel in a private block of the object, or are dropped with --plain for a reader that must not see them.

Usage: `hp:export [-h] [--dicom] [--json] [--plain] --id=<protocol-id> --out=<output-path>`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --id | string |  | Protocol id from 'list'. |
| --out | output-path |  | Target file. |
| --dicom | boolean |  | Writes a DICOM object instead of JSON. |
| --plain | boolean |  | Leaves the Weasis extensions out. |

### hp:feature (since 5.0)

Sets a derived feature of a loaded series by hand (role, orientation, anatomy, laterality, view, contrast, mrWeighting, ctKernel.family, temporal), as a USER value that outranks every header, table, classifier and heuristic; the next matching uses it. '--by agent' records the caller as 'agent:<origin>', so a correction of an agent stays distinguishable from the radiologist's. In memory for the series, until it is loaded again.

Usage: `hp:feature [-h] [--json] [--by=<who>] --series=<series-uid> --set=<feature=value> [--set=<feature=value>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | string |  | Series Instance UID, from 'dicom:list' or 'features'. |
| --set | string |  | A feature and its value, repeatable: orientation=CORONAL, mrWeighting=T2. |
| --by | string |  | 'user' (default) or 'agent': who states the value. |

| Result | Type | Description |
|---|---|---|
| series | string | the series set |
| by | string | 'user' or 'agent:<origin>', the source recorded |
| features | object | the features after the change |
| provenance | object | the provenance of each derived feature |

### hp:features (since 5.0)

The series features the matching works on (modality, role, orientation, anatomy, laterality, view, contrast, MR weighting, CT kernel and family, thickness, spacing, temporal, images of interest...), per series of a loaded study or of a folder of studies: what a selector can test, and why a series matched or not.

Usage: `hp:features [-h] [--json] [--corpus=<input-path>] [--study=<study-uid>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --study | study-uid |  | Study Instance UID from 'dicom:list'. |
| --corpus | input-path |  | A folder of DICOM studies read without a viewer, walked recursively. |

| Result | Type | Description |
|---|---|---|
| studies | array | per study: its series with 'features', the 'provenance' of each derived feature (kind, source, detail, confidence, version) and the 'contests' of the providers |

### hp:group (since 5.0)

Shows another reading step (presentation group) of the active hanging, or hangs another candidate protocol, on the same viewers.

Usage: `hp:group [-h] [--json] [--next] [--previous] [--index=<step>] [--protocol=<protocol-id>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --next | boolean |  | The next step, the first one again after the last. |
| --previous | boolean |  | The previous step. |
| --index | integer |  | The step to show, from 1. |
| --protocol | string |  | Hangs this candidate of the study instead, from its first step. |

### hp:import (since 5.0)

Reads a protocol file, a DICOM Hanging Protocol Storage object or a JSON entry (one, an array, or an object with 'protocols'), and stores the protocols in the user layer with their level; a protocol of the same id in the built-in or site layer is overridden by it. An object without the Weasis private block imports with its DICOM selectors alone, with a warning.

Usage: `hp:import [-h] [--json] --file=<input-path> [--id=<protocol-id>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --file | input-path |  | Source file. |
| --id | string |  | The id to store a single imported protocol under. |

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

### hp:open (since 5.0)

Opens a study hung by a protocol, the reading workflow in one call: the best protocol (or --protocol) is matched on the study with its priors, the older loaded studies of the patient, or only the ones --prior names, and its first step is hung. The study and its priors must be loaded ('dicom:get' first); the selective download of the hung series is not there yet.

Usage: `hp:open [-h] [--json] [--new-tab] [--protocol=<protocol-id>] [--study=<study-uid>] [--prior=<study-uid>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --study | study-uid |  | Study Instance UID from 'dicom:list'. |
| --protocol | string |  | Hangs this protocol from 'list' even when another one scores better. |
| --prior | study-uid |  | A prior study to compare with; repeatable. Default: every loaded prior. |
| --new-tab | boolean |  | Opens new viewers instead of re-hanging the one of the same patient. |

### hp:save (since 5.0)

Save as protocol: derives a user protocol from the display of a 2D viewer, per cell the smallest set of feature selectors that singles its series out among the study and the presentation of the cell (preset, color map, inversion, sort, tiling, synchronization), and stores it in the user layer; --dry-run only returns the rules and which loaded studies the protocol would apply to.

Usage: `hp:save [-h] [--dry-run] [--json] [--id=<protocol-id>] --name=<text> [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --name | string |  | The protocol name; the id is derived from it unless --id. |
| --id | string |  | The entry id. |
| --dry-run | boolean |  | Derives and reports without storing; matches the loaded studies. |

### hp:sets (since 5.0)

The hanging a viewer is part of: the protocol, the reading step shown of how many, the display sets with their view ids, the candidates of the study.

Usage: `hp:sets [-h] [--json] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |

### hp:show (since 5.0)

A protocol of the layers as its JSON entry: the DICOM part by keyword and the Weasis extensions (feature selectors, viewer types, display by reference).

Usage: `hp:show [-h] [--json] --id=<protocol-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --id | string |  | Protocol id from 'list'. |

### hp:validate (since 5.0)

Runs the matching over a folder of studies without a viewer and reports, per protocol, how many studies it applies to and how its cells matched (exact, preferred, fallback, shared, empty) and, per study, the protocol chosen: the regression test of a protocol library. The protocols are those of every layer, or the entries of --protocols.

Usage: `hp:validate [-h] [--json] [--corpus=<input-path>] [--expected=<input-path>] [--file=<input-path>] [--protocol=<protocol-id>] [--protocols=<input-path>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --corpus | input-path |  | A folder of DICOM studies, walked recursively. |
| --file | input-path |  | A JSON file of protocol entries checked for rule errors without any study: image set numbers unique and referenced, selectors known, one time-based item per set, grids the viewer offers. |
| --protocols | input-path |  | A JSON file holding protocol entries (an array, or an object with a 'protocols' array) validated instead of the layers. |
| --protocol | string |  | Only this protocol. |
| --expected | input-path |  | A JSON file of expected hangings, {"studies": {"<study-uid>": {"protocol": <id>, "cells": {"1": <series-uid>}}}}: the report says per study whether the chosen protocol and the cells of its first step are the expected ones. |


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


## mpr

Multiplanar reformats of a series built as series of the study (a coronal or sagittal stack from an axial one), scrolled, windowed, measured and sent like any series.

### mpr:delete (since 5.0)

Removes a reformat from the explorer and its slices from the cache.

Usage: `mpr:delete [-h] [--json] --series=<series-uid>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | A reformat series, from 'mpr:list'. |

### mpr:list (since 5.0)

The reformats built in this session.

Usage: `mpr:list [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### mpr:prefetch (since 5.0)

Builds the volume of a series in the background, after the requests under way, so a later MPR, rendering or reformat finds it ready; nothing when the slices need a rectification question.

Usage: `mpr:prefetch [-h] [--json] [--series=<series-uid>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | The series, from 'dicom:list' (default: the selected pane's). |

### mpr:reformat (since 5.0)

Builds the reformat of a series in a standard plane as a new series of the study, on an isotropic grid at the finest spacing of the source, and opens it in the selected pane. The same series and plane always give the same result.

Usage: `mpr:reformat [-h] [--json] [--no-open] --plane=<plane> [--series=<series-uid>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | The series reformatted, from 'dicom:list' (default: the selected pane's). |
| --plane | string |  | AXIAL, CORONAL or SAGITTAL; not the plane the series already lies in. |
| --no-open | boolean |  | Build the series without opening it. |

### mpr:volumes (since 5.0)

The volumes the service holds for the MPR, the volume rendering and the derived series: built once per series and kept, within a budget, for the next consumer.

Usage: `mpr:volumes [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |


## print

Print a viewer layout: destinations, page, preview rendering, sending.

### print:destinations

Lists where a page can go: the system printers (the default one first) and the DICOM printers, with their papers and resolution.

Usage: `print:destinations [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### print:page

Builds a print job from a viewer, the selected one by default: its whole layout, or one pane. Returns the job handle, the page and the cell figures.

Usage: `print:page [-h] [--json] [--series] [--annotations=<on|off>] [--background=<white|black>] [--frames=<first>-<last>] [--grid=<cols>x<rows>] [--header=<on|off>] [--masking=<profile-id>] [--orientation=<portrait|landscape>] [--pane=<pane-index>] [--paper=<paper>] [--position=<center|top-left>] [--step=<n>] [--text=<auto|small|normal|large>] [--to=<destination>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Print only this pane of the layout, counting from 1. |
| --to | string |  | Destination id or title from 'print:destinations': its papers and printable area apply (default: default margins on the paper of the locale). |
| --paper | string |  | Paper id, such as A4, Letter or 14x17in (default: the destination's). |
| --orientation | enum |  | Page orientation (default: landscape when the layout is wider than tall). |
| --text | enum | auto | Text size of annotations and labels on paper (default: auto, 8 pt or 6 pt). |
| --background | enum |  | Page background (default: the destination's, white for paper). |
| --annotations | enum | on | Print the overlay and the graphics (default: on). |
| --position | enum | center | Where an image sits in its cell (default: center). |
| --masking | string |  | Identity masking profile applied to the page (default: none). |
| --header | enum | off | Header line with patient, study and page number above the cells (default: off). (since 5.0) |
| --series | boolean |  | Print the frames of the series of the pane (--pane, else the selected one) on pages of --grid cells, instead of the layout as shown. (since 5.0) |
| --grid | string | 4x3 | Cells per page with --series (default: 4x3). (since 5.0) |
| --step | integer | 1 | With --series, one frame out of n (default: 1). (since 5.0) |
| --frames | string |  | With --series, the range of frames, counting from 1 (default: all). (since 5.0) |

### print:render

Renders the page of a job to a PNG file at a resolution, as the printer would receive it: the preview of an agent and the test surface of a layout.

Usage: `print:render [-h] [--gray] [--json] [--dpi=<dpi>] --job=<job-id> --out=<file.png> [--page=<n>] [--to=<destination>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --job | string |  | Job from 'print:page'. |
| --to | string |  | Render for this destination: its printable area and resolution apply. |
| --dpi | integer |  | Resolution of the raster (default: the destination's, else 150). |
| --gray | boolean |  | Render in 8-bit gray, as a gray film printer receives it. |
| --page | integer | 1 | The page to render, counting from 1 (default: 1). (since 5.0) |
| --out | output-path |  | The PNG file to write. |

### print:send

Prints a job on a destination and releases it. Sending patient images to a device is closed to callers unless the network.write policy of this instance allows it, as for dicom:send.

Usage: `print:send [-h] [--json] [--copies=<n>] [--dpi=<dpi>] --job=<job-id> [--out=<file.pdf>] --to=<destination>`

Needs: file write, send dicom

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --job | string |  | Job from 'print:page'. |
| --to | string |  | Destination id or title from 'print:destinations'. |
| --copies | integer | 1 | Number of copies (default: 1). |
| --out | output-path |  | The file to write, for the PDF file destination (file:pdf). (since 5.0) |
| --dpi | integer |  | Resolution of the pages written to the file (default: 300). (since 5.0) |


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

Usage: `script:run [-h] [--brief] [--json] [--keep] [--keep-going] [--rollback] [--delay=<ms>] --file=<input-path> [--from=<n>] [--report=<output-path>] [--timeout=<s>] [--to=<n>] [--set=<name=value>]...`

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
| --keep | boolean |  | Leave the display as the failing step left it, instead of restoring the checkpoint taken before the first step (the default for a remote caller). (since 5.0) |
| --rollback | boolean |  | Restore the checkpoint taken before the first step when a step fails (the default for a remote caller; a script of the launch arguments keeps the display). (since 5.0) |
| --report | output-path |  | Write a JUnit XML report of the run to this file. (since 5.0) |


## state

Reading states: keep the display as a session (windows, viewers, every pane's series and presentation, the shell, the protocol that hung it), list, restore and delete the sessions of the user.

### state:checkpoint (since 5.0)

Takes a checkpoint of the display (layout, views, graphics of the shown images, loaded studies, selection, hanging) and returns its id: the point a 'rollback' puts the display back to. Kept in memory, bounded per instance; 'keep' makes it a session.

Usage: `state:checkpoint [-h] [--json] [--label=<text>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --label | string |  | What the checkpoint precedes, for the list. |

| Result | Type | Description |
|---|---|---|
| checkpoint | string | checkpoint id |
| seq | integer | event sequence number at the boundary |
| at | string | when it was taken, ISO-8601 |
| label | string | what the checkpoint precedes |
| origin | string | who asked for the work it precedes |
| views | integer | views it holds |
| graphics | integer | images with graphics it holds |
| studies | integer | studies loaded at the boundary |
| documents | integer | documents written since |
| selectedView | string | the view selected at the boundary |
| protocol | string | the protocol hung at the boundary |
| step | integer | its step, 1-based |
| automatic | boolean | taken by the application at a boundary |
| taken | boolean | always true |

### state:checkpoints (since 5.0)

The checkpoints of this instance, newest first, with what each one holds and what changed since the latest.

Usage: `state:checkpoints [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| checkpoints | array | the checkpoints, newest first, as 'checkpoint' prints them |
| changedSinceLatest | object | the diff from the newest one to the display now |

### state:delete (since 5.0)

Deletes a session of the user.

Usage: `state:delete [-h] [--json] --session=<session-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --session | string |  | Session id from 'list'. |

| Result | Type | Description |
|---|---|---|
| session | string | session id |
| deleted | boolean | always true |

### state:diff (since 5.0)

What changed between two checkpoints, or between a checkpoint and the display now: views added, removed and changed, graphics, studies loaded and closed, the selection and the shell.

Usage: `state:diff [-h] [--json] --from=<checkpoint-id> [--to=<checkpoint-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --from | string |  | The older checkpoint, from 'checkpoints'. |
| --to | string |  | The newer checkpoint (default: the display now). |

| Result | Type | Description |
|---|---|---|
| from | string | the older checkpoint id |
| to | string | the newer checkpoint id, or 'now' |
| diff | object | empty, viewsAdded, viewsRemoved, viewsChanged, graphicsChanged, studiesLoaded, studiesClosed, selectionChanged, shellChanged, documentsChanged |

### state:export (since 5.0)

Writes a session to a directory: its JSON (the sidecar as 'state:keep' stores it), or with --dicom a Basic Structured Display object laying out the panes of the 2D viewers of the main window as image boxes, each with a Grayscale Softcopy Presentation State carrying the pane's window, rotation, flip, inversion, magnification and graphics; the studies of the session must be loaded. What the object cannot carry (MPR and 3D views, other windows, hidden tabs, blends) is listed under 'warnings'.

Usage: `state:export [-h] [--dicom] [--json] --out=<output-path> [--session=<session-id>] [--study=<study-uid>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --session | string |  | Session id from 'list'. |
| --study | study-uid |  | The most recent session showing this study instead of an id. |
| --out | output-path |  | Target directory, created when missing. |
| --dicom | boolean |  | Writes DICOM objects instead of the JSON. |

| Result | Type | Description |
|---|---|---|
| session | string | session id |
| files | array | the files written |
| boxes | integer | with --dicom: image boxes of the structured display |
| warnings | array | with --dicom: what the object cannot carry |

### state:keep (since 5.0)

Keep this display: saves the workspace as a session of the user (windows, viewers, the series and presentation of every pane, the shell, the protocol and step that hung it) and returns its id. The session holds study and series identifiers, never credentials.

Usage: `state:keep [-h] [--json] [--pin] [--from-checkpoint=<checkpoint-id>] [--label=<text>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --label | string |  | A label for the list. |
| --pin | boolean |  | Keeps the session out of the retention by number. |
| --from-checkpoint | string |  | Promotes a checkpoint from 'checkpoints' to a session instead of the display. |

| Result | Type | Description |
|---|---|---|
| session | string | session id |
| pinned | boolean | kept out of the retention by number |
| created | string | when it was kept, ISO-8601 |
| updated | string | when it was last used, ISO-8601 |
| label | string | the label given, when there is one |
| studies | array | study UIDs of the session |
| archives | array | the archives the studies come from |
| protocol | string | the protocol that hung it, when one did |
| step | integer | the step of the protocol, 1-based |
| kept | boolean | always true |

### state:list (since 5.0)

The sessions of the user, the most recently used first.

Usage: `state:list [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| sessions | array | the sessions, most recently used first, as 'keep' prints them |

### state:restore (since 5.0)

Restores a session when its studies are loaded: open views are reused, closed viewers are created again with their series and presentation, the shell follows. A study of the session that is not loaded is reported; load it first.

Usage: `state:restore [-h] [--json] [--session=<session-id>] [--study=<study-uid>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --session | string |  | Session id from 'list'. |
| --study | study-uid |  | The most recent session showing this study instead of an id. |

| Result | Type | Description |
|---|---|---|
| session | string | session id |
| pinned | boolean | kept out of the retention by number |
| created | string | when it was kept, ISO-8601 |
| updated | string | when it was last used, ISO-8601 |
| label | string | the label given, when there is one |
| studies | array | study UIDs of the session |
| archives | array | the archives the studies come from |
| protocol | string | the protocol that hung it, when one did |
| step | integer | the step of the protocol, 1-based |
| restored | boolean | always true |
| views | array | ids of the views shown after the restore |

### state:rollback (since 5.0)

Puts the display back to the latest checkpoint, or to --to: the layout, the views and their presentation, the graphics of the shown images, the selection and the hanging. A study closed since is reported, not reloaded; studies loaded since stay open unless --close-new. What a checkpoint never holds (what left the application) is listed under 'notCaptured'.

Usage: `state:rollback [-h] [--close-new] [--dry-run] [--json] [--to=<checkpoint-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --to | string |  | Checkpoint id from 'checkpoints' (default: the latest). |
| --close-new | boolean |  | Close the studies loaded since the checkpoint. |
| --dry-run | boolean |  | Only say what differs between the checkpoint and the display. |

| Result | Type | Description |
|---|---|---|
| checkpoint | string | checkpoint id |
| seq | integer | event sequence number at the boundary |
| at | string | when it was taken, ISO-8601 |
| label | string | what the checkpoint precedes |
| origin | string | who asked for the work it precedes |
| views | integer | views it holds |
| graphics | integer | images with graphics it holds |
| studies | integer | studies loaded at the boundary |
| documents | integer | documents written since |
| selectedView | string | the view selected at the boundary |
| protocol | string | the protocol hung at the boundary |
| step | integer | its step, 1-based |
| automatic | boolean | taken by the application at a boundary |
| restored | boolean | true once the display is back |
| views | array | ids of the views shown after the rollback |
| notRestored | array | what stayed as it was, one line each |
| closedStudies | array | study UIDs closed by --close-new |
| documents | array | documents put back to their version |
| notCaptured | array | what a checkpoint never holds |
| dryRun | boolean | with --dry-run: nothing was changed |
| diff | object | with --dry-run: what differs, as 'diff' prints it |

### Events of state:

| Event | Since | Payload | Description |
|---|---|---|---|
| `state:checkpoint.taken` | 5.0 | checkpoint: string, checkpointSeq: integer, label: string | A checkpoint of the display was taken. |
| `state:rollback.done` | 5.0 | checkpoint: string, checkpointSeq: integer, views: integer, notRestored: integer, documents: integer | The display was put back to a checkpoint. |


## subtraction

Derived series subtracting one series of a study from another (post-contrast minus pre-contrast), scrolled, windowed, measured and sent like any series.

### subtraction:create (since 5.0)

Builds the series minus the mask as a new series of the study, pixel by pixel on the same grid (A - factor*B + offset, real values), and opens it in the selected pane. The same operands and options always give the same series.

Usage: `subtraction:create [-h] [--absolute] [--all-phases] [--json] [--mip] [--motion-correction] [--no-open] [--signed] [--normalize[=<region>]] [--factor=<number>] [--mask=<series-uid>] [--mask-frames=<frames>] [--mask-phase=<phase-index>] [--offset=<number>] [--phase=<phase-index>] [--series=<series-uid>] [--shift=<rows,columns>] [--tid-offset=<count>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | The series subtracted from, from 'dicom:list' (default: the series of the selected pane). |
| --mask | series-uid |  | The series subtracted, from 'dicom:list' (default: the series of the study without contrast agent, else the earliest compatible one). |
| --phase | integer |  | Phase of a dynamic series to subtract from, from 'subtraction:phases' (default: every slice of the series). |
| --mask-phase | integer |  | Phase of the mask series to subtract (default: the first phase when the mask is the series itself). |
| --all-phases | boolean |  | Build one series per phase of the series minus the mask phase; the first one opens. |
| --mip | boolean |  | Open the result as a maximum intensity projection through all its slices. |
| --factor | number |  | Weight of the mask: 1 subtracts it fully, below 1 keeps part of the anatomy (default: 1). |
| --offset | number |  | Value added to every difference (default: 0). |
| --absolute | boolean |  | Store the absolute difference. |
| --signed | boolean |  | Open with the signed window, negative differences visible, instead of black below zero. |
| --mask-frames | string |  | DSA: mask frames of the run itself, from 1 ('3', '2-4' averaged, '1,3,5'); the series is an X-ray run (default for a run: the frames its DICOM names, else frame 1). |
| --tid-offset | integer |  | DSA: time-interval mask, each frame minus the frame that many before it. |
| --shift | string |  | DSA: sub-pixel shift of the mask in pixels, down and right when positive. |
| --motion-correction | boolean |  | Move the mask of each slice onto it by the translation phase correlation measures (default: what the pane previews, else off). |
| --normalize | string |  | Weight the mask so that its mean equals the one of the series, over the whole slices or over a pixel rectangle 'x,y,width,height' of every slice: the dual-tracer normalization of nuclear medicine; overrides --factor. |
| --no-open | boolean |  | Build the series without opening it. |

### subtraction:delete (since 5.0)

Removes a subtraction series from the explorer and its slices from the cache.

Usage: `subtraction:delete [-h] [--json] --series=<series-uid>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | A subtraction series, from 'subtraction:list'. |

### subtraction:info (since 5.0)

What a subtraction series was computed from and how.

Usage: `subtraction:info [-h] [--json] --series=<series-uid>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | A subtraction series, from 'subtraction:list'. |

### subtraction:list (since 5.0)

The subtraction series built in this session.

Usage: `subtraction:list [-h] [--json] [--study=<study-uid>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --study | study-uid |  | Keep the series of one study, from 'dicom:list'. |

### subtraction:mask (since 5.0)

Sets the mask of the X-ray run a pane shows, taken from its own frames, and previews the subtraction: one frame, several frames averaged, or a time-interval offset. The state of the pane comes back with the mask under 'subtraction'.

Usage: `subtraction:mask [-h] [--json] [--frames=<frames>] [--pane=<pane-index>] [--tid-offset=<count>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --frames | string |  | Mask frame numbers from 1: one ('3'), a range averaged ('2-4') or a list ('1,3,5'). |
| --tid-offset | integer |  | Time-interval mask: each frame minus the frame that many positions before it; negative for the frame after it. |

### subtraction:phases (since 5.0)

The phases of a dynamic series stored as one series (temporal position, trigger time, or repeated slice positions): what --phase and --mask-phase count.

Usage: `subtraction:phases [-h] [--json] [--series=<series-uid>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | A series from 'dicom:list' (default: the series of the selected pane). |

### subtraction:preview (since 5.0)

Shows the series of a pane minus a mask live, slice by slice, before any series is built: a call starts the preview (with the proposed mask unless --mask), --mask and --factor change it, --off puts the plain series back. The state of the pane comes back with the preview under 'subtraction'.

Usage: `subtraction:preview [-h] [--json] [--off] [--motion-correction[=<on>]] [--factor=<number>] [--mask=<series-uid>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --mask | series-uid |  | The series subtracted, from 'dicom:list' (default: the series of the study without contrast agent, else the earliest compatible one). |
| --factor | number |  | Weight of the mask, 1 subtracts it fully (default: 1). |
| --motion-correction | boolean |  | Moves the mask of each slice onto it by the translation phase correlation measures (true when given without a value; false switches it off). |
| --off | boolean |  | Leaves the preview, the plain series stays shown. |

### subtraction:shift (since 5.0)

Moves the mask of the X-ray run a pane shows by a sub-pixel shift, to correct the patient's motion between the mask and the contrast frames, and previews the subtraction. Positive values move the mask down and to the right.

Usage: `subtraction:shift [-h] [--json] [--columns=<pixels>] [--pane=<pane-index>] [--rows=<pixels>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --rows | number |  | Vertical shift of the mask in pixels (default: unchanged). |
| --columns | number |  | Horizontal shift of the mask in pixels (default: unchanged). |


## ui

The workspace around the viewers: the UI profiles (shell slots, toolbars, menus, mouse bindings, measurement profile) that a hanging protocol or a reading step lays over it, the overflow of what the selected view hides, and the catalog of every action of the workspace (menu entries, toolbar buttons, tool panels). Profile ids come from 'ui:profiles', action ids from 'ui:actions'.

### ui:action (since 5.0)

Runs one action of 'ui:actions' by id, as its menu entry, button or stripe click would, and returns the active profile, the shell and the toolbar row after it.

Usage: `ui:action [-h] [--json] --id=<action-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --id | string |  | From 'actions'. |

### ui:actions (since 5.0)

The actions of the workspace as the command palette lists them: the menu entries, the toolbar buttons and the tool panels of the selected viewer, with a stable id, the group, the shortcut and whether the selected view shows them ('relevant'; a hidden one still runs). In text a hidden action is marked with '-'.

Usage: `ui:actions [-h] [--json] [--relevant] [--filter=<text>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --filter | string |  | Keeps the actions whose label, group or id contain every word, best first. |
| --relevant | boolean |  | Keeps the actions the selected view shows. |

### ui:context (since 5.0)

The context of the selected view (its facts), the UI profile in force with its entry, the shell and the toolbar row, what the view hides, and the actions the view shows with their action command when they have one; what an agent needs to pick the next verb.

Usage: `ui:context [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### ui:profile (since 5.0)

One UI profile as its JSON entry; --apply lays it over the workspace of the selected viewer until the next selection asks for another, --clear puts the user's own settings back, --delete removes the entry of the user layer. After a change the active profile, the shell and the toolbar row are returned.

Usage: `ui:profile [-h] [--apply] [--clear] [--delete] [--json] [--id=<ui-profile-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --id | string |  | Profile id from 'profiles'. |
| --apply | boolean |  | Lays --id over the workspace now. |
| --clear | boolean |  | Releases the active profile. |
| --delete | boolean |  | Removes --id from the user layer. |

### ui:profiles (since 5.0)

The UI profiles of every layer (built-in, site, user) with the one in force, and the shell and toolbar row as they are now.

Usage: `ui:profiles [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### ui:reveal (since 5.0)

Shows again, for the selected view only, a toolbar or a tool panel that the view's context or the UI profile hides (the overflow 'More tools...' of the toolbar row); the next view hides it again. The hidden ids are under 'hidden' in the state the verbs of this namespace return.

Usage: `ui:reveal [-h] [--json] [--tool=<tool-id>] [--toolbar=<toolbar-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --toolbar | string |  | Toolbar id from 'hidden.toolbars'. |
| --tool | string |  | Tool id from 'hidden.tools'. |


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

### view2d:fusion (since 5.0)

Blends another series of the study over the one a pane shows (PET over CT, SPECT over MR...): --series starts or replaces the overlay, --color-map and --opacity change the blend, --off removes it; the display state returns the blend under 'blend'. With no argument the state is returned unchanged.

Usage: `view2d:fusion [-h] [--json] [--off] [--color-map=<color-map-id>] [--opacity=<0-1>] [--pane=<pane-index>] [--series=<series-uid>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --series | series-uid |  | The overlay series, from 'dicom:list'; it must share the frame of reference. |
| --color-map | string |  | Color map of the overlay, from 'color:maps'. |
| --opacity | number |  | Opacity of the overlay, 0 transparent to 1 opaque. |
| --off | boolean |  | Removes the overlay. |

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

Usage: `view2d:grid [-h] [--json] [--view=<view-id>] [--grid=<grid-id> | --best-for=<n> | --panes=<n>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --grid | grid-id |  | Applies this grid, by id from the bare verb. |
| --best-for | integer |  | Returns the best grid for this number of panes without applying it. (since 5.0) |
| --panes | integer |  | Applies the best grid for this number of panes. |

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

### view2d:redo (since 5.0)

Redoes the last undone change of the drawings of a pane.

Usage: `view2d:redo [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |

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

### view2d:sort (since 5.0)

Orders the images of the stack a pane shows, by a sort key and a direction, and returns the display state ('sort', 'inverseStack'); with no argument the state is returned unchanged. 'slice-position' orders along the positive patient axis whatever the acquisition direction (feet to head for an axial stack, right to left, front to back), so --inverse true reads head to feet.

Usage: `view2d:sort [-h] [--json] [--inverse=<true|false>] [--key=<sort-key>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --key | string |  | instance, slice-position, slice-location, content-time, acquisition-time, b-value. |
| --inverse | boolean |  | Reads the stack backwards. |

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

### view2d:undo (since 5.0)

Undoes the last change of the drawings of a pane (a graphic added, moved, resized, deleted or pasted, by the mouse or by a command) and returns whether something was undone and what remains to undo or redo. Closing a view or changing its series clears the history.

Usage: `view2d:undo [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |

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

### Events of view2d:

| Event | Since | Payload | Description |
|---|---|---|---|
| `view2d:image.changed` (coalesced) | 5.0 | view: view-id, series: string, imageIndex: integer | The selected pane of a viewer shows another image. |
| `view2d:series.changed` | 5.0 | view: view-id, pane: integer, series: string | A pane of a viewer shows another series, or none; whatever opened it. |
| `view2d:windowLevel.changed` (coalesced) | 5.0 | view: view-id, window: number, level: number | Window or level of the selected pane was set. |
| `view2d:windowLevel.changing` (coalesced) | 5.0 | view: view-id, window: number, level: number | Window or level of the selected pane moves under a drag; local subscribers only. |


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

Usage: `weasis:info [-h] [--integrity] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --integrity | boolean |  | The integrity of the configuration and the plugins: the level reached (signed, digest, none), the signing key, the entries verified and the failures. (since 5.0) |

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

### Events of weasis:

| Event | Since | Payload | Description |
|---|---|---|---|
| `weasis:heartbeat` | 5.0 |  | Sent on an idle stream; seq is the last sequence number. |
| `weasis:ready` | 5.0 | version: string, apiVersion: string, profile: string | The plugins are started and the commands answer. |
| `weasis:shutdown` | 5.0 |  | The application is stopping. |
| `weasis:subscriber.dropped` | 5.0 | subscriber: string, dropped: integer, detached: boolean | This subscriber fell behind: the oldest pending events were dropped; one that loses 6144 events without catching up is detached. |
| `weasis:ui.modal` | 5.0 | state: string, title: string | A modal dialog opened or closed. |
| `weasis:user.active` (coalesced) | 5.0 | window: integer | The user moved the mouse or typed. |


## workspace

Layout of the viewer workspace: windows, tab groups and views. View ids come from 'workspace:list'; verbs that change the layout return the new state with --json.

### workspace:close

Closes a view (fails when the view is not closable).

Usage: `workspace:close [-h] [--json] --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |

### workspace:context (since 5.0)

What is displayed and why, as the tools of the plugins see it: the type of the selected viewer, its selected series and the facts the plugins contribute about them (the hanging protocol and its step, the series features). An agent reads it to know which tools and verbs apply.

Usage: `workspace:context [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

### workspace:detach

Floats a view in its own window: a viewer window, which shows the toolbar row while it is the active one. Without option the window opens near the main window; --screen fills the usable area of the screen of that role (see 'screens'), --bounds gives explicit pixels.

Usage: `workspace:detach [-h] [--json] [--bounds=<screen-rect>] [--screen=<screen-role>] --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |
| --bounds | screen-rect |  | Screen bounds of the new window, as x,y,width,height. |
| --screen | screen-role |  | Screen by role: control, diagnostic-1, diagnostic-2, color, portrait. |

### workspace:fullscreen (since 5.0)

Puts a window on the full bounds of its monitor without decorations: the window of --view, else the active window. --off ends it. A window state, kept by 'workspace:save' and restored by 'workspace:load'.

Usage: `workspace:fullscreen [-h] [--canvas] [--json] [--off] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list' whose window is meant (default: the active one). |
| --off | boolean |  | Ends the fullscreen state of that window. |
| --canvas | boolean |  | The selected pane of the view alone fills the viewer, the window and the monitor (what F11 and a double click do); --off ends it. |

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

### workspace:move (since 5.0)

Moves the window of a view to the screen of a role (its usable area, see 'screens'); the main window moves too. Ends the fullscreen state of that window.

Usage: `workspace:move [-h] [--json] --screen=<screen-role> --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |
| --screen | screen-role |  | Screen by role: control, diagnostic-1, diagnostic-2, color, portrait. |

### workspace:panel (since 5.0)

Shell slots around the workspace: the explorer side (leading) and the tools side (trailing), two stacked slots each, and the central strip. Without option it returns the shell state; --side selects a side, then --slot with --tool or --clear fills or empties a slot, --collapse / --expand hide or show the side, --width and --ratio size it. Tool ids are stable across viewer types; an id the side does not offer now fails and lists the ones it does.

Usage: `workspace:panel [-h] [--as-view] [--clear] [--collapse] [--expand] [--hide] [--json] [--no-strip] [--show] [--ratio=<split-ratio>] [--side=<side>] [--slot=<slot>] [--strip=<tool-id>] [--tool=<tool-id>] [--width=<pixels>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --side | enum |  | Side by role: EXPLORER, TOOLS. |
| --slot | enum |  | Slot of the side: TOP, BOTTOM. |
| --tool | tool-id |  | Opens this tool in --slot of --side, replacing what was there. |
| --clear | boolean |  | Empties --slot of --side. |
| --hide | boolean |  | Hides --tool of --side for good: the user's choice, kept in the setting weasis.tool.<id>.visible (see config:setting), until --show. |
| --show | boolean |  | Shows --tool of --side again after --hide. |
| --as-view | boolean |  | Shows --tool of --side as a tab of the active window instead of a slot, where it gets width and floats like any view; --tool with --slot brings it back. |
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

### workspace:screens (since 5.0)

The screens of the workstation by role (control, diagnostic-1, diagnostic-2, color, portrait) with the monitor each one resolves to: the main window's screen is the control screen, the others are derived left to right, and a role the user gave a monitor on the Screens preference page wins. A role without a monitor of its own resolves to the nearest one and is reported as not fulfilled. --id with --role gives a monitor a role, --id with --auto takes it back.

Usage: `workspace:screens [-h] [--auto] [--json] [--id=<screen-id>] [--role=<screen-role>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --id | string |  | A screen id from the listing, to give it a role or take it back. |
| --role | screen-role |  | The role of --id: control, diagnostic-1, diagnostic-2, color, portrait. |
| --auto | boolean |  | Clears the role of --id; it is derived again. |

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

### Events of workspace:

| Event | Since | Payload | Description |
|---|---|---|---|
| `workspace:layout.changed` | 5.0 | view: view-id, layout: string | A viewer changed its pane layout. |
| `workspace:panel.changed` | 5.0 | panel: string | The toolbars or the tool panels were rebuilt for the selected viewer. |
| `workspace:view.closed` | 5.0 | view: view-id, window: integer | A view left the workspace. |
| `workspace:view.opened` | 5.0 | view: view-id, type: string, window: integer | A view was docked into a window. |
| `workspace:view.selected` (coalesced) | 5.0 | view: view-id, type: string, window: integer | A view became the application's selection. |
| `workspace:window.activated` (coalesced) | 5.0 | window: integer | The active window changed, once it stayed so for 100 ms; from the engine, not AWT. |
| `workspace:window.closed` | 5.0 | window: integer, detached: boolean | A floating viewer window closed. |
| `workspace:window.opened` | 5.0 | window: integer, detached: boolean | A window hosting a workspace appeared. |


## wsi

Whole slides (modality SM) shown in the slide viewer: the pyramid and the calibration of a slide, and the place and magnification its view shows. Base level pixels are the image coordinates of the 'view2d:' verbs on a slide.

### wsi:annotations (since 5.0)

The bulk annotation groups of the slide of a pane (DICOM Microscopy Bulk Simple Annotations of the study: nuclei, cells, regions), each with its label, graphic type, count, colour and whether it is drawn; --group with --show or --hide draws or hides groups (all of them without --group). The groups come back after the change.

Usage: `wsi:annotations [-h] [--hide] [--json] [--show] [--pane=<pane-index>] [--view=<view-id>] [--group=<group-id>[,<group-id>...]]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --group | string |  | Groups by their id from the list, comma separated (default: every group). |
| --show | boolean |  | Draw the groups. |
| --hide | boolean |  | Hide the groups. |

### wsi:export (since 5.0)

Writes the measurements and drawings of the slide of a pane as a DICOM Structured Report (TID 1500 Measurement Report, one planar region per graphic on the base level of the slide, lengths and areas in micrometers), a file that 'dicom:load' brings back as regions over the slide and that other viewers read.

Usage: `wsi:export [-h] [--force] [--json] --out=<output-path> [--pane=<pane-index>] [--view=<view-id>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --out | output-path |  | The DICOM file to write (.dcm). |
| --force | boolean |  | Replace the file when it exists and was not written by this session. |

### wsi:goto (since 5.0)

Shows a place of the slide: --at centres the view on a base level pixel, --zoom sets the scale (1: one base pixel per canvas pixel) or --objective the magnification shown (20 for 20x), --focal-plane and --optical-path pick the plane of a Z-stack and the channel of a multi-path slide; --complete answers once every tile of the view is loaded, so that a capture is sharp. With no argument the display comes back unchanged.

Usage: `wsi:goto [-h] [--complete] [--json] [--at=<image-point>] [--focal-plane=<index>] [--objective=<number>] [--optical-path=<optical-path-id>] [--pane=<pane-index>] [--view=<view-id>] [--zoom=<magnification>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |
| --at | image-point |  | Base level pixel x,y put at the centre of the view. |
| --zoom | magnification |  | Scale of the view, 1 being one base level pixel per canvas pixel. |
| --objective | number |  | Magnification shown, as an objective power: 20 shows the slide as a 20x objective would (needs the objective power or the pixel spacing of the slide). |
| --focal-plane | integer |  | Focal plane of a Z-stack shown, from 1 ('focalPlanes' of 'wsi:info'). |
| --optical-path | string |  | Optical path shown, by its identifier ('opticalPaths' of 'wsi:info'). |
| --complete | boolean |  | Answer once every tile of the view is loaded, after 10 s at the latest. |

### wsi:info (since 5.0)

Describes the slide of a pane: its size in base level pixels, pixel spacing, objective magnification, pyramid levels, focal planes, optical paths and associated images, and what the pane shows: zoom, magnification, centre, viewport, level and tiles.

Usage: `wsi:info [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | Viewer id from 'workspace:list' (default: the selected viewer). |
| --pane | pane-index |  | Pane index in the viewer layout, from 1 (default: the selected pane). |


## Events

What the instance publishes on its event bus; `weasis-cli events` streams them, `events:recent` polls them.

### command:

| Event | Since | Payload | Description |
|---|---|---|---|
| `command:finished` | 5.0 | command: string, status: integer, durationMs: integer | A command of a remote caller finished. |

### dicom:

| Event | Since | Payload | Description |
|---|---|---|---|
| `dicom:loading.progress` (coalesced) | 5.0 | series: series-uid, images: integer | Images arrived in a series; one event per series per tick. |
| `dicom:loading.started` | 5.0 | source: string, series: series-uid | A load, download or export task started. |
| `dicom:loading.stopped` | 5.0 | source: string, series: series-uid, canceled: boolean | A load, download or export task ended. |
| `dicom:object.added` | 5.0 | type: string, sopInstanceUID: string, seriesInstanceUID: series-uid, provenanceKind: string, provenanceSource: string | A non-image object (KO, PR, SR, SEG, RT) is available. |
| `dicom:patient.added` | 5.0 | patientID: patient-handle, patientName: string, patientBirthDate: string | A patient entered the explorer. |
| `dicom:patient.removed` | 5.0 | patientID: patient-handle | A patient and everything under it left the explorer. |
| `dicom:series.added` | 5.0 | id: series-uid, seriesInstanceUID: series-uid, studyInstanceUID: study-uid, modality: string, seriesDescription: string, imageCount: integer | A series entered the explorer; its images may still be loading. |
| `dicom:series.removed` | 5.0 | id: series-uid, studyInstanceUID: study-uid | A series left the explorer. |
| `dicom:study.added` | 5.0 | studyInstanceUID: study-uid, patientID: patient-handle, studyDate: string, studyDescription: string, accessionNumber: string | A study entered the explorer. |
| `dicom:study.closed` | 5.0 | studyInstanceUID: study-uid | No pane of any viewer shows this study anymore. |
| `dicom:study.opened` | 5.0 | studyInstanceUID: study-uid, view: string, series: series-uid | A first pane shows a series of this study. |
| `dicom:study.removed` | 5.0 | studyInstanceUID: study-uid | A study and its series left the explorer. |

### graphic:

| Event | Since | Payload | Description |
|---|---|---|---|
| `graphic:added` | 5.0 | graphic: graphic-id, tool: string, layer: string, model: string | A graphic was drawn or loaded. |
| `graphic:changed` (coalesced) | 5.0 | graphic: graphic-id, view: string | A graphic moved, was resized or relabelled. |
| `graphic:removed` | 5.0 | graphic: graphic-id, model: string | A graphic was deleted. |
| `graphic:selection.changed` | 5.0 | graphics: array, view: string | The selected graphics of a view changed. |

### state:

| Event | Since | Payload | Description |
|---|---|---|---|
| `state:checkpoint.taken` | 5.0 | checkpoint: string, checkpointSeq: integer, label: string | A checkpoint of the display was taken. |
| `state:rollback.done` | 5.0 | checkpoint: string, checkpointSeq: integer, views: integer, notRestored: integer, documents: integer | The display was put back to a checkpoint. |

### view2d:

| Event | Since | Payload | Description |
|---|---|---|---|
| `view2d:image.changed` (coalesced) | 5.0 | view: view-id, series: string, imageIndex: integer | The selected pane of a viewer shows another image. |
| `view2d:series.changed` | 5.0 | view: view-id, pane: integer, series: string | A pane of a viewer shows another series, or none; whatever opened it. |
| `view2d:windowLevel.changed` (coalesced) | 5.0 | view: view-id, window: number, level: number | Window or level of the selected pane was set. |
| `view2d:windowLevel.changing` (coalesced) | 5.0 | view: view-id, window: number, level: number | Window or level of the selected pane moves under a drag; local subscribers only. |

### weasis:

| Event | Since | Payload | Description |
|---|---|---|---|
| `weasis:heartbeat` | 5.0 |  | Sent on an idle stream; seq is the last sequence number. |
| `weasis:ready` | 5.0 | version: string, apiVersion: string, profile: string | The plugins are started and the commands answer. |
| `weasis:shutdown` | 5.0 |  | The application is stopping. |
| `weasis:subscriber.dropped` | 5.0 | subscriber: string, dropped: integer, detached: boolean | This subscriber fell behind: the oldest pending events were dropped; one that loses 6144 events without catching up is detached. |
| `weasis:ui.modal` | 5.0 | state: string, title: string | A modal dialog opened or closed. |
| `weasis:user.active` (coalesced) | 5.0 | window: integer | The user moved the mouse or typed. |

### workspace:

| Event | Since | Payload | Description |
|---|---|---|---|
| `workspace:layout.changed` | 5.0 | view: view-id, layout: string | A viewer changed its pane layout. |
| `workspace:panel.changed` | 5.0 | panel: string | The toolbars or the tool panels were rebuilt for the selected viewer. |
| `workspace:view.closed` | 5.0 | view: view-id, window: integer | A view left the workspace. |
| `workspace:view.opened` | 5.0 | view: view-id, type: string, window: integer | A view was docked into a window. |
| `workspace:view.selected` (coalesced) | 5.0 | view: view-id, type: string, window: integer | A view became the application's selection. |
| `workspace:window.activated` (coalesced) | 5.0 | window: integer | The active window changed, once it stayed so for 100 ms; from the engine, not AWT. |
| `workspace:window.closed` | 5.0 | window: integer, detached: boolean | A floating viewer window closed. |
| `workspace:window.opened` | 5.0 | window: integer, detached: boolean | A window hosting a workspace appeared. |

