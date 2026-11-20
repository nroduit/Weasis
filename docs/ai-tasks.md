# AI tasks (UPS-RS)

The module `weasis-dicom/weasis-dicom-aiw` makes Weasis an IHE AIW-I **Task Requestor** and **Watcher**: from the DICOM explorer the user asks a server to run an analysis on a study or on some of its series, sees the task waiting, running and done on the series, and opens the results.
A task is a UPS-RS workitem (PS3.18 §11); Weasis creates it, asks for its cancellation and reads it, and never assigns a performer nor steps its state.

## In the explorer

A study loaded from a DICOMweb server that takes tasks shows a group **AI tasks** in three places: the context menu of a series, the header of the study in thumbnail mode, and the study content panel.
Each place offers the tasks of the server under two lines, *Selected series (n of m)* and *Whole study*, so what is sent is what is selected.

| Entry | When | What it does |
| --- | --- | --- |
| *Run ‹task›* | no task of that code is waiting or running on that target, and the server allows writing | creates the workitem with the instances of the target as inputs |
| *Cancel ‹task› (queued)* | the task is scheduled and the server allows writing | requests its cancellation |
| *Running: ‹task›* | the task is in progress | nothing, it states the fact |
| *Open results of ‹task›* | the task is completed and names results | loads the result series through the DICOMweb service of the study |
| *Refresh the task states* | always | reads the tasks of the study again |

A series with a task waiting or running carries a mark (the robot with a clock), and one whose results are ready to open the robot with a check, until the results are loaded.
A series whose images are not downloaded yet cannot be an input: a workitem names its inputs instance by instance.

## Which servers take tasks

Nothing is shown until a server is enabled on the page *DICOM › AI Tasks* of the preferences, which writes the document `upsNodes.json` of the user (next to the other documents of the profile).
The page lists the servers, with *Enabled* and *Create tasks* per server, and for the selected one the requester AE title, the reading interval and the tasks offered.
A server is named by the id of its DICOMweb node of the preferences, or by its URL for a study loaded without a node (a `$dicom:rs` launch).

```json
{"nodes": [{
  "id": "pacs",
  "url": "https://pacs.example.org/dicomweb",
  "enabled": true,
  "writeAllowed": true,
  "requesterAet": "WEASIS",
  "requesterInPath": false,
  "pollSeconds": 30,
  "catalog": [
    {"value": "CT-ORGAN-SEG", "scheme": "99SITE", "meaning": "Organ segmentation"}
  ]
}]}
```

| Key | Meaning | Default |
| --- | --- | --- |
| `id` | the id of the DICOMweb node, or any name when `url` is given | required |
| `url` | the root URL of the server; a study whose origin starts with it is served | none |
| `enabled` | the entry is used | `false` |
| `writeAllowed` | tasks may be created and canceled; without it the group only shows states and results | `false` |
| `requesterAet` | the AE title of this user agent on the server | `WEASIS` |
| `requesterInPath` | the AE title goes into the path of the subscriptions (dcm4chee-arc) | `false` |
| `pollSeconds` | how often the tasks are read again while one is waiting or running; never under 10, `0` for never | `30` |
| `catalog` | the tasks offered, in the order of the menu: code value, coding scheme designator, meaning | empty |

The catalog is per server and set by the site (RAD-80 §4.80.4.1.2.1): there is no built-in list of tasks.

## Commands

Every entry of the menu is also a verb of the `ups:` namespace, and the transactions are reachable one by one (`weasis-cli help ups`).
On a loaded study `--study` is enough: the server is the origin of the study and its entry of the document.
With `--url` (and `--auth`, the uid of an authentication method of the user) a verb talks to any server, the inputs coming from the options.

```bash
weasis-cli ups:nodes
weasis-cli ups:search --study 1.2.3
weasis-cli ups:run --study 1.2.3 --code CT-ORGAN-SEG --scheme 99SITE --series 1.2.3.4
weasis-cli ups:watch --study 1.2.3 --timeout 120
weasis-cli ups:open --study 1.2.3 --workitem 1.2.3.99
```

The verbs that read the server need the `network.read` capability and `run` and `cancel` need `network.write`: a remote caller is refused until the policy of the instance allows them (see the *Commands* page of the preferences).

## For plugin authors

The group is an `ExplorerActionProvider` (see [Writing a plugin](plugins.md)): another service that takes studies or series plugs into the same three places of the explorer by implementing that extension point, without touching this module.
