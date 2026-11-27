# Scenario scripts of the 5.0 preview

One script per feature of the *What to test* table of the pre-release notes: the steps of the
scenario sheet that a command can drive, with `@expect` lines turning the answers into checks.
A tester replays the script of a feature against a running preview and attaches its JUnit report
to a finding; an observer runs them all on a candidate before a session.

| Script | Feature | Data |
|---|---|---|
| `hanging-protocols.weasis` | hanging protocols, the why panel, corrections, steps, sessions, save as protocol | one CT study with several reconstructions (or a mammography with its prior); start with an empty explorer |
| `windows-screens.weasis` | screens by role, a viewer window, fullscreen, a tool as a tab, a layout kept and restored | any study |
| `explorer.weasis` | modes, facets, filters, reveal, objects and images of interest | a folder of studies |
| `measurements.weasis` | tools, a lesion measured, statistics, undo and redo, the CSV, SR and XML exports | one CT study |
| `masking.weasis` | masked attributes, a region hidden before a capture, the device library | one ultrasound study |
| `printing.weasis` | destinations, a layout page and its preview, a series on pages, a PDF | any study |
| `subtraction.weasis` | phases, the live preview, the derived series and its sources | a loaded study: `--set series=<post-contrast uid> --set mask=<pre-contrast uid>` |
| `dsa.weasis` | digital subtraction angiography: a mask from the frames of the run, averaged, shifted, time-interval differencing, the subtracted series of each plane | one X-ray angiography run, biplane, stored without subtraction |
| `3d-zoom.weasis` | zoom, a reformat, the MPR and the 3D view on one volume, color management | one CT study, a graphics adapter the renderer accepts |
| `ecg.weasis` | an ECG opens in the waveform viewer, the identity of the file | a folder of ECG files |
| `whole-slide.weasis` | a slide from files, the pyramid, a magnification, a measurement, the SR export | one DICOM slide |
| `settings-sites.weasis` | where the settings live, a package exported and read back, the layers | none |
| `commands.weasis` | the instance, a transaction and its rollback, the events, the feedback link | any study |

## Run one

```bash
weasis-cli script:run --file hanging-protocols.weasis \
    --set data=/path/to/the/study --set out=/path/to/a/folder \
    --report ~/weasis-preview/hanging-protocols.xml
```

Every script takes `out`, a folder the instance may write to (your home folder is allowed by
default); most take `data`, a folder of DICOM files loaded by the script. The answer is one JSON
object per step, then a summary with the number of failed steps; add `--keep-going` to run on after
a failure and see every one. The files the script writes carry the sequence number of its
checkpoint in their names, so a second run never collides with the first.

A script loads what it needs, takes a checkpoint first and ends with a rollback to it: the display
and the explorer are as they were before it ran. The expectations are structural (a unit, a count, a
handle that comes back where it should), never your clinical values: the scenario sheet of the
feature is where you judge those.

`weasis-cli script:check --file <script>` parses a script against your instance without running it,
which tells you at once whether a verb or an option it uses is missing there, and lists the
parameters it expects from `--set`.
