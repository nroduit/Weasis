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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.weasis.core.api.command.CommandAudit;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.CommandOrigin;
import org.weasis.core.api.command.CommandPolicy;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.command.CommandVocabulary;
import org.weasis.core.api.command.ViewId;

class EventCommandsTest {

  /** A namespace without a command model that declares events. */
  static final class PanelExtension implements CommandExtension {
    @Override
    public String getNamespace() {
      return "panel";
    }

    @Override
    public void execute(String commandName, String[] args) {}

    @Override
    public String getHelp() {
      return "panel commands:\n  panel:show";
    }

    @Override
    public List<EventType> getEventTypes() {
      return List.of(
          EventType.of("panel:shown", "5.0", "A panel was shown.")
              .field("view", ViewId.class, "the view that shows it")
              .build(),
          EventType.of("panel:moving", "5.0", "A panel is being dragged.")
              .coalesced()
              .field("x", int.class, "position")
              .build());
    }
  }

  private final EventBus bus = new EventBus(50, 1 << 16);

  @AfterEach
  void close() {
    bus.close();
  }

  private CommandManager manager(CommandPolicy policy) {
    PluginManager plugins = mock(PluginManager.class);
    when(plugins.getExtensions(CommandExtension.class)).thenReturn(List.of(new PanelExtension()));
    return new CommandManager(plugins, CommandAudit.disabled(), policy, bus);
  }

  @Test
  void theCatalogHoldsWhatTheNamespacesAndTheBusDeclare() {
    CommandManager manager = manager(CommandPolicy.defaults());
    CommandResult list =
        manager.run("events:list --namespace panel --json", CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertTrue(list.isOk(), list.error()),
        () -> assertTrue(list.body().contains("\"name\": \"panel:shown\""), list.body()),
        () -> assertTrue(list.body().contains("\"type\": \"view-id\""), list.body()),
        () -> assertTrue(list.body().contains("\"coalesced\": true"), list.body()),
        () -> assertTrue(bus.catalog().find(WeasisEvents.READY).isPresent()),
        () -> assertEquals(List.of("command", "panel", "weasis"), bus.catalog().namespaces()));
    CommandResult markdown =
        manager.run("events:list --namespace panel --markdown", CommandOrigin.LAUNCH_ARGS);
    assertTrue(
        markdown.body().contains("| `panel:moving` (coalesced) | 5.0 | x: integer |"),
        markdown.body());
  }

  @Test
  void theHelpOfANamespaceListsItsEventsNextToItsVerbs() {
    CommandManager manager = manager(CommandPolicy.defaults());
    String text = manager.run("help panel", CommandOrigin.LAUNCH_ARGS).body();
    String json = manager.run("help panel --json", CommandOrigin.LAUNCH_ARGS).body();
    String all = manager.getJsonHelp();
    String reference = manager.getMarkdownHelp();
    assertAll(
        () -> assertTrue(text.contains("panel:show"), text),
        () -> assertTrue(text.contains("Events ('weasis-cli events --filter panel:*'"), text),
        () -> assertTrue(text.contains("panel:shown  {view: view-id}"), text),
        () -> assertTrue(json.contains("\"events\": ["), json),
        () -> assertTrue(all.contains("\"events\": {"), all),
        () -> assertTrue(all.contains("\"weasis:ready\""), all),
        () -> assertTrue(reference.contains("## Events"), reference),
        () -> assertTrue(reference.contains("### panel:"), reference));
  }

  @Test
  void recentIsAPollOverTheRingBufferMaskedForARemoteCaller() throws Exception {
    CommandManager manager = manager(CommandPolicy.defaults());
    bus.publish(
        Event.of("panel:shown")
            .put("view", "v1")
            .identity("patientName", "DOE^JOHN", Event.PATIENT_NAME));
    bus.publish(Event.of("panel:shown").put("view", "v2"));
    EventBusTest.waitUntil(() -> bus.lastSequence() >= 2);
    CommandResult local = manager.run("events:recent --json", CommandOrigin.LAUNCH_ARGS);
    CommandResult remote = manager.run(List.of("events:recent"), CommandOrigin.REMOTE);
    CommandResult since =
        manager.run("events:recent --since 1 --where view=v2 --json", CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertTrue(local.body().contains("DOE^JOHN"), local.body()),
        () -> assertFalse(remote.body().contains("DOE^JOHN"), remote.body()),
        () -> assertTrue(remote.body().contains("\"lastSeq\": 2"), remote.body()),
        () -> assertTrue(since.body().contains("\"seq\": 2"), since.body()),
        () -> assertFalse(since.body().contains("\"seq\": 1"), since.body()));
  }

  @Test
  void aClosedPolicyRefusesTheStreamAndThePollWithTheSingleRefusalShape() {
    CommandPolicy closed =
        CommandPolicy.defaults().withEvents(new CommandPolicy.EventRules(false, Set.of(), 4));
    CommandManager manager = manager(closed);
    CommandResult poll = manager.run(List.of("events:recent"), CommandOrigin.REMOTE);
    CommandFailure refused =
        assertThrows(
            CommandFailure.class,
            () -> manager.openEventStream(EventFilter.ALL, CommandOrigin.REMOTE));
    assertAll(
        () -> assertEquals(CommandResult.FORBIDDEN, poll.status()),
        () -> assertTrue(poll.error().contains("\"policy\": \"events\""), poll.error()),
        () -> assertEquals("events", refused.error().policy()),
        () -> assertTrue(manager.run("events:recent", CommandOrigin.LAUNCH_ARGS).isOk()));
  }

  @Test
  void theSubscriberCapAndTheNamespaceLimitComeFromThePolicy() throws Exception {
    CommandPolicy policy =
        CommandPolicy.defaults().withEvents(new CommandPolicy.EventRules(true, Set.of("panel"), 1));
    CommandManager manager = manager(policy);
    try (CommandManager.EventStreamGrant first =
        manager.openEventStream(EventFilter.ALL, CommandOrigin.REMOTE)) {
      CommandFailure second =
          assertThrows(
              CommandFailure.class,
              () -> manager.openEventStream(EventFilter.ALL, CommandOrigin.REMOTE));
      assertEquals("events.maxSubscribers", second.error().policy());
      assertTrue(first.allows(Event.of("panel:shown").build()));
      assertFalse(first.allows(Event.of("dicom:study.added").build()));
    }
    try (CommandManager.EventStreamGrant again =
        manager.openEventStream(EventFilter.ALL, CommandOrigin.REMOTE)) {
      assertTrue(again.allows(Event.of("panel:shown").build()), "the closed grant freed its slot");
    }
  }

  @Test
  void theEventVerbsFollowTheVocabulary() {
    assertEquals(List.of(), CommandVocabulary.violations("events", new EventCommands(bus)));
  }

  @Test
  void aRemoteCommandPublishesItsEnd() throws Exception {
    CommandManager manager = manager(CommandPolicy.defaults());
    manager.run(List.of("events:subscribers"), CommandOrigin.REMOTE);
    EventBusTest.waitUntil(() -> bus.lastSequence() >= 1);
    Event finished = bus.recent(0, 1, EventFilter.parse(WeasisEvents.COMMAND_FINISHED)).getFirst();
    assertAll(
        () -> assertEquals("events:subscribers", finished.get("command")),
        () -> assertEquals(0, finished.get("status")),
        () -> assertEquals("remote", finished.origin()));
  }
}
