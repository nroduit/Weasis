# DICOM Retrieve Paths

A developer guide to the five ways Weasis fills a series, what their behavior
already has in common, where it genuinely diverges, and what has to become
common before a hanging protocol can pull priors.

Everything downstream of the first received byte is one code path. The
divergences that remain are in what the archive is asked, how the answer
arrives, and — the part that actually blocks new features — who is allowed to
start a retrieve at all.

## Table of Contents

- [The shared substrate](#the-shared-substrate)
- [The five transports](#the-five-transports)
- [Entry points](#entry-points)
- [What has to become common for priors](#what-has-to-become-common-for-priors)
- [A shape that fits what exists](#a-shape-that-fits-what-exists)
- [Reference: files and symbols](#reference-files-and-symbols)

## The shared substrate

Whatever the protocol, a retrieve produces one task per series and hands it to
the same download manager. Anything built on top of this inherits stop, resume,
priority and progress for free.

| Behaviour | Where it lives |
| --- | --- |
| One task per series, queued in a shared priority queue | `LoadSeries`, `DownloadManager` |
| Stop and resume, continuation built by `createResumeTask()` | `LoadSeries.cancelAndReplace` |
| Three series in flight, thumbnail selection promotes and preempts | `DownloadPriority`, `LoadSeries.setPriority()` |
| Patient / study / series nodes, progress bar and preview | `DicomModel`, `ThumbnailManager` |
| Identity tags from the query answer forced onto received objects | `SeriesDownloadManager.applyOverrides` |
| Whether a finished series opens a viewer | `PluginOpeningStrategy` |

Nothing downstream of `DownloadManager` knows which protocol produced a task.

## The five transports

| Behaviour | C-GET | C-MOVE | WADO-URI | WADO-RS bulk | WADO-RS manifest |
| --- | --- | --- | --- | --- | --- |
| Protocol family | DIMSE | DIMSE | HTTP | HTTP | HTTP |
| Query service | C-FIND | C-FIND | C-FIND | QIDO-RS | none, the manifest *is* the answer |
| Download unit | whole series | whole series | one instance | whole series | one instance |
| Requests per series | 1 | 1 | N | 1 | N |
| Parallelism | 3 series | **1 series** | 3 series x 4 images | 3 series | 3 series x 4 images |
| Objects arrive on | the retrieve association | **a local store SCP** | the HTTP response | a multipart response | the HTTP response |
| First-pass listing | none | none | **image-level C-FIND** | none | read from the manifest |
| Resume listing | image-level C-FIND | image-level C-FIND | already listed | QIDO `/instances` | already listed |
| Resume filtering | before the request | before the request | at download time | at download time | at download time |
| Resume request | the missing UIDs, 500 per request | the missing UIDs, 500 per request | one per missing instance | one per missing instance | one per missing instance |
| Resume fallback | **the whole series** | **the whole series** | none | **the whole series** | none |
| Progress total | (0020,1209) from C-FIND | (0020,1209) from C-FIND | instance list size | (0020,1209) from QIDO | manifest instance count |
| Stop mechanism | C-CANCEL then abort | C-CANCEL then abort | close the HTTP stream | close the HTTP stream | close the HTTP stream |
| Credentials | calling AE title | calling AE title **+ a reachable listener port** | headers, OAuth2 | headers, OAuth2 | headers, OAuth2 |
| Identity fix-up | rewrite the stored file | rewrite the stored file | applied while writing | applied while writing | applied while writing |

Four behaviours are scattered across the rows above but follow one design. The
differences between transports are in mechanism, not in intent:

- **Resume is always list, filter, fetch the remainder.** A resumed series first
  lists its own content with an instance-level query — a single C-FIND at
  `QueryRetrieveLevel.IMAGE` keyed on the series, or a paged QIDO `/instances` —
  then drops the SOP Instance UIDs already held by the model, then asks for what
  is left. `SeriesInstanceList` is the shared carrier and
  `LoadSeries.isSOPInstanceUIDExist` the shared test, which also looks into the
  split siblings of the series.

  Where the filter sits still differs. The DIMSE paths filter first and send the
  missing UIDs as retrieve keys, so the archive never touches the rest. The HTTP
  paths enumerate the whole series and skip the stored instances when building
  the download tasks, so the listing is complete but the transfers are not.

  Both DIMSE and bulk WADO-RS keep a **series-level fallback**: if the archive
  refuses the instance-level retrieve, or if the enumeration comes back empty,
  the whole series is requested again rather than leaving the resume stuck. That
  path re-transfers what is already stored.
- **Identity fix-up follows the same rules everywhere**, through a single static.
  The DIMSE paths rewrite the received file because the object arrives complete;
  the HTTP paths apply the same overrides while streaming to disk.
- **A QIDO query never inherits a retrieve `Accept` header.** `parseJSON` sends
  whatever headers it is handed, so a node configured with the multipart Accept
  of a WADO-RS retrieve would ask a query for `multipart/related` and parse an
  empty result — a study whose series silently refuse to expand. Every site that
  reuses retrieve-scoped headers for a query passes them through
  `RsQueryResult.jsonQueryParameters` first. The launcher's own `--query-header`
  values are the deliberate exception: they are already query-scoped and are sent
  as configured.
- **The progress total comes from (0020,1209)** wherever the protocol can supply
  it, so the bar is determinate from the first frame without an extra query.

C-MOVE is the one real outlier. The objects come back to a local store SCP
rather than through the retrieve association, so the whole batch shares one
listener; that forces C-MOVE onto `DownloadManager.UNIQUE_EXECUTOR` and requires
a port the archive can reach. Every other transport runs on the concurrent
executor.

## Entry points

The transports are close to interchangeable. The entry points are not, and this
rather than any protocol difference is what decides whether a feature can fetch
priors.

| Behaviour | Query/Retrieve dialog | `dicom:rs` | `dicom:get` |
| --- | --- | --- | --- |
| Transports | C-GET, C-MOVE, WADO-URI, WADO-RS | WADO-RS | WADO-URI and WADO-RS, via a manifest |
| Callable from code | **no**, needs a live `DicomQrView` | yes | yes |
| Query filters | full search form, saved templates | patient, accession, study, series, date range, modality, most-recent-N | none, the caller supplies the answer |
| Series selection | checkbox tree, `RetrieveSelection` | by series UID in the query | whatever the manifest lists |
| Query vocabulary | `DicomParam[]` and a period | a string query map | — |

On a DICOMweb site a prior fetch is already expressible as `dicom:rs`. On a
C-FIND site there is no way in at all: `RetrieveTask` reads six things off a
Swing component — destination node, calling node, retrieve type, auth method,
query model and the DICOM listener.

## What has to become common for priors

Pulling priors means, from an open study: find the other studies of this
patient, pick the ones a protocol wants, and fetch a few of their series quietly
in the background. Most of that already works.

### Blocking

**DIMSE cannot be driven from code.** The retrieve parameters have to leave the
widget and become a value object, so that something other than the dialog can
construct a retrieve.

**Two vocabularies for the same query.** The dialog speaks `DicomParam[]` plus a
period; the DICOMweb command speaks a string query map. "Same patient, last
three studies, CT or MR" has to be written twice and stays untestable as a unit.
One query descriptor, executed by either a C-FIND or a QIDO backend, is the
smallest thing that unblocks a protocol rule.

**Nothing turns a patient into a prior search.** The matching key already
exists: `PatientComparator` builds the pseudo UID from PatientID, issuer, name,
birth date and sex, which is exactly how a prior is recognised as the same
person. No code asks an archive what else it holds for that key.

### Partial

**The selection type is bundle-local.** `RetrieveSelection` — studies, each
narrowed to chosen series — is the right abstraction and is already honoured by
all four transports, but it lives in `weasis-dicom-qr`, so nothing outside that
bundle can express "these two series of that prior".

**No background priority band.** Priority is a single counter that decrements as
series are promoted. Priors must never outrank the study on screen, and there is
no band below the foreground to put them in, so a rule as simple as "priors
start after the current study is complete" cannot be expressed.

**Useful knobs are DICOMweb-only.** `most-recent-N` and `show-whole-study` exist
for QIDO and have no C-FIND counterpart, so the same protocol rule would behave
differently depending on how the archive is configured.

## A shape that fits what exists

The transports do not need to change. What is missing is a seam above them, so
the dialog becomes one caller among several rather than the only way in.

```java
// weasis-dicom-explorer — one seam, two implementations
public interface RetrieveSource {
  List<Attributes> queryStudies(StudyQuery query);
  List<Attributes> querySeries(String studyUid);
  List<LoadSeries> retrieve(RetrieveSelection selection, DownloadPriority.Band band);
}

// DimseSource    — called node, calling node, C-GET / C-MOVE / WADO-URI
// DicomWebSource — base URL, auth method, headers

// StudyQuery: patient key, date range, modalities, limit, most-recent-N
// executed as C-FIND keys or as QIDO parameters, never written twice
```

- **Nothing downstream moves.** `DownloadManager`, `LoadSeries`, stop, resume,
  thumbnails and priority keep working exactly as they do now.
- **The dialog gets smaller.** `RetrieveTask` stops reaching into a Swing
  component and takes a `RetrieveSource`, which the view builds from its combo
  boxes.
- **Priors become a rule, not a protocol.** A hanging protocol says "the last
  two CT studies of this patient, localizer plus the matching plane, background
  band" and never learns whether the archive speaks DIMSE or HTTP.
- **It is testable.** A query descriptor and a selection are values; today the
  equivalent logic can only be exercised through a live archive and a visible
  dialog.

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

Behaviour above was verified by reading the 4.7.3-SNAPSHOT working tree, not
against a live archive.