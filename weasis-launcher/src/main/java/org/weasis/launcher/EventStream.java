/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventFilter;
import org.weasis.core.api.event.Subscription;
import org.weasis.core.api.event.WeasisEvents;

/**
 * Serves one {@code si.sub} connection: replays the ring buffer after the sequence the client last
 * saw, then writes every matching event as one JSON line as the bus delivers it, masked for a
 * remote reader, with a heartbeat when nothing happens. The writer is the subscriber's own delivery
 * thread, so a slow reader slows only itself: the bus drops its oldest pending events and detaches
 * it when it keeps falling behind, never the viewer.
 */
final class EventStream {
  private static final Logger LOGGER = LoggerFactory.getLogger(EventStream.class);

  /** Seconds between two heartbeats on an idle stream (system property). */
  static final String HEARTBEAT_PROP = "weasis.commands.events.heartbeat"; // NON-NLS

  private static final int DEFAULT_HEARTBEAT_SECONDS = 15;

  private final CommandManager.EventStreamGrant grant;
  private final PrintStream out;
  private final CountDownLatch ended = new CountDownLatch(1);
  private final Object writeLock = new Object();
  private final List<Event> heldBack = new ArrayList<>();
  private volatile long lastWritten = System.nanoTime();
  private long replayedUpTo;
  private boolean replaying = true;

  private EventStream(CommandManager.EventStreamGrant grant, PrintStream out) {
    this.grant = grant;
    this.out = out;
  }

  /** Serves the connection until the client closes it or the stream fails; returns then. */
  static void serve(
      Socket socket,
      BufferedReader in,
      PrintStream out,
      CommandProtocol.Subscribe request,
      CommandManager.EventStreamGrant grant) {
    EventStream stream = new EventStream(grant, out);
    EventFilter filter = grant.filter();
    CommandProtocol.writeStreamStart(out);
    try (grant;
        Subscription subscription =
            grant
                .bus()
                .subscribe(
                    filter,
                    Subscription.Delivery.ASYNC,
                    "remote:" + socket.getPort(), // NON-NLS
                    stream::deliver)) {
      stream.replay(request.since(), filter);
      Thread.ofVirtual().name("si-events-reader").start(() -> stream.watchClient(in)); // NON-NLS
      stream.heartbeats(subscription);
    } finally {
      stream.ended.countDown();
    }
  }

  /** The buffered events after {@code since}, then whatever the bus delivered meanwhile. */
  private void replay(long since, EventFilter filter) {
    List<Event> buffered =
        since > 0 ? grant.bus().recent(since, Integer.MAX_VALUE, filter) : List.of();
    List<Event> late;
    synchronized (heldBack) {
      for (Event event : buffered) {
        write(event);
        replayedUpTo = event.seq();
      }
      late = heldBack.stream().filter(event -> event.seq() > replayedUpTo).toList();
      heldBack.clear();
      replaying = false;
    }
    late.forEach(this::write);
  }

  private void deliver(Event event) {
    synchronized (heldBack) {
      if (replaying) {
        heldBack.add(event);
        return;
      }
    }
    write(event);
  }

  private void write(Event event) {
    if (!grant.allows(event) && !event.name().startsWith("weasis:")) { // NON-NLS
      return;
    }
    synchronized (writeLock) {
      out.println(grant.mask(event).toJson());
      out.flush();
      lastWritten = System.nanoTime();
      if (out.checkError()) {
        ended.countDown();
      }
    }
  }

  /** Blocks until the client closes its side, which is how a stream normally ends. */
  private void watchClient(BufferedReader in) {
    try {
      while (in.readLine() != null) {
        // a client has nothing more to say; any line is ignored
      }
    } catch (IOException e) {
      // the connection is gone either way
    } finally {
      ended.countDown();
    }
  }

  private void heartbeats(Subscription subscription) {
    long interval = Duration.ofSeconds(heartbeatSeconds()).toNanos();
    try {
      while (subscription.isActive()) {
        long idle = System.nanoTime() - lastWritten;
        long wait = Math.max(interval - idle, TimeUnit.MILLISECONDS.toNanos(100));
        if (ended.await(wait, TimeUnit.NANOSECONDS)) {
          return;
        }
        if (System.nanoTime() - lastWritten >= interval) {
          write(Event.of(WeasisEvents.HEARTBEAT).build().numbered(grant.bus().lastSequence()));
        }
      }
      LOGGER.info("Event stream detached by the bus");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static int heartbeatSeconds() {
    return Math.max(1, Integer.getInteger(HEARTBEAT_PROP, DEFAULT_HEARTBEAT_SECONDS));
  }
}
