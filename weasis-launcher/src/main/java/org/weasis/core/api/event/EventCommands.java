/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.event;

import java.util.ArrayList;
import java.util.List;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandPolicy;
import org.weasis.core.api.command.JsonWriter;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.Since;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Command namespace {@code events}: what this instance publishes, what it published lately, and who
 * listens. The stream itself is not a verb: {@code weasis-cli events} holds a connection open on
 * the singleton socket. Registered by the command manager, since the bus is the manager's.
 */
@Command(
    name = "events",
    description =
        "The events this instance publishes: the catalog, the recent ones, and the subscribers. "
            + "'weasis-cli events' streams them live.",
    subcommands = {
      EventCommands.ListEvents.class,
      EventCommands.Recent.class,
      EventCommands.Subscribers.class
    })
public class EventCommands implements CommandExtension {

  private final EventBus bus;

  public EventCommands(EventBus bus) {
    this.bus = bus;
  }

  @Override
  public String getNamespace() {
    return "events"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  abstract static class Verb extends AbstractVerb {
    @ParentCommand EventCommands parent;

    EventBus bus() {
      return parent.bus;
    }
  }

  @Command(
      name = "list",
      description =
          "The events this instance may publish, by namespace, with the fields of their payload.")
  @Since("5.0")
  static class ListEvents extends Verb {

    @Option(
        names = "--namespace",
        paramLabel = "<namespace>",
        description = "Only the events of this namespace.")
    @Since("5.0")
    String namespace;

    @Option(names = "--markdown", description = "A Markdown table, for a reference page.")
    @Since("5.0")
    boolean markdown;

    @Override
    protected void run() {
      EventCatalog catalog = bus().catalog();
      List<EventType> types = namespace == null ? catalog.types() : catalog.types(namespace);
      if (markdown) {
        print(EventCatalog.markdownTable(types));
      } else if (isJson()) {
        JsonWriter out = new JsonWriter();
        out.beginObject().name("events"); // NON-NLS
        catalog.write(out, types);
        print(out.endObject().toString());
      } else {
        print(types.isEmpty() ? "No event declared." : EventCatalog.text(types));
      }
    }
  }

  @Command(
      name = "recent",
      description =
          "The last events the bus kept, oldest first: a poll for a caller that cannot hold a "
              + "stream. Pass the 'lastSeq' of the previous answer as --since to read only what is new.")
  @Since("5.0")
  @Needs(Capability.EVENTS)
  static class Recent extends Verb {

    @Option(
        names = "--since",
        paramLabel = "<seq>",
        defaultValue = "0",
        description = "Only the events after this sequence number.")
    @Since("5.0")
    long since;

    @Option(
        names = "--last",
        paramLabel = "<n>",
        defaultValue = "50",
        description = "At most this many events, the newest ones (default: ${DEFAULT-VALUE}).")
    @Since("5.0")
    int last;

    @Option(
        names = "--filter",
        paramLabel = "<event-glob>",
        description = "Event names to keep, with '*' as a wildcard: 'dicom:*,workspace:view.*'.")
    @Since("5.0")
    String filter;

    @Option(
        names = "--where",
        paramLabel = "<key=value>",
        description = "A payload value an event must carry; repeatable.")
    @Since("5.0")
    List<String> where = new ArrayList<>();

    @Override
    protected void run() {
      if (last < 1 || last > 1000) {
        throw invalid("--last is between 1 and 1000");
      }
      EventFilter eventFilter = EventFilter.parse(filter, where);
      CommandContext context = CommandContext.current();
      List<Event> events = new ArrayList<>(bus().recent(since, Integer.MAX_VALUE, eventFilter));
      if (context.isRemote()) {
        CommandPolicy.EventRules rules = context.policy().events();
        events.removeIf(event -> !rules.allows(event.namespace()));
      }
      if (events.size() > last) {
        events = events.subList(events.size() - last, events.size());
      }
      JsonWriter out = new JsonWriter();
      out.beginObject().name("lastSeq").value(bus().lastSequence()); // NON-NLS
      out.name("events").beginArray(); // NON-NLS
      for (Event event : events) {
        (context.isRemote() ? bus().maskForRemote(event) : event).write(out);
      }
      print(out.endArray().endObject().toString());
    }
  }

  @Command(
      name = "subscribers",
      description =
          "The active subscriptions: id, owner, filter, delivery, dropped events, and whether the "
              + "owner component is gone (dangling).")
  @Since("5.0")
  static class Subscribers extends Verb {

    @Override
    protected void run() {
      List<Subscription> subscriptions = bus().subscriptions();
      if (isJson()) {
        JsonWriter out = new JsonWriter();
        out.beginObject().name("subscribers").beginArray(); // NON-NLS
        for (Subscription s : subscriptions) {
          out.beginObject()
              .name("id") // NON-NLS
              .value(s.id())
              .name("owner") // NON-NLS
              .value(s.owner())
              .name("filter") // NON-NLS
              .value(s.filter().describe())
              .name("delivery") // NON-NLS
              .value(s.delivery().name().toLowerCase(java.util.Locale.ROOT))
              .name("dropped") // NON-NLS
              .value(s.dropped())
              .name("dangling") // NON-NLS
              .value(s.isDangling())
              .endObject();
        }
        print(out.endArray().endObject().toString());
        return;
      }
      if (subscriptions.isEmpty()) {
        print("No subscriber.");
        return;
      }
      for (Subscription s : subscriptions) {
        print(
            "  %-5s %-28s %-6s dropped %-4d %s%s"
                .formatted(
                    s.id(),
                    s.owner(),
                    s.delivery().name().toLowerCase(java.util.Locale.ROOT),
                    s.dropped(),
                    s.filter().describe(),
                    s.isDangling() ? "  (dangling)" : ""));
      }
    }
  }

  /** The verbs of this namespace, for the listing of a manager that registers it itself. */
  public static List<String> verbs() {
    return List.of("list", "recent", "subscribers"); // NON-NLS
  }
}
