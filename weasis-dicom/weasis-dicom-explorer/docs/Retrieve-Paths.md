# DICOM Retrieve Paths

A developer guide to the five ways Weasis fills a series and the common download layer they all
feed.

## Table of Contents

- [The shared substrate](#the-shared-substrate)
- [The five transports](#the-five-transports)
- [Entry points](#entry-points)
- [Reference: files and symbols](#reference-files-and-symbols)

## The shared substrate

Whatever the protocol, a retrieve produces one task per series and hands it to the same download
manager. Anything built on top of this inherits stop, resume, priority and progress.

```
entry point ──> transport (C-GET, C-MOVE, WADO-URI, WADO-RS) ──> one LoadSeries task per series
                                                                   └─> DownloadManager queue
                                                                         └─> DicomModel, thumbnails, viewer
```

| Behaviour | Where it lives |
| --- | --- |
| One task per series, queued in a shared priority queue | `LoadSeries`, `DownloadManager` |
| Stop and resume, continuation built by `createResumeTask()` | `LoadSeries.cancelAndReplace` |
| Download priority, thumbnail selection promotes a series | `DownloadPriority`, `LoadSeries.setPriority()` |
| Patient / study / series nodes, progress bar and preview | `DicomModel`, `ThumbnailManager` |
| Whether a finished series opens a viewer | `PluginOpeningStrategy` |

Nothing downstream of `DownloadManager` knows which protocol produced a task.

## The five transports

| Transport | Protocol family | Query service | Download unit | Implemented by |
| --- | --- | --- | --- | --- |
| C-GET | DIMSE | C-FIND | whole series | `LoadQrSeries` |
| C-MOVE | DIMSE | C-FIND | whole series, received by a local store SCP | `LoadQrSeries` |
| WADO-URI | HTTP | C-FIND | one instance | `LoadWadoUriSeries` |
| WADO-RS bulk | HTTP | QIDO-RS | whole series | `SeriesDownloadManager` |
| WADO-RS manifest | HTTP | none, the manifest is the answer | one instance | `ManifestModelBuilder`, `SeriesDownloadManager` |

Invariants shared by every transport:

- **Resume is list, filter, fetch the remainder.** A resumed series lists its instances,
  drops the SOP Instance UIDs already held by the model (`LoadSeries.isSOPInstanceUIDExist`,
  `SeriesInstanceList`), then requests what is left.
- **Identity tags from the query answer are forced onto received objects**, so every transport
  produces the same patient / study / series identity in the model.
- **A QIDO query never inherits a retrieve `Accept` header.** Code that reuses retrieve-scoped
  headers for a query passes them through `RsQueryResult.jsonQueryParameters` first.
- **C-MOVE runs on `DownloadManager.UNIQUE_EXECUTOR`** because all objects return to one local
  listener; the other transports use `DownloadManager.CONCURRENT_EXECUTOR`.

## Entry points

| Entry point | Transports | Callable from code |
| --- | --- | --- |
| Query/Retrieve dialog (`DicomQrView`, `RetrieveTask`) | C-GET, C-MOVE, WADO-URI, WADO-RS | no, reads its parameters from the dialog |
| `dicom:rs` command (`RsQueryParams`, `RsQueryResult`) | WADO-RS | yes |
| `dicom:get` command (manifest) | WADO-URI and WADO-RS, via a manifest | yes |

The Query/Retrieve dialog expresses the series to fetch as a `RetrieveSelection`: checked
studies, each narrowed to chosen series.

## Reference: files and symbols

| Symbol | Module | Role |
| --- | --- | --- |
| `RetrieveTask` | `weasis-dicom-qr` | Drives a Query/Retrieve, one task per series |
| `RetrieveSelection` | `weasis-dicom-qr` | Checked studies, each narrowed to chosen series |
| `RetrieveContext` | `weasis-dicom-qr` | Shared DIMSE parameters, image-level C-FIND, store listener |
| `LoadQrSeries` | `weasis-dicom-qr` | One series retrieved with C-GET or C-MOVE |
| `LoadWadoUriSeries` | `weasis-dicom-qr` | One series queried with C-FIND, downloaded over WADO-URI |
| `LoadSeries` | `weasis-dicom-explorer` | Base download task: progress, stop, resume, priority |
| `SeriesDownloadManager` | `weasis-dicom-explorer` | Per-instance and bulk WADO-RS download, tag overrides |
| `DownloadManager` | `weasis-dicom-explorer` | Priority queue and the two executors |
| `RsQueryParams` / `RsQueryResult` | `weasis-dicom-explorer` | Headless QIDO/WADO-RS query behind `dicom:rs` |
| `ManifestModelBuilder` | `weasis-dicom-explorer` | Builds the model and tasks from a manifest |
| `PatientComparator` | `weasis-dicom-codec` | Patient pseudo UID, the cross-source identity key |
