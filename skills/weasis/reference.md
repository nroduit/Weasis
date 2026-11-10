# Weasis commands

Command API 2.34, protocol 2, application development.

## color

Color management of the display: switch, display profile, view conversion.

### color:set

Switches color management or sets the display profile, then prints the status.

Usage: `color:set [-h] [--json] [--enabled=<true|false>] [--display-profile=<input-path> | --srgb]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --display-profile | input-path |  | ICC profile file (.icc, .icm) of the display of this workstation. |
| --enabled | boolean |  | Convert color images to the display profile. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --srgb | boolean |  | No display profile: the display is sRGB. |

### color:status

Whether color management is on, the display profile, and the profile and conversion of a view.

Usage: `color:status [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |


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
| --force | boolean |  | Overwrite a JSON document that exists. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --source | path |  | Root of the resources package holding the XML documents (default: the current one). |
| --target | path |  | Folder to write the JSON documents into (default: the config folder of the preference directory). |

### config:import-legacy (since 5.0)

Imports the settings, workstation values and documents of a Weasis 4 installation (~/.weasis) into this one; nothing there is changed. A value already set here is kept unless --replace; --dry-run shows what would change.

Usage: `config:import-legacy [-h] [--dry-run] [--json] [--replace] [--from=<input-path>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --dry-run | boolean |  | Show what would change, change nothing. |
| --from | input-path |  | The folder of the Weasis 4 installation (default: ~/.weasis). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --replace | boolean |  | Take the Weasis 4 value where this installation already has one. |

| Result | Type | Description |
|---|---|---|
| root | string | the Weasis 4 folder read |
| preferences | string | its preference folder of the user and profile |
| data | string | its data folder |
| dryRun | boolean | true when nothing was changed |
| replace | boolean | true when existing values were replaced |
| items | array | what was imported: a settings group, a machine group, a document |
| items[].kind | string | settings, machine or document |
| items[].name | string | the group or the document |
| items[].source | string | the Weasis 4 file read |
| items[].target | string | the file of this installation |
| items[].added | integer | values or documents added |
| items[].replaced | integer | values or documents replaced |
| items[].kept | integer | values or documents left as they were |
| added | integer | total added |
| replaced | integer | total replaced |
| kept | integer | total kept |
| checkpoint | string | the checkpoint taken before the import, when one was |

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
| --out | output-path |  | The package to write (default: a dated name in the current folder). |
| --with-machine | boolean |  | With a user package, also the settings and documents of this workstation. |

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
| --dry-run | boolean |  | Show what would change, change nothing. |
| --file | input-path |  | The package to import. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --replace | boolean |  | Replace each document and settings group of the package whole, instead of merging. |


## dicom

The DICOM explorer: loading, listing, opening and closing studies, and reading attributes.

### dicom:echo (since 5.0)

C-ECHO to a configured DICOM node or printer, with the TLS of its document: the readiness probe of an archive before a query, a retrieve or a send. The answer gives the round trip; a failure names what failed (an untrusted or expired certificate, no protocol in common, a closed port) instead of a generic connection error.

Usage: `dicom:echo [-h] [--json] [--aet=<aet>] --node=<node> [--timeout=<seconds>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --aet | string |  | Calling AE title (default: weasis.aet, else the first calling node, else WEASIS_AE). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --node | string |  | The node: its id, its description or its AE title, from the DICOM nodes. |
| --timeout | integer | 10 | Connect and accept time-out (default: 10). |

### dicom:info (since 5.0)

Technical description of a series: modality, body part, image count, geometry and pixel spacing. Identifiers follow the identity policy.

Usage: `dicom:info [-h] [--json] (--series=<series-uid> | --view=<view-id>)`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list'. |
| --view | view-id |  | View id from 'workspace:list'; its selected pane gives the series. |

### dicom:interest (since 5.0)

Images of interest of the loaded series: those a key object selection, an annotated presentation state or a segmentation points at, and those drawn on in Weasis, with their sources. Filter by study or by series.

Usage: `dicom:interest [-h] [--images] [--json] [--series=<series-uid>] [--study=<study-uid>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --images | boolean |  | Also list the SOP Instance UIDs of the images of interest. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list': this series only. |
| --study | study-uid |  | Study Instance UID from 'dicom:list'; default: every loaded study. |

### dicom:list (since 5.0)

Patients, studies and series loaded in the explorer, as JSON. Identifiers follow the identity policy; UIDs are the handles for the other verbs.

Usage: `dicom:list [-h] [--json] [--after=<n>] [--limit=<n>]`

Produces: `patient-handle`, `study-uid`, `series-uid`

| Option | Type | Default | Description |
|---|---|---|---|
| --after | integer |  | Skip the first n entries, as the previous answer's 'next' says. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --limit | integer |  | At most this many entries (default: all of them). (since 5.0) |

| Result | Type | Description |
|---|---|---|
| patients | array | the loaded patients |
| patients[].patientID | patient-handle | the patient handle, masked for a remote caller |
| patients[].patientName | string | Patient's Name, masked |
| patients[].patientBirthDate | string | Patient's Birth Date, ISO 8601, masked |
| patients[].patientSex | string | Patient's Sex |
| patients[].studies | array | the studies of the patient |
| patients[].studies[].studyInstanceUID | study-uid | the study handle |
| patients[].studies[].studyDate | string | Study Date, ISO 8601 |
| patients[].studies[].studyTime | string | Study Time, ISO 8601 |
| patients[].studies[].studyDescription | string | Study Description |
| patients[].studies[].accessionNumber | string | Accession Number, masked |
| patients[].studies[].studyID | string | Study ID, masked |
| patients[].studies[].series | array | the series of the study |
| patients[].studies[].series[].id | series-uid | the series handle; the UID, or the UID with a suffix for a split series |
| patients[].studies[].series[].seriesInstanceUID | series-uid | Series Instance UID |
| patients[].studies[].series[].seriesNumber | integer | Series Number |
| patients[].studies[].series[].modality | string | Modality |
| patients[].studies[].series[].seriesDescription | string | Series Description |
| patients[].studies[].series[].imageCount | integer | images loaded |
| patients[].studies[].series[].downloadState | string | complete, partial or none, under a selective download |
| patients[].studies[].series[].provenance | object | where the series comes from, when it was derived |
| total | integer | patients before the window |
| truncated | boolean | true when the window cut the list |
| next | integer | offset of the next window, when truncated |

### dicom:load

Loads DICOM files from local paths, URLs, manifests, ZIP archives or the portable directories, and returns what was queued and how far the loading went.

Usage: `dicom:load [-h] [--json] [--portable] [--wait[=<wait-level>]] [--timeout=<seconds>] [--manifest=<remote-url>]... [--manifest-gzip=<data>]... [--path=<input-path>]... [--url=<remote-url>]... [--zip=<remote-url>]...`

Needs: file read, network read

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --manifest | remote-url | [] | An XML manifest (WADO). (since 5.0) |
| --manifest-gzip | string | [] | An XML manifest encoded as GZIP then Base64. (since 5.0) |
| --path | input-path | [] | A DICOM file or a directory (recursive). (since 5.0) |
| --portable | boolean |  | The DICOM directories configured next to the executable. |
| --timeout | integer | 600 | Give up waiting after this long (default: 600). (since 5.0) |
| --url | remote-url | [] | A DICOM file URL. (since 5.0) |
| --wait | enum | none | Answer when the loading reaches this point: none, first-series or all (default: none, or all when the option carries no value). (since 5.0) |
| --zip | remote-url | [] | A ZIP archive of DICOM files. |

### dicom:load-dicomweb

Loads DICOM studies through a DICOMweb service (QIDO/WADO-RS) and returns what was queued and how far the loading went.

Usage: `dicom:load-dicomweb [-h] [--json] [--show-whole-study] [--wait[=<wait-level>]] [--accept-ext=<ext>] [--auth-uid=<auth-uid>] [--oidc-issuer=<issuer>] [--oidc-user=<login>] [--query-ext=<ext>] [--retrieve-ext=<ext>] [--timeout=<seconds>] [--url=<remote-url>] [-H=<header>]... [--query-header=<header>]... [--request=<query>]... [--retrieve-header=<header>]...`

Needs: network read

| Option | Type | Default | Description |
|---|---|---|---|
| --accept-ext | string | transfer-syntax=* | Parameters of the multipart/related Accept header (default: transfer-syntax=*). |
| --auth-uid | string |  | UID of the Weasis authentication method. |
| -H, --header | string | [] | Custom header for every request. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --oidc-issuer | string |  | Issuer identifier of the OpenID Connect authentication request. (since 5.0) |
| --oidc-user | string |  | Login hint of the end user (OpenID Connect). (since 5.0) |
| --query-ext | string |  | Additional query URL parameters (QIDO). |
| --query-header | string | [] | Custom header for the query requests (QIDO). |
| --request | string | [] | Query parameters of the URL, see weasis-pacs-connector. |
| --retrieve-ext | string |  | Additional retrieve URL parameters (WADO). |
| --retrieve-header | string | [] | Custom header for the retrieve requests (WADO). |
| --show-whole-study | boolean |  | When downloading a series, list the other series of the study, ready for download. |
| --timeout | integer | 600 | Give up waiting after this long (default: 600). (since 5.0) |
| --url | remote-url |  | URL of the DICOMweb service. |
| --wait | enum | none | Answer when the loading reaches this point: none, first-series or all (default: none, or all when the option carries no value). (since 5.0) |

### dicom:objects (since 5.0)

Non-image objects of the loaded studies (key object selections, presentation states, segmentations, RT objects): kind, label, date, creator and the series they reference. Filter by study, by referenced series or by kind. Labels follow the identity policy.

Usage: `dicom:objects [-h] [--json] [--series=<series-uid>] [--study=<study-uid>] [--type=<kind>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list': only the objects that reference this series. |
| --study | study-uid |  | Study Instance UID from 'dicom:list'; default: every loaded study. |
| --type | string |  | Only this kind: ko, rejection, pr, seg, rtstruct, rtplan, rtdose, other. |

### dicom:open (since 5.0)

Opens a series: in the view given by --view, and in its pane given by --pane, in a new view of the type given by --viewer-type, or in the default viewer of its type. Returns the view id and what the viewer decided while opening (e.g. 'rectified', 'warnings'); the client option -y answers its questions instead of showing them.

Usage: `dicom:open [-h] [--interest] [--json] [--wait[=<wait-level>]] [--pane=<pane-index>] --series=<series-uid> [--view=<view-id> | --viewer-type=<viewer-type>]`

Produces: `view-id`

| Option | Type | Default | Description |
|---|---|---|---|
| --interest | boolean |  | Show the images of interest only (referenced by a KO, an annotated PR or a SEG, or drawn on); the whole series when it has none. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane of --view that receives the series, from 1 (default: the selected one). (since 5.0) |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list'. |
| --view | view-id |  | View id from 'workspace:list' that receives the series. |
| --viewer-type | string |  | Opens a new view of this type, by id from 'dicom:info' (e.g. 'mpr'), or by the name the interface shows. (since 5.0) |
| --wait | enum | none | Answer once the viewer has finished building its content, e.g. the MPR volume: none or all, since a viewer builds in one step (default: none, or all when the option carries no value). |

| Result | Type | Description |
|---|---|---|
| target | object | the view and pane the command resolved |
| target.view | view-id | the view |
| target.pane | pane-index | the pane in its grid |
| target.viewerType | string | the viewer type id |
| target.series | series-uid | the series the pane shows |
| target.image | image-index | the image shown, from 1 |
| target.imageCount | integer | images of the series |
| ready | boolean | true once the view finished building its content, with --wait |
| warnings | array | what the view decided while opening, with -y |

### dicom:tag (since 5.0)

Value of a DICOM attribute of an image of the series, by keyword or (gggg,eeee). Identity attributes follow the identity policy; private tags are not exposed.

Usage: `dicom:tag [-h] [--json] [--image=<image-index>] [--tag=<dicom-keyword>] (--series=<series-uid> | --view=<view-id>)`

| Option | Type | Default | Description |
|---|---|---|---|
| --image | image-index | 1 | Image index in the series, from 1 (default: 1). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | series-uid |  | Series Instance UID (or series id) from 'dicom:list'. |
| --tag | string |  | DICOM keyword (e.g. SliceLocation) or hexadecimal tag (gggg,eeee). (since 5.0) |
| --view | view-id |  | View id from 'workspace:list'; its selected pane gives the series. |

### dicom:unload

Takes patients, studies or series out of the explorer and returns what was unloaded. Every handle is resolved first: an unknown one unloads nothing. A patient is named by the identifier 'dicom:list' returned, which is the masked one under a masking policy.

Usage: `dicom:unload [-h] [--all] [--json] [--patient=<patient-handle>]... [--series=<series-uid>]... [--study=<study-uid>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --all | boolean |  | Unload every patient. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --patient | patient-handle | [] | Patient identifier to unload, as 'dicom:list' returns it. |
| --series | series-uid | [] | Series Instance UID, or series id, to unload. |
| --study | study-uid | [] | Study Instance UID to unload. |

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
| --markdown | boolean |  | A Markdown table, for a reference page. (since 5.0) |
| --namespace | string |  | Only the events of this namespace. (since 5.0) |

### events:recent (since 5.0)

The last events the bus kept, oldest first: a poll for a caller that cannot hold a stream. Pass the 'lastSeq' of the previous answer as --since to read only what is new.

Usage: `events:recent [-h] [--json] [--filter=<event-glob>] [--last=<n>] [--since=<seq>] [--where=<key=value>]...`

Needs: events

| Option | Type | Default | Description |
|---|---|---|---|
| --filter | string |  | Event names to keep, with '*' as a wildcard: 'dicom:*,workspace:view.*'. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --last | integer | 50 | At most this many events, the newest ones (default: 50). (since 5.0) |
| --since | integer | 0 | Only the events after this sequence number. (since 5.0) |
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
| --clear | boolean |  | Clears every facet first. |
| --interest | string | [] | Keep the series with images of interest: any, or pointed at by one of ko, graphics, pr, seg (repeatable, or comma-separated). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --modality | string | [] | Modalities to keep (repeatable, or comma-separated). |
| --objects | string | [] | Keep the series referenced by an object of one of these kinds: ko, pr, seg, rtstruct, rtplan, rtdose, rejection, other (repeatable, or comma-separated). |
| --state | string | [] | Download states to keep: complete, pending, downloading. |
| --study | study-uid | [] | Study Instance UID from 'dicom:list': only its series; repeatable, as a selection on the timeline strip. |
| --text | string |  | Free text over series and study attributes, anatomy and its region group. |

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
| --new-tab | boolean |  | Opens new viewers instead of re-hanging the one of the same patient. |
| --protocol | string |  | Hangs this protocol from 'list' even when another one scores better. |
| --protocols | input-path |  | A JSON file of protocol entries used instead of the layers, to try a draft. |
| --screen | screen-role |  | Opens the viewers of the step in a viewer window on the screen of this role (control, diagnostic-1, diagnostic-2, color, portrait, see 'workspace:screens'). |
| --step | integer |  | The reading step to hang, from 1 (default: the first). |
| --study | study-uid |  | Study Instance UID from 'dicom:list'. |

### hp:compare (since 5.0)

Hangs a study against the priors you name: the protocol (the best comparison one, or --protocol) is matched on --current with --prior as its only priors, and its first step is hung. Every study must be loaded.

Usage: `hp:compare [-h] [--json] --current=<study-uid> [--protocol=<protocol-id>] --prior=<study-uid> [--prior=<study-uid>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --current | study-uid |  | The current study, from 'dicom:list'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --prior | study-uid |  | A prior study of the same patient; repeatable. |
| --protocol | string |  | Hangs this protocol from 'list' even when another one scores better. |

### hp:correct (since 5.0)

Puts another series of the study in a cell of the active hanging: the cell is re-hung with it, the correction is recorded with the rule that would have produced the choice, and --apply writes that rule into the protocol when it is a user one.

Usage: `hp:correct [-h] [--apply] [--json] --cell=<cell> --series=<series-uid> [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --apply | boolean |  | Writes the proposed rule into the protocol (user protocols only). |
| --cell | integer |  | The cell to correct, from 1. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | string |  | Series Instance UID of the study, from 'dicom:list' or 'explain'. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

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
| --id | string |  | Protocol id from 'list'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |

### hp:explain (since 5.0)

Why the cells of the active hanging show what they show: per cell the rules that selected the series, its score and the alternatives; the why of a hanging.

Usage: `hp:explain [-h] [--json] [--cell=<cell>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --cell | integer |  | Only this cell, from 1. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

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
| --dicom | boolean |  | Writes a DICOM object instead of JSON. |
| --id | string |  | Protocol id from 'list'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --out | output-path |  | Target file. |
| --plain | boolean |  | Leaves the Weasis extensions out. |

### hp:feature (since 5.0)

Sets a derived feature of a loaded series by hand (role, orientation, anatomy, laterality, view, contrast, mrWeighting, ctKernel.family, temporal), as a USER value that outranks every header, table, classifier and heuristic; the next matching uses it. '--by agent' records the caller as 'agent:<origin>', so a correction of an agent stays distinguishable from the radiologist's. In memory for the series, until it is loaded again.

Usage: `hp:feature [-h] [--json] [--by=<who>] --series=<series-uid> --set=<feature=value> [--set=<feature=value>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --by | string |  | 'user' (default) or 'agent': who states the value. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | string |  | Series Instance UID, from 'dicom:list' or 'features'. |
| --set | string |  | A feature and its value, repeatable: orientation=CORONAL, mrWeighting=T2. |

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
| --corpus | input-path |  | A folder of DICOM studies read without a viewer, walked recursively. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --study | study-uid |  | Study Instance UID from 'dicom:list'. |

| Result | Type | Description |
|---|---|---|
| studies | array | per study: its series with 'features', the 'provenance' of each derived feature (kind, source, detail, confidence, version) and the 'contests' of the providers |

### hp:group (since 5.0)

Shows another reading step (presentation group) of the active hanging, or hangs another candidate protocol, on the same viewers.

Usage: `hp:group [-h] [--json] [--next] [--previous] [--index=<step>] [--protocol=<protocol-id>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --index | integer |  | The step to show, from 1. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --next | boolean |  | The next step, the first one again after the last. |
| --previous | boolean |  | The previous step. |
| --protocol | string |  | Hangs this candidate of the study instead, from its first step. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### hp:import (since 5.0)

Reads a protocol file, a DICOM Hanging Protocol Storage object or a JSON entry (one, an array, or an object with 'protocols'), and stores the protocols in the user layer with their level; a protocol of the same id in the built-in or site layer is overridden by it. An object without the Weasis private block imports with its DICOM selectors alone, with a warning.

Usage: `hp:import [-h] [--json] --file=<input-path> [--id=<protocol-id>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --file | input-path |  | Source file. |
| --id | string |  | The id to store a single imported protocol under. |
| --json | boolean |  | Structured JSON output (default for remote calls). |

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
| --protocol | string |  | Plans this protocol from 'list' even when another one scores better. |
| --protocols | input-path |  | A JSON file of protocol entries matched instead of the layers, to try a draft. |
| --study | study-uid |  | Study Instance UID from 'dicom:list'. |

### hp:open (since 5.0)

Opens a study hung by a protocol, the reading workflow in one call: the best protocol (or --protocol) is matched on the study with its priors, the older loaded studies of the patient, or only the ones --prior names, and its first step is hung. The study and its priors must be loaded ('dicom:get' first); the selective download of the hung series is not there yet.

Usage: `hp:open [-h] [--json] [--new-tab] [--protocol=<protocol-id>] [--study=<study-uid>] [--prior=<study-uid>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --new-tab | boolean |  | Opens new viewers instead of re-hanging the one of the same patient. |
| --prior | study-uid |  | A prior study to compare with; repeatable. Default: every loaded prior. |
| --protocol | string |  | Hangs this protocol from 'list' even when another one scores better. |
| --study | study-uid |  | Study Instance UID from 'dicom:list'. |

### hp:save (since 5.0)

Save as protocol: derives a user protocol from the display of a 2D viewer, per cell the smallest set of feature selectors that singles its series out among the study and the presentation of the cell (preset, color map, inversion, sort, tiling, synchronization), and stores it in the user layer; --dry-run only returns the rules and which loaded studies the protocol would apply to.

Usage: `hp:save [-h] [--dry-run] [--json] [--id=<protocol-id>] --name=<text> [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --dry-run | boolean |  | Derives and reports without storing; matches the loaded studies. |
| --id | string |  | The entry id. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --name | string |  | The protocol name; the id is derived from it unless --id. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### hp:sets (since 5.0)

The hanging a viewer is part of: the protocol, the reading step shown of how many, the display sets with their view ids, the candidates of the study.

Usage: `hp:sets [-h] [--json] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### hp:show (since 5.0)

A protocol of the layers as its JSON entry: the DICOM part by keyword and the Weasis extensions (feature selectors, viewer types, display by reference).

Usage: `hp:show [-h] [--json] --id=<protocol-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --id | string |  | Protocol id from 'list'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |

### hp:validate (since 5.0)

Runs the matching over a folder of studies without a viewer and reports, per protocol, how many studies it applies to and how its cells matched (exact, preferred, fallback, shared, empty) and, per study, the protocol chosen: the regression test of a protocol library. The protocols are those of every layer, or the entries of --protocols.

Usage: `hp:validate [-h] [--json] [--corpus=<input-path>] [--expected=<input-path>] [--file=<input-path>] [--protocol=<protocol-id>] [--protocols=<input-path>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --corpus | input-path |  | A folder of DICOM studies, walked recursively. |
| --expected | input-path |  | A JSON file of expected hangings, {"studies": {"<study-uid>": {"protocol": <id>, "cells": {"1": <series-uid>}}}}: the report says per study whether the chosen protocol and the cells of its first step are the expected ones. |
| --file | input-path |  | A JSON file of protocol entries checked for rule errors without any study: image set numbers unique and referenced, selectors known, one time-based item per set, grids the viewer offers. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --protocol | string |  | Only this protocol. |
| --protocols | input-path |  | A JSON file holding protocol entries (an array, or an object with a 'protocols' array) validated instead of the layers. |


## image

Loading and closing of plain images (non-DICOM) in the default viewer.

### image:compare (since 5.0)

Compares two image files pixel by pixel and returns how far apart they are: the number of pixels that differ, their share, and the largest and mean channel difference.

Usage: `image:compare [-h] [--json] --a=<input-path> --b=<input-path> [--tolerance=<n>]`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --a | input-path |  | First image. (since 5.0) |
| --b | input-path |  | Second image. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
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
| --file | input-path | [] | Image file to open. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --url | remote-url | [] | Image URL to download and open. |

### image:unload

Closes groups or images and returns what was closed; an unknown handle closes nothing.

Usage: `image:unload [-h] [--all] [--json] [--group=<group>]... [--series=<series>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --all | boolean |  | Close every group. |
| --group | string | [] | Group to close, as 'image:load' returns it. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --series | string | [] | Series or image to close, as 'image:load' returns it. |


## measure

Measurement tools: the tool registry, the profiles, the export of the graphics of a pane and the manual calibration.

### measure:calibrate (since 5.0)

Calibrates the image of a pane from a line of known length: the pixel size follows the line, the series with --series; --remove drops the user's calibration.

Usage: `measure:calibrate [-h] [--json] [--remove] [--series] [--graphic=<graphic-id>] [--length=<n>] [--pane=<pane-index>] [--unit=<unit>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --graphic | graphic-id |  | A line graphic of the pane, from view2d:measure or view2d:graphics. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --length | number |  | The real length of the line, in --unit. |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --remove | boolean |  | Drop the user's calibration instead. |
| --series | boolean |  | Calibrate every image of the series. |
| --unit | string | mm | mm, cm, inch, micrometer… (default: mm). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

| Result | Type | Description |
|---|---|---|
| pixelSize | number | the size of one pixel after the call |
| unit | string | the unit of pixelSize |
| series | boolean | true when the series was calibrated |
| removed | boolean | true when the user's calibration was dropped |

### measure:export (since 5.0)

Writes the graphics of a pane to a file: xml (the presentation model of Weasis), csv (the measured values, one row per value) or a format a plugin adds, sr and pr with the DICOM plugins ('measure:export --format ?' lists them).

Usage: `measure:export [-h] [--force] [--json] --format=<format> [--layer=<layer-name>] [--out=<output-path>] [--pane=<pane-index>] [--view=<view-id>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --force | boolean |  | Replace an existing file. |
| --format | string |  | xml, csv, or a format of a plugin; '?' lists the formats of this instance. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --layer | layer-name |  | Only the graphics of this layer (default: the measurement and drawing layers). |
| --out | output-path |  | The file to write. |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

| Result | Type | Description |
|---|---|---|
| formats | array | with --format ?: the formats of this instance |
| formats[].format | string | the format id |
| formats[].description | string | what it writes |
| format | string | the format written |
| file | string | the file written |
| graphics | integer | graphics written |
| skipped | integer | graphics the format could not take |
| details | object | what the format says about the file, e.g. its SOP Instance UID |

### measure:profile (since 5.0)

Chooses the measurement profile in use, or exports and imports profiles as JSON.

Usage: `measure:profile [-h] [--force] [--json] [--id=<id>]... (--automatic | --export=<output-path> | --import=<input-path> | --select=<id>)`

Needs: file read, file write

| Option | Type | Default | Description |
|---|---|---|---|
| --automatic | boolean |  | Let the modality choose the profile again. |
| --export | output-path |  | Write the user profiles (or --id) as a JSON file. |
| --force | boolean |  | Replace an existing file. |
| --id | string |  | With --export: the profiles to write, by id (default: the user profiles). |
| --import | input-path |  | Read profiles from a JSON file into the user layer. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --select | string |  | Use this profile whatever the modality. |

| Result | Type | Description |
|---|---|---|
| selected | string | the profile chosen by hand, absent when automatic |
| automatic | boolean | true when the modality chooses the profile |
| file | string | the file written or read |
| profiles | array | the ids written or read |

### measure:profiles (since 5.0)

The measurement profiles: built-in, site and user, and the one in use.

Usage: `measure:profiles [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| selected | string | the profile chosen by hand, absent when automatic |
| automatic | boolean | true when the modality chooses the profile |
| profiles | array | the profiles |
| profiles[].id | string | the profile id |
| profiles[].name | string | the name shown |
| profiles[].modalities | array | the modalities it binds to |
| profiles[].tools | array | the tool keys of its palette |
| profiles[].origin | string | built-in, site or user |
| profiles[].locked | boolean | true when the site locks it |

### measure:tools (since 5.0)

The measurement and drawing tools of the registry: key, category, label, the measurements each one computes and the points it takes.

Usage: `measure:tools [-h] [--json] [--category=<measure|draw>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --category | string |  | Only the tools of this category. |
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| tools | array | the tools, in the order of the palette |
| tools[].tool | string | the tool key |
| tools[].category | string | measure or draw |
| tools[].label | string | the label shown |
| tools[].points | integer | points the tool takes at least |
| tools[].measurements | array | the measurement keys the tool computes |
| tools[].measurements[].key | string | stable name of the measurement |
| tools[].measurements[].name | string | English name |


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
| --no-open | boolean |  | Build the series without opening it. |
| --plane | string |  | AXIAL, CORONAL or SAGITTAL; not the plane the series already lies in. |
| --series | series-uid |  | The series reformatted, from 'dicom:list' (default: the selected pane's). |

### mpr:volumes (since 5.0)

The volumes the service holds for the MPR, the volume rendering and the derived series: built once per series and kept, within a budget, for the next consumer.

Usage: `mpr:volumes [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |


## plugins

The plugins of this instance: the loaded set with its sources, tiers and states, the site policy, and the user's own plugins (import, remove, enable, disable), effective at the next start.

### plugins:add (since 5.0)

Imports a plugin jar into the user folder: reads its manifest and signature, says its id, version, tier and capabilities, refuses what the policy or the checks refuse; loaded at the next start.

Usage: `plugins:add [-h] [--json] --file=<input-path>`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --file | input-path |  | The plugin jar. |
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| id | string | plugin id |
| version | string | plugin version |
| source | string | package, site or user |
| tier | string | core, verified, site or unverified |
| state | string | started, disabled, refused, candidate or failed |
| file | string | the jar |
| sha256 | string | the digest of the jar, when computed |
| publisherKeyId | string | the key that signed it |
| capabilities | array | the declared capabilities |
| reason | string | why it is not started |
| restart | boolean | always true: loaded at the next start |

### plugins:disable (since 5.0)

Disables a user plugin for the next start.

Usage: `plugins:disable [-h] [--json] <plugin-id>`

| Parameter | Type | Description |
|---|---|---|
| <plugin-id> | string | The plugin id, from 'list'. |

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| id | string | plugin id |
| enabled | boolean | the state recorded |
| restart | boolean | always true |

### plugins:enable (since 5.0)

Enables a user plugin for the next start.

Usage: `plugins:enable [-h] [--json] <plugin-id>`

| Parameter | Type | Description |
|---|---|---|
| <plugin-id> | string | The plugin id, from 'list'. |

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| id | string | plugin id |
| enabled | boolean | the state recorded |
| restart | boolean | always true |

### plugins:info (since 5.0)

One plugin of the manifest, by id.

Usage: `plugins:info [-h] [--json] <plugin-id>`

| Parameter | Type | Description |
|---|---|---|
| <plugin-id> | string | The plugin id, from 'list'. |

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| id | string | plugin id |
| version | string | plugin version |
| source | string | package, site or user |
| tier | string | core, verified, site or unverified |
| state | string | started, disabled, refused, candidate or failed |
| file | string | the jar |
| sha256 | string | the digest of the jar, when computed |
| publisherKeyId | string | the key that signed it |
| capabilities | array | the declared capabilities |
| reason | string | why it is not started |

### plugins:list (since 5.0)

The loaded-plugins manifest of this start: every plugin with its source (package, site, user), tier (core, verified, site, unverified), capabilities and state, the site policy, whether the set is locked, and the fingerprint of the set.

Usage: `plugins:list [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| policy | string | the site policy in force |
| locked | boolean | whether the set is frozen by the site |
| fingerprint | string | SHA-256 of the started set, for the validation record |
| userFolder | string | the drop-in folder of the user |
| manifest | string | the loaded-plugins.json file |
| plugins | array | the plugins, each as 'info' prints it |

### plugins:remove (since 5.0)

Removes a user plugin: its jar and its registry entry.

Usage: `plugins:remove [-h] [--json] <plugin-id>`

| Parameter | Type | Description |
|---|---|---|
| <plugin-id> | string | The plugin id, from 'list'. |

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| id | string | plugin id |
| removed | boolean | always true |
| restart | boolean | always true |

### plugins:verify (since 5.0)

Checks the user's plugins again now: the digest of each jar against its import and its signature against the site keys; what would change at the next start.

Usage: `plugins:verify [-h] [--json]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |

| Result | Type | Description |
|---|---|---|
| plugins | array | per imported plugin: id, file, ok, sha256, tier, reason |
| candidates | array | jars of the folder not imported |


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
| --annotations | enum | on | Print the overlay and the graphics (default: on). |
| --background | enum |  | Page background (default: the destination's, white for paper). |
| --frames | string |  | With --series, the range of frames, counting from 1 (default: all). (since 5.0) |
| --grid | string | 4x3 | Cells per page with --series (default: 4x3). (since 5.0) |
| --header | enum | off | Header line with patient, study and page number above the cells (default: off). (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --masking | string |  | Identity masking profile applied to the page (default: none). |
| --orientation | enum |  | Page orientation (default: landscape when the layout is wider than tall). |
| --pane | pane-index |  | Print only this pane of the layout, counting from 1. |
| --paper | string |  | Paper id, such as A4, Letter or 14x17in (default: the destination's). |
| --position | enum | center | Where an image sits in its cell (default: center). |
| --series | boolean |  | Print the frames of the series of the pane (--pane, else the selected one) on pages of --grid cells, instead of the layout as shown. (since 5.0) |
| --step | integer | 1 | With --series, one frame out of n (default: 1). (since 5.0) |
| --text | enum | auto | Text size of annotations and labels on paper (default: auto, 8 pt or 6 pt). |
| --to | string |  | Destination id or title from 'print:destinations': its papers and printable area apply (default: default margins on the paper of the locale). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### print:render

Renders the page of a job to a PNG file at a resolution, as the printer would receive it: the preview of an agent and the test surface of a layout.

Usage: `print:render [-h] [--gray] [--json] [--dpi=<dpi>] --job=<job-id> --out=<output-path> [--page=<n>] [--to=<destination>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --dpi | integer |  | Resolution of the raster (default: the destination's, else 150). |
| --gray | boolean |  | Render in 8-bit gray, as a gray film printer receives it. |
| --job | string |  | Job from 'print:page'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --out | output-path |  | The PNG file to write. |
| --page | integer | 1 | The page to render, counting from 1 (default: 1). (since 5.0) |
| --to | string |  | Render for this destination: its printable area and resolution apply. |

### print:send

Prints a job on a destination and releases it. Sending patient images to a device is closed to callers unless the network.write policy of this instance allows it, as for dicom:send.

Usage: `print:send [-h] [--json] [--copies=<n>] [--dpi=<dpi>] --job=<job-id> [--out=<output-path>] --to=<destination>`

Needs: file write, send dicom

| Option | Type | Default | Description |
|---|---|---|---|
| --copies | integer | 1 | Number of copies (default: 1). |
| --dpi | integer |  | Resolution of the pages written to the file (default: 300). (since 5.0) |
| --job | string |  | Job from 'print:page'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --out | output-path |  | The file to write, for the PDF file destination (file:pdf). (since 5.0) |
| --to | string |  | Destination id or title from 'print:destinations'. |


## redact

Regions burned over the pixels of a view, and the device library they can come from.

### redact:add (since 5.0)

Hides an area of the selected view.

Usage: `redact:add [-h] [--json] [--pane=<pane-index>] [--scope=<scope>] [--view=<view-id>] (--ellipse=<image-rect> | --polygon=<image-polygon> | --rect=<image-rect>)`

| Option | Type | Default | Description |
|---|---|---|---|
| --ellipse | image-rect |  | Ellipse inscribed in this rectangle. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --polygon | image-polygon |  | Closed outline as x,y pairs (three at least). |
| --rect | image-rect |  | Axis-aligned rectangle, in image pixels. |
| --scope | enum | IMAGE | Where it applies: IMAGE, SERIES. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### redact:apply (since 5.0)

Hides the regions of a device entry on the selected view, all of them: a chosen entry is applied as it was drawn, where 'match' proposes only what the profile in force does not keep.

Usage: `redact:apply [-h] [--json] --entry=<entry-id> [--pane=<pane-index>] [--scope=<scope>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --entry | entry-id |  | Device entry from 'redact:library'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --scope | enum | IMAGE | Where it applies: IMAGE, SERIES. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### redact:attest (since 5.0)

Records that a person reviewed the pixels of the selected image or series and found no burned-in identity, so it may be captured or exported without a redaction; --withdraw takes it back. Held until the study is unloaded.

Usage: `redact:attest [-h] [--json] [--withdraw] [--by=<name>] [--pane=<pane-index>] [--reason=<text>] [--scope=<scope>] [--view=<view-id>]`

Needs: pixel attest

| Option | Type | Default | Description |
|---|---|---|---|
| --by | string |  | Who reviewed the pixels; required unless --withdraw. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --reason | string |  | What the review was, such as the catalog and the date it was recorded. |
| --scope | enum | IMAGE | Where it applies: IMAGE, SERIES. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |
| --withdraw | boolean |  | Takes back the attestation of that scope. |

### redact:clear (since 5.0)

Removes the regions of the selected image or series.

Usage: `redact:clear [-h] [--json] [--pane=<pane-index>] [--scope=<scope>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --scope | enum | IMAGE | Where it applies: IMAGE, SERIES. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### redact:delete (since 5.0)

Removes a device entry from the user library.

Usage: `redact:delete [-h] [--json] --entry=<entry-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --entry | entry-id |  | Device entry from 'redact:library'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |

### redact:export (since 5.0)

Writes the device library to a file.

Usage: `redact:export [-h] [--complete] [--force] [--json] [--karnak] [--masking-profile=<masking-profile-id>] --out=<output-path>`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --complete | boolean |  | Write a whole Karnak profile, not only its masks block. |
| --force | boolean |  | Replace the file when it exists and was not written by this session. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --karnak | boolean |  | Write the masks block of a Karnak profile. |
| --masking-profile | masking-profile-id |  | Masking profile of a Karnak export (default: the session one). (since 5.0) |
| --out | output-path |  | File to write. |

### redact:import (since 5.0)

Adds the device entries of a file to the user library.

Usage: `redact:import [-h] [--json] [--karnak] --file=<input-path>`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --file | input-path |  | Document to read. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
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
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### redact:match (since 5.0)

The device entries that fit the selected view, and why the others do not.

Usage: `redact:match [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### redact:save (since 5.0)

Saves the regions of the selected view as a device entry of the library.

Usage: `redact:save [-h] [--json] [--entry=<entry-id>] [--name=<name>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --entry | entry-id |  | Entry to replace (default: a new one). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --name | string |  | Name of the entry (default: the station name of the series). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |


## script

Runs a list of commands from a file, with variables and expectations.

### script:check (since 5.0)

Parses every step against the command models without running anything, and answers what would fail.

Usage: `script:check [-h] [--json] --file=<input-path>`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --file | input-path |  | The script to read, one command per line. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |

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
| --brief | boolean |  | Leave the body of each step out of the answer. (since 5.0) |
| --delay | integer | 0 | Wait this long between steps (default: 0). (since 5.0) |
| --file | input-path |  | The script to read, one command per line. (since 5.0) |
| --from | integer | 1 | First step to run, counting from 1. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --keep | boolean |  | Leave the display as the failing step left it, instead of restoring the checkpoint taken before the first step (the default for a remote caller). (since 5.0) |
| --keep-going | boolean |  | Run the remaining steps after a failing one. (since 5.0) |
| --report | output-path |  | Write a JUnit XML report of the run to this file. (since 5.0) |
| --rollback | boolean |  | Restore the checkpoint taken before the first step when a step fails (the default for a remote caller; a script of the launch arguments keeps the display). (since 5.0) |
| --set | string | {} | A parameter the script reads as null. (since 5.0) |
| --timeout | integer | 0 | Give up after this long, 0 to wait as long as it takes. (since 5.0) |
| --to | integer |  | Last step to run (default: the last one). (since 5.0) |


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
| --from | string |  | The older checkpoint, from 'checkpoints'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
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
| --dicom | boolean |  | Writes DICOM objects instead of the JSON. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --out | output-path |  | Target directory, created when missing. |
| --session | string |  | Session id from 'list'. |
| --study | study-uid |  | The most recent session showing this study instead of an id. |

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
| --from-checkpoint | string |  | Promotes a checkpoint from 'checkpoints' to a session instead of the display. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --label | string |  | A label for the list. |
| --pin | boolean |  | Keeps the session out of the retention by number. |

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
| --close-new | boolean |  | Close the studies loaded since the checkpoint. |
| --dry-run | boolean |  | Only say what differs between the checkpoint and the display. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --to | string |  | Checkpoint id from 'checkpoints' (default: the latest). |

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
| --absolute | boolean |  | Store the absolute difference. |
| --all-phases | boolean |  | Build one series per phase of the series minus the mask phase; the first one opens. |
| --factor | number |  | Weight of the mask: 1 subtracts it fully, below 1 keeps part of the anatomy (default: 1). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --mask | series-uid |  | The series subtracted, from 'dicom:list' (default: the series of the study without contrast agent, else the earliest compatible one). |
| --mask-frames | string |  | DSA: mask frames of the run itself, from 1 ('3', '2-4' averaged, '1,3,5'); the series is an X-ray run (default for a run: the frames its DICOM names, else frame 1). |
| --mask-phase | integer |  | Phase of the mask series to subtract (default: the first phase when the mask is the series itself). |
| --mip | boolean |  | Open the result as a maximum intensity projection through all its slices. |
| --motion-correction | boolean |  | Move the mask of each slice onto it by the translation phase correlation measures (default: what the pane previews, else off). |
| --no-open | boolean |  | Build the series without opening it. |
| --normalize | string |  | Weight the mask so that its mean equals the one of the series, over the whole slices or over a pixel rectangle 'x,y,width,height' of every slice: the dual-tracer normalization of nuclear medicine; overrides --factor. |
| --offset | number |  | Value added to every difference (default: 0). |
| --phase | integer |  | Phase of a dynamic series to subtract from, from 'subtraction:phases' (default: every slice of the series). |
| --series | series-uid |  | The series subtracted from, from 'dicom:list' (default: the series of the selected pane). |
| --shift | string |  | DSA: sub-pixel shift of the mask in pixels, down and right when positive. |
| --signed | boolean |  | Open with the signed window, negative differences visible, instead of black below zero. |
| --tid-offset | integer |  | DSA: time-interval mask, each frame minus the frame that many before it. |

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
| --frames | string |  | Mask frame numbers from 1: one ('3'), a range averaged ('2-4') or a list ('1,3,5'). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --tid-offset | integer |  | Time-interval mask: each frame minus the frame that many positions before it; negative for the frame after it. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

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
| --factor | number |  | Weight of the mask, 1 subtracts it fully (default: 1). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --mask | series-uid |  | The series subtracted, from 'dicom:list' (default: the series of the study without contrast agent, else the earliest compatible one). |
| --motion-correction | boolean |  | Moves the mask of each slice onto it by the translation phase correlation measures (true when given without a value; false switches it off). |
| --off | boolean |  | Leaves the preview, the plain series stays shown. |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### subtraction:shift (since 5.0)

Moves the mask of the X-ray run a pane shows by a sub-pixel shift, to correct the patient's motion between the mask and the contrast frames, and previews the subtraction. Positive values move the mask down and to the right.

Usage: `subtraction:shift [-h] [--json] [--columns=<pixels>] [--pane=<pane-index>] [--rows=<pixels>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --columns | number |  | Horizontal shift of the mask in pixels (default: unchanged). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --rows | number |  | Vertical shift of the mask in pixels (default: unchanged). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |


## ui

The workspace around the viewers: the UI profiles (shell slots, toolbars, menus, mouse bindings, measurement profile) that a hanging protocol or a reading step lays over it, the overflow of what the selected view hides, and the catalog of every action of the workspace (menu entries, toolbar buttons, tool panels). Profile ids come from 'ui:profiles', action ids from 'ui:actions'.

### ui:action (since 5.0)

Runs one action of 'ui:actions' by id, as its menu entry, button or stripe click would, and returns the active profile, the shell and the toolbar row after it.

Usage: `ui:action [-h] [--json] --id=<action-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --id | string |  | From 'actions'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |

### ui:actions (since 5.0)

The actions of the workspace as the command palette lists them: the menu entries, the toolbar buttons and the tool panels of the selected viewer, with a stable id, the group, the shortcut and whether the selected view shows them ('relevant'; a hidden one still runs). In text a hidden action is marked with '-'.

Usage: `ui:actions [-h] [--json] [--relevant] [--filter=<text>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --filter | string |  | Keeps the actions whose label, group or id contain every word, best first. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
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
| --apply | boolean |  | Lays --id over the workspace now. |
| --clear | boolean |  | Releases the active profile. |
| --delete | boolean |  | Removes --id from the user layer. |
| --id | string |  | Profile id from 'profiles'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |

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
| --tool | string |  | Tool id from 'hidden.tools'. |
| --toolbar | string |  | Toolbar id from 'hidden.toolbars'. |


## view2d

Display of a 2D DICOM view: zoom, window/level, pan, scrolling, grid, mouse action, and the agent verbs capture, stats and measure. Every verb works on the pane given by --view and --pane, the selected one by default, and returns its display state.

### view2d:annotate (since 5.0)

Adds a text annotation on the AI layer of a pane: with --at it points at that place with an arrow, with --label-at alone it is a caption, text where you put it and nothing else drawn.

Usage: `view2d:annotate [-h] [--canvas] [--json] [--at=<image-point>] [--label-at=<image-point>] [--pane=<pane-index>] --text=<text> [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --at | image-point |  | Place the annotation points at; without it the text stands alone. |
| --canvas | boolean |  | Coordinates are canvas pixels (as in a capture) instead of image pixels. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --label-at | image-point |  | Position of the text itself (default: above and right of --at). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --text | string |  | Text to show. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:capture (since 5.0)

Renders a view as displayed (window/level, annotations, identity masking) into a PNG or JPEG file and returns how image pixels map to the capture; --frame image renders the image alone at its own size, for exact comparisons.

Usage: `view2d:capture [-h] [--force] [--json] [--annotations=<on|off>] [--crop=<canvas-rect>] [--frame=<frame>] [--max-pixels=<n>] --out=<output-path> [--pane=<pane-index>] [--view=<view-id>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --annotations | enum |  | Text, scale, orientation, drawings and cross-lines: on or off (default: as displayed for the canvas, off for the image frame). (since 5.0) |
| --crop | canvas-rect |  | Crop to this region of the canvas before scaling. (since 5.0) |
| --force | boolean |  | Replace the file when it exists and was not written by this session. (since 5.0) |
| --frame | enum | canvas | canvas (default): the pane as displayed, its size and zoom; image: one pixel per displayed image pixel, whatever the pane, the zoom and the pan. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --max-pixels | integer |  | Downscale so the file has at most this many pixels (default: 2000000 for the canvas, none for the image frame). |
| --out | output-path |  | File to write; the extension selects PNG or JPEG (.jpg). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:clear-ai (since 5.0)

Removes every graphic drawn by the agent in the view.

Usage: `view2d:clear-ai [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:flip (since 5.0)

Mirrors the image of a pane and returns its display state; with no argument the state is returned unchanged. A vertical flip is the horizontal one turned by 180°, the form DICOM presentation states use.

Usage: `view2d:flip [-h] [--json] [--axis=<flip-axis>] [--pane=<pane-index>] [--view=<view-id>] [on|off]`

| Parameter | Type | Description |
|---|---|---|
| on\|off | enum | on, off. |

| Option | Type | Default | Description |
|---|---|---|---|
| --axis | enum | horizontal | horizontal or vertical (default: horizontal). (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:fusion (since 5.0)

Blends another series of the study over the one a pane shows (PET over CT, SPECT over MR...): --series starts or replaces the overlay, --color-map and --opacity change the blend, --off removes it; the display state returns the blend under 'blend'. With no argument the state is returned unchanged.

Usage: `view2d:fusion [-h] [--json] [--off] [--color-map=<color-map-id>] [--opacity=<0-1>] [--pane=<pane-index>] [--series=<series-uid>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --color-map | string |  | Color map of the overlay, from 'color:maps'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --off | boolean |  | Removes the overlay. |
| --opacity | number |  | Opacity of the overlay, 0 transparent to 1 opaque. |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --series | series-uid |  | The overlay series, from 'dicom:list'; it must share the frame of reference. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:graphics (since 5.0)

Lists the graphics of the view: id, type, layer, vertices and measurements, so measured values come from the viewer rather than from a picture.

Usage: `view2d:graphics [-h] [--json] [--no-measurements] [--after=<n>] [--layer=<layer-name>] [--limit=<n>] [--pane=<pane-index>] [--view=<view-id>]`

Produces: `graphic-id`

| Option | Type | Default | Description |
|---|---|---|---|
| --after | integer |  | Skip the first n entries, as the previous answer's 'next' says. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --layer | layer-name |  | Only the graphics of this layer name, e.g. AI. |
| --limit | integer |  | At most this many entries (default: all of them). (since 5.0) |
| --no-measurements | boolean |  | Skip the measurement values. |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

| Result | Type | Description |
|---|---|---|
| target | object | the view and pane the command resolved |
| target.view | view-id | the view |
| target.pane | pane-index | the pane in its grid |
| target.viewerType | string | the viewer type id |
| target.series | series-uid | the series the pane shows |
| target.image | image-index | the image shown, from 1 |
| target.imageCount | integer | images of the series |
| graphics | array | the graphics of the image |
| graphics[].id | graphic-id | the graphic id |
| graphics[].type | string | the graphic type |
| graphics[].layer | object | the layer of the graphic |
| graphics[].layer.name | layer-name | the layer name |
| graphics[].layer.type | string | the layer type |
| graphics[].points | array | the points in image pixels, as x,y pairs |
| graphics[].labels | array | the labels drawn next to the graphic |
| graphics[].measurements | array | the measured values of the graphic |
| graphics[].measurements[].key | string | stable name of the measurement |
| graphics[].measurements[].name | string | English name |
| graphics[].measurements[].value | number | the value |
| graphics[].measurements[].unit | string | UCUM code of the unit |
| graphics[].measurements[].provenance | object | where the calibration comes from |
| total | integer | graphics before the window |
| truncated | boolean | true when the window cut the list |
| next | integer | offset of the next window, when truncated |

### view2d:grid

Grid of panes of a viewer: with no argument it lists the grids it offers, --panes and --grid apply one and return the state of the viewer, --best-for answers without changing anything. An unknown grid id fails.

Usage: `view2d:grid [-h] [--json] [--view=<view-id>] [--best-for=<n> | --grid=<grid-id> | --panes=<n>]`

Produces: `grid-id`

| Option | Type | Default | Description |
|---|---|---|---|
| --best-for | integer |  | Returns the best grid for this number of panes without applying it. (since 5.0) |
| --grid | grid-id |  | Applies this grid, by id from the bare verb. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --panes | integer |  | Applies the best grid for this number of panes. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

| Result | Type | Description |
|---|---|---|
| grids | array | the grids the view offers, when none was applied |
| grids[].grid | grid-id | the grid id |
| grids[].panes | integer | panes of the grid |
| grids[].selected | boolean | true for the grid in use |
| grid | grid-id | the grid applied, when one was |
| panes | integer | panes of the grid applied |

### view2d:hang (since 5.0)

Hangs series in the cells of a viewer in one call: --grid picks the grid, each --cell puts a series in a cell (an empty value clears it), --tiled tiles a cell, --sync and --select set the synchronization and the selected cell; --file applies a layout saved by 'workspace:save'. The state of the viewer is returned. With no argument the current layout is returned, in the form --file takes.

Usage: `view2d:hang [-h] [--json] [--file=<input-path>] [--grid=<grid-id>] [--select=<cell>] [--sync=<sync-mode>] [--view=<view-id>] [--cell=<cell>=<series-uid>]... [--tiled=<cell>=<cols>x<rows>]...`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --cell | string |  | Series shown in that cell, from 1; an empty value clears the cell. |
| --file | input-path |  | A layout written by 'workspace:save' or by this verb without argument. |
| --grid | grid-id |  | Grid to apply, by id from 'grid'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --select | integer |  | Cell to select, from 1. |
| --sync | enum |  | none, stack or tile, among the modes the viewer offers. |
| --tiled | string |  | Tiles of that cell; 1x1 returns it to a single image. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:measure (since 5.0)

Draws a measurement on the AI layer of the view and returns its values, in the spatial unit of the viewer (mm when the image is calibrated).

Usage: `view2d:measure [-h] [--canvas] [--json] [--pane=<pane-index>] --points=<image-polygon> [--tool-key=<key>] [--type=<type>] [--view=<view-id>]`

Produces: `graphic-id`

| Option | Type | Default | Description |
|---|---|---|---|
| --canvas | boolean |  | Coordinates are canvas pixels (as in a capture) instead of image pixels. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --points | image-polygon |  | Vertices as x,y pairs; rectangle and ellipse take two opposite corners. |
| --tool-key | string |  | A tool key from 'measure:tools', for the tools --type does not name. |
| --type | enum |  | LINE, POLYLINE, RECTANGLE, ELLIPSE, CIRCLE, POLYGON, ANGLE; or --tool-key for any tool of the registry. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

| Result | Type | Description |
|---|---|---|
| target | object | the view and pane the command resolved |
| target.view | view-id | the view |
| target.pane | pane-index | the pane in its grid |
| target.viewerType | string | the viewer type id |
| target.series | series-uid | the series the pane shows |
| target.image | image-index | the image shown, from 1 |
| target.imageCount | integer | images of the series |
| id | graphic-id | the graphic drawn |
| type | string | the graphic type |
| layer | object | the layer of the graphic |
| layer.name | layer-name | the layer name, AI for a remote caller |
| layer.type | string | the layer type |
| measurements | array | the measured values of the graphic |
| measurements[].key | string | stable name of the measurement |
| measurements[].name | string | English name |
| measurements[].value | number | the value |
| measurements[].unit | string | UCUM code of the unit |
| measurements[].provenance | object | where the calibration comes from |

### view2d:mouse-action

Binds an action to a mouse button and returns the bindings; with no action the current ones are returned unchanged.

Usage: `view2d:mouse-action [-h] [--json] [--action=<mouse-action-type>] [--button=<mouse-button>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --action | enum |  | sequence, window-level, zoom, pan, rotation, crosshair, measure, draw, context-menu or none. (since 5.0) |
| --button | enum | left | left, middle, right or wheel (default: left). (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |

### view2d:pan

Moves the image inside a pane by an offset in canvas pixels and returns its display state; with no argument the state is returned unchanged.

Usage: `view2d:pan [-h] [--json] [--dx=<canvas-pixels>] [--dy=<canvas-pixels>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --dx | integer |  | Horizontal offset, positive to the right. (since 5.0) |
| --dy | integer |  | Vertical offset, positive downwards. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:preset (since 5.0)

Applies a window/level preset to a pane and returns its display state, or lists the presets of its image when none is given. The presets belong to the image, so the pane given by --view and --pane is selected first.

Usage: `view2d:preset [-h] [--json] [--pane=<pane-index>] [--preset=<preset-id>] [--view=<view-id>]`

Produces: `preset-id`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --preset | preset-id |  | Preset id, or its name as the viewer shows it. (since 5.0) |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

| Result | Type | Description |
|---|---|---|
| presets | array | the presets of the series, the selected one marked |
| presets[].preset | preset-id | the preset id |
| presets[].name | string | the name shown |
| presets[].selected | boolean | true for the preset applied |
| presets[].window | window-width | the window width |
| presets[].level | window-center | the window center |

### view2d:redo (since 5.0)

Redoes the last undone change of the drawings of a pane.

Usage: `view2d:redo [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:reset

Puts the display of a pane back as the image opened, whole or tool by tool, and returns its state; with no argument the state is returned unchanged.

Usage: `view2d:reset [-h] [--all] [--json] [--pane=<pane-index>] [--view=<view-id>] [--tool=<reset-tool>]...`

| Option | Type | Default | Description |
|---|---|---|---|
| --all | boolean |  | Reset everything to the original display. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --tool | enum | [] | window-level, zoom, pan or rotation; repeat it for several. (since 5.0) |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:rotate (since 5.0)

Turns the image of a pane clockwise and returns its display state; with no argument the state is returned unchanged.

Usage: `view2d:rotate [-h] [--json] [--by=<degrees>] [--degrees=<degrees>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --by | integer |  | Turns by this many degrees, negative counter-clockwise. (since 5.0) |
| --degrees | degrees |  | Absolute angle, clockwise; any integer, reduced to 0-359. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:scroll

Moves through the images of the series a pane shows; with no argument the current position is returned unchanged.

Usage: `view2d:scroll [-h] [--json] [--pane=<pane-index>] [--view=<view-id>] [--by=<images> | --image=<image-index>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --by | integer |  | Moves by this many images, negative towards the first one. |
| --image | image-index |  | Image to show, from 1. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:sort (since 5.0)

Orders the images of the stack a pane shows, by a sort key and a direction, and returns the display state ('sort', 'inverseStack'); with no argument the state is returned unchanged. 'slice-position' orders along the positive patient axis whatever the acquisition direction (feet to head for an axial stack, right to left, front to back), so --inverse true reads head to feet.

Usage: `view2d:sort [-h] [--json] [--inverse=<true|false>] [--key=<sort-key>] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --inverse | boolean |  | Reads the stack backwards. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --key | string |  | instance, slice-position, slice-location, content-time, acquisition-time, b-value. |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:stats (since 5.0)

Pixel statistics of a region (min, max, mean, median, standard deviation, ...) in the image value unit, e.g. HU, plus the region geometry in the spatial unit.

Usage: `view2d:stats [-h] [--canvas] [--json] [--pane=<pane-index>] [--view=<view-id>] (--ellipse=<image-rect> | --graphic=<graphic-id> | --polygon=<image-polygon> | --rect=<image-rect>)`

| Option | Type | Default | Description |
|---|---|---|---|
| --canvas | boolean |  | Coordinates are canvas pixels (as in a capture) instead of image pixels. |
| --ellipse | image-rect |  | Ellipse inscribed in this rectangle. |
| --graphic | graphic-id |  | Id of an existing area graphic of the view, from 'graphics'. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --polygon | image-polygon |  | Polygon vertices as x,y pairs (three at least). |
| --rect | image-rect |  | Axis-aligned rectangle, in image pixels unless --canvas is given. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:sync

Sets how the panes of a viewer follow each other and returns its state: none turns the synchronization off, another mode turns it on; with no argument the state is returned unchanged.

Usage: `view2d:sync [-h] [--json] [--mode=<sync-mode>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --mode | enum |  | none, stack or tile, among the modes the viewer offers. (since 5.0) |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:undo (since 5.0)

Undoes the last change of the drawings of a pane (a graphic added, moved, resized, deleted or pasted, by the mouse or by a command) and returns whether something was undone and what remains to undo or redo. Closing a view or changing its series clears the history.

Usage: `view2d:undo [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:viewport (since 5.0)

Viewport of a pane: --tiled shows its series on a grid of tiles, --stack returns it to one image; the state of the viewer, with its cells, is returned. With no argument the state is returned unchanged.

Usage: `view2d:viewport [-h] [--json] [--pane=<pane-index>] [--view=<view-id>] [--stack | --tiled=<cols>x<rows>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --stack | boolean |  | Returns the pane to a single image. (since 5.0) |
| --tiled | string |  | Shows the series of the pane on this grid of tiles, successive images. (since 5.0) |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### view2d:window-level

Window and level of a pane: absolute with --window and --level, relative with --window-by and --level-by in slider steps (range 4096). With no argument the state is returned unchanged.

Usage: `view2d:window-level [-h] [--json] [--level-by=<steps>] [--pane=<pane-index>] [--view=<view-id>] [--window-by=<steps>] [--window=<window-width> --level=<window-center>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --level | window-center |  | Centre of the window, in the unit of the pixels. (since 5.0) |
| --level-by | integer |  | Raises the level by this many slider steps, negative to lower it. (since 5.0) |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |
| --window | window-width |  | Width of the window, in the unit of the pixels. (since 5.0) |
| --window-by | integer |  | Widens the window by this many slider steps, negative to narrow it. (since 5.0) |

### view2d:zoom

Changes the zoom of a pane and returns its display state; with no argument the state is returned unchanged.

Usage: `view2d:zoom [-h] [--json] [--pane=<pane-index>] [--view=<view-id>] [--by=<steps> | --fit=<zoom-fit> | --set=<magnification>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --by | integer |  | Moves the zoom slider by this many steps, negative to zoom out. |
| --fit | enum |  | default, real-size or best-fit. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --set | magnification |  | Magnification, 1 being one image pixel per canvas pixel. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

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

### weasis:feedback (since 5.0)

The feedback channel of a preview: the link to the issue form of the 5.0 preview with the version and the platform of this instance filled in, and the discussions category for questions; --open opens the form in the browser.

Usage: `weasis:feedback [-h] [--json] [--open] [--feature=<text>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --feature | string |  | The feature of the finding, preselected in the form. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --open | boolean |  | Open the form in the default browser. |

| Result | Type | Description |
|---|---|---|
| url | string | The issue form with the version and the platform filled in |
| discussions | string | The discussions category of the preview |
| version | string | The version of this instance |
| preview | boolean | Whether this instance is a preview build |
| platform | string | Operating system, architecture and monitors |
| opened | boolean | Whether the form was opened in the browser |

### weasis:info

Version and installation details, the version being one of the keys. The 4.x option -v still answers with the version alone.

Usage: `weasis:info [-h] [--integrity] [--json] [--plugins]`

| Option | Type | Default | Description |
|---|---|---|---|
| --integrity | boolean |  | The integrity of the configuration and the plugins: the level reached (signed, digest, none), the signing key, the entries verified and the failures. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --plugins | boolean |  | The loaded-plugins manifest: policy, lock, fingerprint and every plugin. (since 5.0) |

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
| --bounds | screen-rect |  | Screen bounds of the new window, as x,y,width,height. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --screen | screen-role |  | Screen by role: control, diagnostic-1, diagnostic-2, color, portrait. |
| --view | view-id |  | View id from 'workspace:list'. |

### workspace:fullscreen (since 5.0)

Puts a window on the full bounds of its monitor without decorations: the window of --view, else the active window. --off ends it. A window state, kept by 'workspace:save' and restored by 'workspace:load'.

Usage: `workspace:fullscreen [-h] [--canvas] [--json] [--off] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --canvas | boolean |  | The selected pane of the view alone fills the viewer, the window and the monitor (what F11 and a double click do); --off ends it. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --off | boolean |  | Ends the fullscreen state of that window. |
| --view | view-id |  | View id from 'workspace:list' whose window is meant (default: the active one). |

### workspace:list

Windows, tab groups and views as JSON.

Usage: `workspace:list [-h] [--json] [--after=<n>] [--limit=<n>]`

Produces: `view-id`

| Option | Type | Default | Description |
|---|---|---|---|
| --after | integer |  | Skip the first n entries, as the previous answer's 'next' says. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --limit | integer |  | At most this many entries (default: all of them). (since 5.0) |

| Result | Type | Description |
|---|---|---|
| selected | view-id | the selected view |
| windows | array | the windows, the main one first |
| windows[].window | integer | window number, from 1 |
| windows[].main | boolean | true for the main window |
| windows[].groups | array | the tab groups of the window |
| windows[].groups[].group | integer | tab group number, from 1 |
| windows[].groups[].views | array | the views of the tab group |
| windows[].groups[].views[].id | view-id | the view id |
| windows[].groups[].views[].name | string | the title of the view |
| windows[].groups[].views[].selected | boolean | true for the selected view |
| windows[].groups[].views[].viewerType | string | the viewer type id |
| windows[].groups[].views[].tool | tool-id | the tool shown, for a tool opened as a view |
| windows[].groups[].views[].grid | grid-id | the grid of an image view |
| windows[].groups[].views[].sync | string | the synchronization mode of an image view |
| windows[].groups[].views[].panes | array | the panes of an image view |
| windows[].groups[].views[].panes[].pane | pane-index | the pane index |
| windows[].groups[].views[].panes[].selected | boolean | true for the selected pane |
| windows[].groups[].views[].panes[].series | series-uid | the series shown |
| windows[].groups[].views[].panes[].image | image-index | the image shown, from 1 |
| windows[].groups[].views[].panes[].imageCount | integer | images of the series |

### workspace:load

Restores a layout captured by 'workspace:save': open views are reused, closed viewers are created again with their series and presentation when the series are still loaded; the shell (explorer and tool slots) follows when the snapshot has one.

Usage: `workspace:load [-h] [--json] --file=<input-path>`

Needs: file read

| Option | Type | Default | Description |
|---|---|---|---|
| --file | input-path |  | Snapshot file to restore. |
| --json | boolean |  | Structured JSON output (default for remote calls). |

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
| --screen | screen-role |  | Screen by role: control, diagnostic-1, diagnostic-2, color, portrait. |
| --view | view-id |  | View id from 'workspace:list'. |

### workspace:panel (since 5.0)

Shell slots around the workspace: the explorer side (leading) and the tools side (trailing), two stacked slots each, and the central strip. Without option it returns the shell state; --side selects a side, then --slot with --tool or --clear fills or empties a slot, --collapse / --expand hide or show the side, --width and --ratio size it. Tool ids are stable across viewer types; an id the side does not offer now fails and lists the ones it does.

Usage: `workspace:panel [-h] [--as-view] [--clear] [--collapse] [--expand] [--hide] [--json] [--no-strip] [--show] [--ratio=<split-ratio>] [--side=<side>] [--slot=<slot>] [--strip=<tool-id>] [--tool=<tool-id>] [--width=<pixels>]`

Produces: `tool-id`

| Option | Type | Default | Description |
|---|---|---|---|
| --as-view | boolean |  | Shows --tool of --side as a tab of the active window instead of a slot, where it gets width and floats like any view; --tool with --slot brings it back. |
| --clear | boolean |  | Empties --slot of --side. |
| --collapse | boolean |  | Hides both slots of --side (tools remembered). |
| --expand | boolean |  | Shows the remembered tools of --side again. |
| --hide | boolean |  | Hides --tool of --side for good: the user's choice, kept in the setting weasis.tool.<id>.visible (see config:setting), until --show. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --no-strip | boolean |  | Empties the central strip. |
| --ratio | split-ratio |  | Share of the top slot of --side when both slots are shown. |
| --show | boolean |  | Shows --tool of --side again after --hide. |
| --side | enum |  | Side by role: EXPLORER, TOOLS. |
| --slot | enum |  | Slot of the side: TOP, BOTTOM. |
| --strip | tool-id |  | Keeps the central strip on this tool (fails when another tool is shown). |
| --tool | tool-id |  | Opens this tool in --slot of --side, replacing what was there. |
| --width | integer |  | Content width of --side, in pixels. |

| Result | Type | Description |
|---|---|---|
| explorer | object | the explorer side of the shell |
| explorer.slots | tool-id[] | the tool of each slot, null when empty |
| explorer.width | integer | content width in pixels |
| explorer.ratio | number | share of the first slot |
| explorer.collapsed | boolean | true when the side is hidden |
| tools | object | the tools side of the shell |
| tools.slots | tool-id[] | the tool of each slot, null when empty |
| tools.width | integer | content width in pixels |
| tools.ratio | number | share of the first slot |
| tools.collapsed | boolean | true when the side is hidden |
| strip | string | how the hidden tools are shown: mini or none |

### workspace:save

Captures the layout as JSON, inline or to a file.

Usage: `workspace:save [-h] [--force] [--json] [--out=<output-path>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --force | boolean |  | Replace the file when it exists and was not written by this session. (since 5.0) |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --out | output-path |  | Write the snapshot to this file and report its path. |

### workspace:screens (since 5.0)

The screens of the workstation by role (control, diagnostic-1, diagnostic-2, color, portrait) with the monitor each one resolves to: the main window's screen is the control screen, the others are derived left to right, and a role the user gave a monitor on the Screens preference page wins. A role without a monitor of its own resolves to the nearest one and is reported as not fulfilled. --id with --role gives a monitor a role, --id with --auto takes it back.

Usage: `workspace:screens [-h] [--auto] [--json] [--id=<screen-id>] [--role=<screen-role>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --auto | boolean |  | Clears the role of --id; it is derived again. |
| --id | string |  | A screen id from the listing, to give it a role or take it back. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --role | screen-role |  | The role of --id: control, diagnostic-1, diagnostic-2, color, portrait. |

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
| --anchor | view-id |  | View whose group is split. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --ratio | split-ratio | 0.5 | Share of the space given to the anchor (default: 0.5). |
| --side | enum |  | Side of the anchor for the new group: TOP, BOTTOM, LEFT, RIGHT. |
| --view | view-id |  | View id from 'workspace:list'. |

### workspace:stack

Moves a view into the tab group of another view.

Usage: `workspace:stack [-h] [--json] --anchor=<view-id> --view=<view-id>`

| Option | Type | Default | Description |
|---|---|---|---|
| --anchor | view-id |  | View whose tab group receives the view. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --view | view-id |  | View id from 'workspace:list'. |

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
| --group | string |  | Groups by their id from the list, comma separated (default: every group). |
| --hide | boolean |  | Hide the groups. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --show | boolean |  | Draw the groups. |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### wsi:export (since 5.0)

Writes the measurements and drawings of the slide of a pane as a DICOM Structured Report (TID 1500 Measurement Report, one planar region per graphic on the base level of the slide, lengths and areas in micrometers), a file that 'dicom:load' brings back as regions over the slide and that other viewers read.

Usage: `wsi:export [-h] [--force] [--json] --out=<output-path> [--pane=<pane-index>] [--view=<view-id>]`

Needs: file write

| Option | Type | Default | Description |
|---|---|---|---|
| --force | boolean |  | Replace the file when it exists and was not written by this session. |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --out | output-path |  | The DICOM file to write (.dcm). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |

### wsi:goto (since 5.0)

Shows a place of the slide: --at centres the view on a base level pixel, --zoom sets the scale (1: one base pixel per canvas pixel) or --objective the magnification shown (20 for 20x), --focal-plane and --optical-path pick the plane of a Z-stack and the channel of a multi-path slide; --complete answers once every tile of the view is loaded, so that a capture is sharp. With no argument the display comes back unchanged.

Usage: `wsi:goto [-h] [--complete] [--json] [--at=<image-point>] [--focal-plane=<index>] [--objective=<number>] [--optical-path=<optical-path-id>] [--pane=<pane-index>] [--view=<view-id>] [--zoom=<magnification>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --at | image-point |  | Base level pixel x,y put at the centre of the view. |
| --complete | boolean |  | Answer once every tile of the view is loaded, after 10 s at the latest. |
| --focal-plane | integer |  | Focal plane of a Z-stack shown, from 1 ('focalPlanes' of 'wsi:info'). |
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --objective | number |  | Magnification shown, as an objective power: 20 shows the slide as a 20x objective would (needs the objective power or the pixel spacing of the slide). |
| --optical-path | string |  | Optical path shown, by its identifier ('opticalPaths' of 'wsi:info'). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |
| --zoom | magnification |  | Scale of the view, 1 being one base level pixel per canvas pixel. |

### wsi:info (since 5.0)

Describes the slide of a pane: its size in base level pixels, pixel spacing, objective magnification, pyramid levels, focal planes, optical paths and associated images, and what the pane shows: zoom, magnification, centre, viewport, level and tiles.

Usage: `wsi:info [-h] [--json] [--pane=<pane-index>] [--view=<view-id>]`

| Option | Type | Default | Description |
|---|---|---|---|
| --json | boolean |  | Structured JSON output (default for remote calls). |
| --pane | pane-index |  | Pane index in the grid of the view, from 1 (default: the selected pane). |
| --view | view-id |  | View id from 'workspace:list' (default: the selected view). |


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

## Types

| Type | Definition | From | Used by |
|---|---|---|---|
| canvas-rect | a rectangle in the canvas pixels of a pane: x,y,width,height |  | view2d:capture |
| degrees | an angle in degrees, clockwise, reduced to 0-359 |  | view2d:rotate |
| entry-id | the id of a device entry, from redact:library or redact:match (handle) |  | redact:apply, redact:delete, redact:save |
| graphic-id | the id of one graphic of an image, from the verbs that draw and from view2d:graphics (handle) | view2d:graphics, view2d:measure | measure:calibrate, view2d:stats |
| grid-id | the id of one grid of panes a view can take, from the state the view2d verbs return (handle) | view2d:grid | view2d:grid, view2d:hang |
| image-index | the position of an image in the series a pane shows, counted from 1 |  | dicom:tag, view2d:scroll |
| image-point | a point in image pixels, origin at the top-left corner of the image |  | view2d:annotate, wsi:goto |
| image-polygon | an outline in image pixels, two points at least |  | redact:add, view2d:measure, view2d:stats |
| image-rect | a rectangle in image pixels: x,y,width,height |  | redact:add, view2d:stats |
| input-path | a local file the verb reads; the policy decides what a remote caller may read |  | color:set, config:import-legacy, config:unpack, dicom:load, hp:apply, hp:features, hp:import, hp:match, hp:validate, image:compare, image:load, measure:profile, plugins:add, redact:import, script:check, script:run, view2d:hang, workspace:load |
| layer-name | the name of a layer of graphics in a view |  | measure:export, view2d:graphics |
| magnification | how much an image is magnified in a pane: 1 is one image pixel per canvas pixel |  | view2d:zoom, wsi:goto |
| masking-profile-id | the id of a masking profile of the configuration |  | redact:export |
| output-path | a local file the verb writes; the policy decides where a remote caller may write |  | config:catalog, config:pack, hp:export, measure:export, measure:profile, print:render, print:send, redact:export, script:run, state:export, view2d:capture, workspace:save, wsi:export |
| pane-index | the position of a pane in the grid of a view, counted from 1 |  | color:status, dicom:open, measure:calibrate, measure:export, print:page, redact:add, redact:apply, redact:attest, redact:clear, redact:list, redact:match, redact:save, subtraction:mask, subtraction:preview, subtraction:shift, view2d:annotate, view2d:capture, view2d:clear-ai, view2d:flip, view2d:fusion, view2d:graphics, view2d:measure, view2d:pan, view2d:preset, view2d:redo, view2d:reset, view2d:rotate, view2d:scroll, view2d:sort, view2d:stats, view2d:undo, view2d:viewport, view2d:window-level, view2d:zoom, wsi:annotations, wsi:export, wsi:goto, wsi:info |
| patient-handle | the masked patient id of a loaded patient, from dicom:list, stable for the session (handle) | dicom:list | dicom:unload |
| preset-id | the id of a window level preset, from view2d:preset (handle) | view2d:preset | view2d:preset |
| remote-url | an absolute URL; the policy decides whether a remote caller may use it |  | dicom:load, dicom:load-dicomweb, image:load |
| screen-rect | a rectangle in screen pixels: x,y,width,height |  | workspace:detach |
| screen-role | the role a screen plays in the window model |  | hp:apply, workspace:detach, workspace:move, workspace:screens |
| series-uid | the Series Instance UID, or the series id, of a loaded series, from dicom:list (handle) | dicom:list | dicom:info, dicom:interest, dicom:objects, dicom:open, dicom:tag, dicom:unload, explorer:reveal, explorer:select, mpr:delete, mpr:prefetch, mpr:reformat, subtraction:create, subtraction:delete, subtraction:info, subtraction:phases, subtraction:preview, view2d:fusion |
| split-ratio | the share of the space given to the first side of a split, strictly between 0 and 1 |  | workspace:panel, workspace:split |
| study-uid | the Study Instance UID of a loaded study, from dicom:list (handle) | dicom:list | dicom:interest, dicom:objects, dicom:unload, explorer:filter, hp:apply, hp:compare, hp:features, hp:match, hp:open, state:export, state:restore, subtraction:list |
| tool-id | the id of a tool of the shell, from workspace:panel (handle) | workspace:panel | workspace:panel |
| view-id | the id of a view, from workspace:list (handle) | dicom:open, workspace:list | color:status, dicom:info, dicom:open, dicom:tag, hp:correct, hp:explain, hp:group, hp:save, hp:sets, measure:calibrate, measure:export, print:page, redact:add, redact:apply, redact:attest, redact:clear, redact:list, redact:match, redact:save, subtraction:mask, subtraction:preview, subtraction:shift, view2d:annotate, view2d:capture, view2d:clear-ai, view2d:flip, view2d:fusion, view2d:graphics, view2d:grid, view2d:hang, view2d:measure, view2d:pan, view2d:preset, view2d:redo, view2d:reset, view2d:rotate, view2d:scroll, view2d:sort, view2d:stats, view2d:sync, view2d:undo, view2d:viewport, view2d:window-level, view2d:zoom, workspace:close, workspace:detach, workspace:fullscreen, workspace:maximize, workspace:move, workspace:select, workspace:split, workspace:stack, workspace:unmaximize, wsi:annotations, wsi:export, wsi:goto, wsi:info |
| window-center | the window center applied to the pixel values, in their own unit |  | view2d:window-level |
| window-width | the window width applied to the pixel values, in their own unit |  | view2d:window-level |

## Glossary

| Term | Definition | Do not use |
|---|---|---|
| anatomic region | the coded region of the anatomy a series shows, with Body Part Examined as alias |  |
| anchor | the view a new split or stack is placed against |  |
| annotation | a text graphic |  |
| candidate | a protocol that matches a study, with its match quality |  |
| canvas | the pixel space of a pane after zoom, pan, rotation and flip |  |
| capture | the pixels of a pane, a view or a window written to an image file |  |
| cell | one image place of a printed page or of a protocol layout |  |
| checkpoint | a saved display state a transaction can roll back to |  |
| checkpoint id | the handle of a checkpoint, from state:checkpoints |  |
| close | remove a view, a study or a patient from the workspace |  |
| color map | a named mapping of the pixel values to colors applied to a pane |  |
| current | the study being read, as opposed to a prior |  |
| destination | where a print job goes: a system printer, a DICOM printer or a file |  |
| device entry | a saved set of redaction regions matched by the attributes of a device | template |
| document | a JSON file of settings or of a list (nodes, presets, protocols), with its id, schema and layers |  |
| download | transfer the pixel data of a loaded series from its source; a loaded series may be not downloaded under a selective hanging protocol | fetch |
| entry | one item of a document or a library, with its id |  |
| event | a change of state the instance publishes on its bus, named like the commands |  |
| format | the file format of an export: xml, csv, or what a plugin adds |  |
| frame | one frame of a multiframe DICOM instance |  |
| graphic | any drawn object on a pane (measurement, annotation, region) with a graphic id | shape |
| grid | the arrangement of panes inside a view |  |
| handle | an id a verb returns and another verb accepts; never invented by the caller |  |
| image | one displayable item of a series: an instance, or one frame of a multiframe instance |  |
| job | a print job: the pages prepared for a destination |  |
| layer | the named group a graphic belongs to; the AI layer holds what a remote caller drew |  |
| layout | the arrangement of windows, tab groups and views, saved as a snapshot |  |
| legacy installation | the ~/.weasis folder of a Weasis 4 release of the same account, read by the import and never written |  |
| level | one resolution of the pyramid of a slide, or one entry of a layered document |  |
| load | bring data into the explorer model, from files, a URL or an archive |  |
| mask | the series or frames subtracted from another in a subtraction |  |
| masking | identity values hidden in text, lists and events; redaction is reserved for pixels |  |
| masking profile | a named set of rules of the configuration that decides which identity values are hidden |  |
| measurement | a named value with a unit computed from a graphic or a region | metric |
| mip | a maximum intensity projection through a stack of images |  |
| motion correction | the per-image translation that aligns the mask with the series before subtracting |  |
| open | show a series in a view |  |
| page | one printed page, with its cells |  |
| pane | one image canvas inside the grid of a view, addressed by its index within the view | canvas index |
| patient | the DICOM patient as loaded in the explorer model | exam |
| phase | one time point of a dynamic series |  |
| plugin | a module loaded by the plugin manager, with its id, version and signature |  |
| policy | what the instance allows a caller to do: files, network, remote verbs, masking |  |
| position | where an image sits in its cell: center or top-left |  |
| preset | a named window width and window center pair |  |
| prior | an earlier study of the same patient hung beside the current one |  |
| protocol | a hanging protocol: the rules that hang a study, with its display sets and steps |  |
| provenance | where a derived value comes from: the providers, objects and calibration behind it |  |
| reason | why something was done or refused, as the why panel and the refusals say it |  |
| redaction | pixels hidden over an image; masking is reserved for identity values in text |  |
| region | a geometric area given by the caller (rect, ellipse, polygon) in a stated space | roi, zone |
| scope | what a redaction applies to: the image, the series or the study |  |
| screen | a physical display of the workstation, with a role in the window model |  |
| script | a file of commands run in one call, with variables and expectations |  |
| selected | the view, pane or series that receives a command when no handle is given | current view, active view, current pane, active pane, focused view |
| series | the DICOM series as loaded in the explorer model |  |
| session | a reading state kept as an exact restore of a study: views, panes, protocol and sidecar |  |
| slide | a whole slide image, with its pyramid of levels |  |
| slot | one of the two places of a side of the shell that holds a tool |  |
| step | one reading step of a protocol, or one line of a script |  |
| study | the DICOM study as loaded in the explorer model |  |
| tab group | a set of views shown as tabs in one area of a window | container, dock |
| target | the view and pane a command resolved, echoed in every result bound to a view |  |
| tid offset | time interval differencing: the number of frames between a frame and its mask |  |
| tile | one square of a level of a slide |  |
| tool | a panel of the workspace (explorer, image tool, measurements…), shown in a slot or as a view |  |
| tool key | the stable id of a measurement or drawing tool, from measure:tools |  |
| uid | a DICOM unique identifier, the handle of a study or a series |  |
| unload | remove data from the explorer model |  |
| view | one tab of the workspace, an instance of a viewer type showing series; its handle is the view id | viewer id |
| viewer type | a kind of view (2d, mpr, 3d, sr, …) able to open a given series |  |
| viewport | a pane that shows one series as a stack or as tiles of successive images, the stack and tiled modes of 4.8 |  |
| window | a top-level frame of the application, main or detached |  |
| window center | DICOM Window Center (0028,1050), the middle of the range of pixel values shown | window centre |
| window level | the window width and window center pair of a pane, always these words |  |
| window width | DICOM Window Width (0028,1051), the range of the pixel values shown |  |

