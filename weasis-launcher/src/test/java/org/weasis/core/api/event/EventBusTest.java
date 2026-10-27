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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EventBusTest {

  private final EventBus bus = new EventBus(100, 1 << 16);

  @AfterEach
  void close() {
    bus.close();
  }

  private static Event view(String name, String view) {
    return Event.of(name).put("view", view).build();
  }

  @Test
  void eventsReachASubscriberInPublicationOrderWithGrowingSequenceNumbers() throws Exception {
    int count = 500;
    CountDownLatch all = new CountDownLatch(count);
    List<Event> received = new CopyOnWriteArrayList<>();
    try (Subscription ignored =
        bus.subscribe(
            EventFilter.parse("test:*"),
            Subscription.Delivery.ASYNC,
            e -> {
              received.add(e);
              all.countDown();
            })) {
      for (int i = 0; i < count; i++) {
        bus.publish(Event.of("test:tick").put("i", i));
      }
      assertTrue(all.await(5, TimeUnit.SECONDS));
    }
    for (int i = 0; i < count; i++) {
      assertEquals(i, received.get(i).get("i"));
      if (i > 0) {
        assertTrue(received.get(i).seq() > received.get(i - 1).seq());
      }
    }
  }

  @Test
  void aFilterKeepsOnlyTheNamesAndPayloadValuesAsked() throws Exception {
    CountDownLatch two = new CountDownLatch(2);
    List<String> names = new CopyOnWriteArrayList<>();
    try (Subscription ignored =
        bus.subscribe(
            EventFilter.parse("view2d:*", List.of("view=v2")),
            Subscription.Delivery.ASYNC,
            e -> {
              names.add(e.name());
              two.countDown();
            })) {
      bus.publish(view("view2d:image.changed", "v1"));
      bus.publish(view("view2d:image.changed", "v2"));
      bus.publish(view("dicom:study.added", "v2"));
      bus.publish(view("view2d:zoom.changed", "v2"));
      assertTrue(two.await(5, TimeUnit.SECONDS));
    }
    assertEquals(List.of("view2d:image.changed", "view2d:zoom.changed"), names);
  }

  @Test
  void aSlowSubscriberSeesOnlyTheLastOfACoalescedRun() throws Exception {
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch released = new CountDownLatch(1);
    List<Event> received = new CopyOnWriteArrayList<>();
    try (Subscription ignored =
        bus.subscribe(
            EventFilter.ALL,
            Subscription.Delivery.ASYNC,
            e -> {
              received.add(e);
              if (received.size() == 1) {
                released.countDown();
                await(gate);
              }
            })) {
      bus.publish(Event.of("test:first"));
      assertTrue(released.await(5, TimeUnit.SECONDS)); // the handler now blocks on the first event
      for (int i = 1; i <= 50; i++) {
        bus.publish(Event.of("view2d:windowLevel.changing").put("value", i).coalesce("wl:v1"));
      }
      bus.publish(Event.of("test:last"));
      waitUntil(() -> bus.lastSequence() >= 52);
      gate.countDown();
      waitUntil(() -> received.size() >= 3);
    }
    List<Event> replay = bus.recent(0, 100, EventFilter.parse("view2d:*"));
    assertAll(
        () -> assertEquals(3, received.size()),
        () -> assertEquals("view2d:windowLevel.changing", received.get(1).name()),
        () -> assertEquals(50, received.get(1).get("value")),
        () -> assertEquals("test:last", received.get(2).name()),
        () -> assertEquals(1, replay.size(), "the ring keeps the last of a coalesced run"),
        () -> assertEquals(50, replay.getFirst().get("value")));
  }

  @Test
  void anOverflowingSubscriberIsToldAndThenDetached() throws Exception {
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch released = new CountDownLatch(1);
    List<Event> received = new CopyOnWriteArrayList<>();
    Subscription subscription =
        bus.subscribe(
            EventFilter.parse("test:*,weasis:*"),
            Subscription.Delivery.ASYNC,
            e -> {
              received.add(e);
              if (received.size() == 1) {
                released.countDown();
                await(gate);
              }
            });
    bus.publish(Event.of("test:first"));
    assertTrue(released.await(5, TimeUnit.SECONDS));
    int flood = EventBus.QUEUE_CAPACITY + EventBus.DETACH_AFTER_DROPS + 5;
    for (int i = 0; i < flood; i++) {
      bus.publish(Event.of("test:flood").put("i", i));
    }
    waitUntil(() -> bus.lastSequence() >= flood + 1);
    gate.countDown();
    waitUntil(() -> !subscription.isActive());
    Event last = received.getLast();
    assertAll(
        () -> assertEquals(WeasisEvents.SUBSCRIBER_DROPPED, last.name()),
        () -> assertEquals(true, last.get("detached")),
        () -> assertTrue(subscription.dropped() >= EventBus.DETACH_AFTER_DROPS),
        () -> assertFalse(bus.subscriptions().contains(subscription)));
  }

  @Test
  void anEdtSubscriberIsDrainedOnTheExecutorOnePassPerTick() throws Exception {
    List<Runnable> ticks = new CopyOnWriteArrayList<>();
    bus.setEdtExecutor(ticks::add);
    List<Event> received = new ArrayList<>();
    try (Subscription ignored =
        bus.subscribe(EventFilter.ALL, Subscription.Delivery.EDT, received::add)) {
      for (int i = 0; i < 10; i++) {
        bus.publish(Event.of("test:tick").put("i", i));
      }
      waitUntil(() -> bus.lastSequence() >= 10);
      waitUntil(() -> !ticks.isEmpty());
      assertTrue(received.isEmpty(), "nothing is delivered before the executor runs");
      ticks.forEach(Runnable::run);
      assertAll(
          () -> assertEquals(1, ticks.size(), "one drain for the whole burst"),
          () -> assertEquals(10, received.size()));
    }
  }

  @Test
  void theRingBufferAnswersRecentAndAwaitFindsWhatHappenedJustBefore() throws Exception {
    for (int i = 0; i < 150; i++) {
      bus.publish(Event.of("test:tick").put("i", i));
    }
    waitUntil(() -> bus.lastSequence() >= 150);
    List<Event> recent = bus.recent(0, Integer.MAX_VALUE, EventFilter.ALL);
    assertAll(
        () -> assertEquals(100, recent.size(), "bounded by count"),
        () -> assertEquals(51, recent.getFirst().seq()),
        () -> assertEquals(2, bus.recent(148, 10, EventFilter.ALL).size()));
    Optional<Event> found = bus.await(EventFilter.parse("test:tick"), 140, Duration.ofSeconds(1));
    assertTrue(found.isPresent());
    assertEquals(141, found.get().seq());
    assertTrue(bus.await(EventFilter.parse("test:never"), 0, Duration.ofMillis(100)).isEmpty());
  }

  @Test
  void aViewBoundSubscriptionClosesWhenTheViewCloses() throws Exception {
    AtomicInteger count = new AtomicInteger();
    Subscription subscription =
        bus.subscribe(EventFilter.ALL, Subscription.Delivery.ASYNC, e -> count.incrementAndGet())
            .untilViewClosed("v7");
    bus.publish(view(EventBus.VIEW_CLOSED, "v1"));
    waitUntil(() -> bus.lastSequence() >= 1);
    assertTrue(subscription.isActive());
    bus.publish(view(EventBus.VIEW_CLOSED, "v7"));
    waitUntil(() -> !subscription.isActive());
    waitUntil(() -> bus.subscriptions().isEmpty()); // the watch closes with it
  }

  @Test
  void publishingNeverWaitsForASubscriber() throws Exception {
    CountDownLatch gate = new CountDownLatch(1);
    try (Subscription ignored =
        bus.subscribe(EventFilter.ALL, Subscription.Delivery.ASYNC, e -> await(gate))) {
      long start = System.nanoTime();
      for (int i = 0; i < 10_000; i++) {
        bus.publish(Event.of("test:fast").put("i", i));
      }
      long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(millis < 2000, "10 000 events published in " + millis + " ms");
      gate.countDown();
    }
  }

  @Test
  void identityIsRemovedForARemoteReaderUnlessAMaskerSaysOtherwise() {
    Event event =
        Event.of("dicom:patient.added")
            .identity("patientName", "DOE^JOHN", Event.PATIENT_NAME)
            .put("studies", 2)
            .build();
    Event masked = bus.maskForRemote(event);
    assertAll(
        () -> assertFalse(masked.payload().containsKey("patientName")),
        () -> assertEquals(2, masked.get("studies")),
        () -> assertTrue(masked.identity().isEmpty()));
    bus.setRemoteMasker(e -> e.withPayload(Map.of("patientName", "P-1"), e.identity()));
    assertEquals("P-1", bus.maskForRemote(event).get("patientName"));
  }

  @Test
  void anEventNeedsANamespaceAndRefusesReservedKeys() {
    assertThrows(IllegalArgumentException.class, () -> Event.of("nonamespace").build());
    assertThrows(IllegalArgumentException.class, () -> Event.of("a:b").put("seq", 1).build());
    String json = Event.of("a:b").put("n", 1).put("s", "x\"y").build().numbered(3).toJson();
    assertTrue(json.startsWith("{\"seq\":3,\"name\":\"a:b\""), json);
    assertTrue(json.endsWith("\"n\":1,\"s\":\"x\\\"y\"}"), json);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Condition not met within 5 s");
      }
      Thread.sleep(5);
    }
  }
}
