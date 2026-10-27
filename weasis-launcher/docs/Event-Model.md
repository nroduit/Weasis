# Event Model

## Purpose

Commands are how a caller acts on Weasis; events are how a caller observes it without polling.
Every change of state a feature makes is published as a typed, named event on one in-process bus,
and the same events reach the UI, the plugins, the scripts and the local processes connected to
the singleton socket. The model lives in `org.weasis.core.api.event` of the launcher, next to the
command model, because it is the API every plugin sees and the stream server needs it from
`Singleton`.

## An event

An `Event` is a record: a name in the command vocabulary (`namespace:subject.change`, e.g.
`dicom:study.opened`, `workspace:view.selected`), the global sequence number, the publication
time, the origin (`app`, or the lower-case command origin of the call that caused the change) and a
payload of handles and small values. The payload never holds a model object or a Swing type: what
crosses the socket is what the bus carries, so a UI part and a remote caller read the same thing.
Payload values are strings, numbers, booleans, dates, lists and maps; the keys `name`, `seq`,
`time` and `origin` belong to the envelope. A payload field that carries identity is declared with
its kind (`Event.PATIENT_NAME`, `PATIENT_ID`, `BIRTH_DATE`, `ACCESSION_NUMBER`, `DESCRIPTION`,
`DATE`, `FREE_TEXT`), so a remote subscriber sees it through the masking profile of the instance.
An event with a coalescing key says that, for a subscriber that fell behind, only the last pending
event with that key matters: a window/level drag, a slice scroll or the progress of a series emits
`.changing` or `.progress` events keyed by view or by series, and the `.changed` form on release.

An `EventType` describes an event for the catalog: name, the version that introduced it, what it
tells, whether it is coalesced, and its fields with their vocabulary types (`view-id`,
`series-uid`, `integer`, …). An event, once published in a release, keeps its name and its fields
within a major; fields are only added.

## The bus

`EventBus.global()` is the bus of the application. `publish` enqueues and returns from any thread;
a dispatcher thread numbers the events in one global sequence, keeps the last ones in a ring buffer
bounded by count and by bytes (a coalesced event keeps only its last occurrence there), and fans them out to the bounded queue of each subscriber. A
subscriber receives its events in order, one delivery at a time, either on a thread of the bus
(`Delivery.ASYNC`) or on the event dispatch thread (`Delivery.EDT`), where the pending events of a
tick are drained in one call and a handler longer than 50 ms is logged. The bus never calls
`invokeAndWait`, so an EDT that waits for the bus cannot deadlock it. When a subscriber's queue is
full, the coalesced events are collapsed first, then the oldest pending event is dropped and the
subscriber receives `weasis:subscriber.dropped`; after a few overflows it is detached. The ring
buffer and the other subscribers are never affected by a slow one.

`subscribe` takes an `EventFilter` (globs on the name such as `dicom:*`, plus optional
`key=value` conditions on the payload) and returns a `Subscription`, which is the only way to stop
the deliveries. A UI subscriber binds it to a lifetime it does not control: `until(component)`
closes it when the component is no longer displayable, `untilViewClosed(viewId)` when the
workspace view closes; `events:subscribers` reports the subscriptions whose owner component is
gone. `await(filter, since, timeout)` blocks for the first matching event, looking in the ring
buffer first so an event that happened just before is not missed. `recent(since, limit, filter)`
reads the ring buffer.

## The catalog

The `EventCatalog` of the bus lists every event the instance may publish. The bus declares the
`weasis:` and `command:` events (`WeasisEvents`); the command manager adds what each loaded
namespace declares in `CommandExtension.getEventTypes()`; core services that bridge a listener
family declare the events of that family. `events:list` prints the catalog, `help <namespace>`
lists the events of a namespace under its verbs, and `help --json` and `help --markdown` carry
them, so a connector can type its tools.

## Bridges and publishers

The existing listener families stay as they are and keep driving the application: opening a viewer
on `REGISTER`, refreshing the explorer on `ADD`. Bridges observe them and translate them into bus
events, so the whole application is observable before any publisher is rewritten:

| Family | Bridge | Events |
|---|---|---|
| `ObservableEvent` on `DicomModel` | `DicomEventBridge` (DICOM explorer) | `dicom:patient.*`, `study.added` / `removed`, `series.*`, `object.added`, `loading.*`, `workspace:panel.changed`; the model fires `ADD` for series only, so a study and a patient are announced with their first series |
| the panes (`DefaultView2d.setSeries`) | `ViewerEventBridge`, then `DicomEventBridge` subscribing to the bus | `view2d:series.changed` from the pane; `dicom:study.opened` / `study.closed` derived from the studies the panes show, whatever opened them (hanging protocol, drop, explorer, verb) |
| `WorkspaceListener` and the engine | `WorkspaceManager` itself | `workspace:view.opened`, `view.closed`, `view.selected`, `window.opened`, `window.closed`, `window.activated` |
| `SeriesViewerEvent` | `ViewerEventBridge` from `ImageViewerEventManager` | `view2d:image.changed`, `windowLevel.changing` / `changed`, `workspace:layout.changed` |
| Graphic model | `GraphicEventBridge` from `AbstractGraphicModel` and `GraphicsPane` | `graphic:added`, `removed`, `changed`, `selection.changed` |
| AWT | `UiActivityBridge` (UI core) | `weasis:ui.modal`, `weasis:user.active` |

A bridge converts the model object to handles on the publishing thread, before any hop, so a
subscriber sees what was true when the event happened; a consumer that needs the current state
re-reads the model and uses the event as a trigger. New features publish directly from the service
the verbs call, and declare their events with their verbs. Control flow goes through services and
verbs; notification goes through the bus; never the reverse.

## The stream and the policy

The singleton socket carries a second request, `si.sub` (protocol version 2): the client sends its
filter and the sequence number it last saw, the server replays the ring buffer from there, then
writes one JSON object per line until the client closes, with a `weasis:heartbeat` line on an idle
stream. `weasis-cli events [--filter <glob>] [--since <seq>] [--count <n>] [--timeout <s>]` is the
client; `events:recent` is the poll for a host that cannot hold a connection. A version 1 client is
still served for `si.run` and answered in its version.

What a remote subscriber sees is decided by the instance (`CommandPolicy.EventRules`,
`weasis.commands.policy.events`, `events.namespaces`, `events.maxSubscribers`): the events may be
closed, limited to namespaces, and capped in concurrent subscribers; a refusal has the same shape
as a refused command. Identity fields pass the masking profile of a remote caller through the
masker the DICOM explorer installs (`EventBus.setRemoteMasker`), the same profile the command
results use. Every subscription and its end is one `CommandAudit` line. Nothing in the stream
touches Swing, so it works without a display.

## In scripts

`@wait <event-glob> [--timeout s] [--where key=value]` is a step of the `script:` format: the
runner waits for the first matching event published after the previous command started, binds it
to `${event…}` and to the step's variable, and fails the step on timeout. The wait levels of the
loading verbs are the shorthand for waiting on `dicom:loading.stopped`.
