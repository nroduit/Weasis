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

import java.awt.Component;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The application-wide event bus: publishers hand it an {@link Event} and return at once, a
 * dispatcher thread numbers the events in one global sequence, keeps the last ones in a ring buffer
 * and fans them out to the bounded queue of each subscriber. A subscriber receives its events in
 * order, one delivery at a time, on a thread of the bus or on the event dispatch thread; one that
 * falls behind loses the oldest pending events, is told so, and is detached when it keeps falling
 * behind. Nothing here waits for the EDT, so an EDT that waits for the bus cannot deadlock it.
 */
public final class EventBus {
  private static final Logger LOGGER = LoggerFactory.getLogger(EventBus.class);

  /** Event of the workspace engine that ends a subscription bound to a view. */
  public static final String VIEW_CLOSED = "workspace:view.closed"; // NON-NLS

  /** Payload key naming the view in the workspace events. */
  public static final String VIEW_KEY = "view"; // NON-NLS

  /** Pending events a subscriber may hold before the oldest is dropped. */
  public static final int QUEUE_CAPACITY = 2048;

  /**
   * Events a subscriber may lose without ever catching up (draining its queue) before it is
   * detached rather than slowed down: three queues' worth.
   */
  public static final int DETACH_AFTER_DROPS = 3 * QUEUE_CAPACITY;

  /** An EDT handler longer than this is logged with the event name. */
  static final long SLOW_HANDLER_MS = 50;

  private static final int INBOX_CAPACITY = 1 << 16;
  private static final int DEFAULT_RING_COUNT = 1000;
  private static final long DEFAULT_RING_BYTES = 1L << 20;

  private final LinkedBlockingQueue<Event> inbox = new LinkedBlockingQueue<>(INBOX_CAPACITY);
  private final AtomicLong sequence = new AtomicLong();
  private final AtomicLong lostAtInbox = new AtomicLong();
  private final AtomicInteger ids = new AtomicInteger();
  private final RingBuffer ring;
  private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
  private final EventCatalog catalog = new EventCatalog();
  private final Thread dispatcher;
  private volatile Executor edt = SwingUtilities::invokeLater;
  private volatile UnaryOperator<Event> remoteMasker = EventBus::withoutIdentity;
  private volatile boolean closed;

  /** The bus of the application. */
  public static EventBus global() {
    return Holder.INSTANCE;
  }

  private static final class Holder {
    private static final EventBus INSTANCE = new EventBus();
  }

  public EventBus() {
    this(DEFAULT_RING_COUNT, DEFAULT_RING_BYTES);
  }

  /**
   * @param ringCount how many events the ring buffer keeps at most
   * @param ringBytes how many bytes of JSON the ring buffer keeps at most
   */
  public EventBus(int ringCount, long ringBytes) {
    this.ring = new RingBuffer(ringCount, ringBytes);
    catalog.declare(WeasisEvents.types());
    this.dispatcher = Thread.ofVirtual().name("event-bus").start(this::dispatch); // NON-NLS
  }

  public EventCatalog catalog() {
    return catalog;
  }

  /** Publishes without waiting; the event is numbered and delivered by the dispatcher. */
  public void publish(Event event) {
    if (closed || event == null) {
      return;
    }
    if (!inbox.offer(event)) {
      long lost = lostAtInbox.incrementAndGet();
      if (lost == 1 || lost % 1000 == 0) {
        LOGGER.warn("Event bus inbox full, {} event(s) lost (last: {})", lost, event.name());
      }
    }
  }

  public void publish(Event.Builder event) {
    publish(event.build());
  }

  /** The sequence number of the last event dispatched. */
  public long lastSequence() {
    return sequence.get();
  }

  /** The buffered events after {@code since} that match, oldest first, at most {@code limit}. */
  public List<Event> recent(long since, int limit, EventFilter filter) {
    return ring.after(since, limit, filter == null ? EventFilter.ALL : filter);
  }

  /** Subscribes under a short owner name, for the listing of subscribers. */
  public Subscription subscribe(
      EventFilter filter, Subscription.Delivery delivery, String owner, Consumer<Event> handler) {
    Subscriber subscriber =
        new Subscriber(
            "s" + ids.incrementAndGet(),
            owner == null ? "anonymous" : owner, // NON-NLS
            filter == null ? EventFilter.ALL : filter,
            delivery,
            handler);
    subscribers.add(subscriber);
    return subscriber;
  }

  public Subscription subscribe(
      EventFilter filter, Subscription.Delivery delivery, Consumer<Event> handler) {
    return subscribe(filter, delivery, null, handler);
  }

  /**
   * Waits for the first event matching {@code filter} published after {@code since}, looking in the
   * ring buffer first so an event that happened between the caller's last look and now is not
   * missed; empty when {@code timeout} passes first.
   */
  public Optional<Event> await(EventFilter filter, long since, Duration timeout)
      throws InterruptedException {
    CompletableFuture<Event> first = new CompletableFuture<>();
    try (Subscription ignored =
        subscribe(filter, Subscription.Delivery.ASYNC, "await", first::complete)) { // NON-NLS
      recent(since, 1, filter).stream().findFirst().ifPresent(first::complete);
      return Optional.of(first.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
    } catch (TimeoutException e) {
      return Optional.empty();
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getCause());
    }
  }

  /** The active subscriptions, in subscription order. */
  public List<Subscription> subscriptions() {
    return List.copyOf(subscribers);
  }

  /** How EDT subscribers are reached; a test replaces it. */
  public void setEdtExecutor(Executor executor) {
    this.edt = executor == null ? SwingUtilities::invokeLater : executor;
  }

  /**
   * How an event is rewritten before it leaves the process for a remote subscriber. The default
   * removes every payload field that carries identity; the module that knows the masking profiles
   * installs one that masks them the way the command results are masked.
   */
  public void setRemoteMasker(UnaryOperator<Event> masker) {
    this.remoteMasker = masker == null ? EventBus::withoutIdentity : masker;
  }

  /** The event as a remote subscriber may see it. */
  public Event maskForRemote(Event event) {
    if (event.identity().isEmpty()) {
      return event;
    }
    try {
      return remoteMasker.apply(event);
    } catch (RuntimeException e) {
      LOGGER.warn("Masking {} failed, identity fields removed", event.name(), e);
      return withoutIdentity(event);
    }
  }

  static Event withoutIdentity(Event event) {
    Map<String, Object> kept = new LinkedHashMap<>(event.payload());
    event.identity().keySet().forEach(kept::remove);
    return event.withPayload(kept, Map.of());
  }

  /** Stops the dispatcher and detaches every subscriber; for tests and shutdown. */
  public void close() {
    closed = true;
    dispatcher.interrupt();
    subscribers.forEach(Subscriber::close);
  }

  private void dispatch() {
    while (!closed) {
      Event event;
      try {
        event = inbox.take();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      Event numbered = event.numbered(sequence.incrementAndGet());
      ring.add(numbered);
      for (Subscriber subscriber : subscribers) {
        subscriber.offer(numbered);
      }
    }
  }

  /** The last events, bounded by count and by bytes of JSON. */
  private static final class RingBuffer {
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private final ArrayDeque<Integer> sizes = new ArrayDeque<>();
    private final int maxCount;
    private final long maxBytes;
    private long bytes;

    RingBuffer(int maxCount, long maxBytes) {
      this.maxCount = Math.max(1, maxCount);
      this.maxBytes = Math.max(1024, maxBytes);
    }

    synchronized void add(Event event) {
      if (event.coalesceKey() != null) {
        forgetEarlier(event.coalesceKey());
      }
      int size = event.toJson().length();
      events.addLast(event);
      sizes.addLast(size);
      bytes += size;
      while (events.size() > maxCount || (bytes > maxBytes && events.size() > 1)) {
        events.pollFirst();
        bytes -= sizes.pollFirst();
      }
    }

    /** A coalesced event keeps only its last occurrence: a replay sees a run as one event. */
    private void forgetEarlier(String key) {
      java.util.Iterator<Event> it = events.iterator();
      java.util.Iterator<Integer> sizesIt = sizes.iterator();
      while (it.hasNext()) {
        Event pending = it.next();
        int size = sizesIt.next();
        if (key.equals(pending.coalesceKey())) {
          it.remove();
          sizesIt.remove();
          bytes -= size;
        }
      }
    }

    synchronized List<Event> after(long since, int limit, EventFilter filter) {
      List<Event> found = new ArrayList<>();
      for (Event event : events) {
        if (event.seq() > since && filter.matches(event)) {
          found.add(event);
          if (found.size() >= limit) {
            break;
          }
        }
      }
      return found;
    }
  }

  private final class Subscriber implements Subscription {
    private final String id;
    private final String owner;
    private final EventFilter filter;
    private final Delivery delivery;
    private final Consumer<Event> handler;
    private final ArrayDeque<Event> queue = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private volatile boolean active = true;
    private volatile Component ownerComponent;
    private volatile Subscription viewWatch;
    private HierarchyListener displayability;
    private long dropped;
    private long droppedSinceDrain;
    private boolean noticed;
    private boolean scheduled;
    private boolean detaching;

    Subscriber(
        String id, String owner, EventFilter filter, Delivery delivery, Consumer<Event> handler) {
      this.id = id;
      this.owner = owner;
      this.filter = filter;
      this.delivery = delivery;
      this.handler = handler;
      if (delivery == Delivery.ASYNC) {
        Thread.ofVirtual().name("event-" + id).start(this::work); // NON-NLS
      }
    }

    void offer(Event event) {
      if (!active || !filter.matches(event)) {
        return;
      }
      boolean schedule;
      lock.lock();
      try {
        if (detaching) {
          return;
        }
        if (event.coalesceKey() != null) {
          queue.removeIf(pending -> event.coalesceKey().equals(pending.coalesceKey()));
        }
        if (queue.size() >= QUEUE_CAPACITY) {
          overflow(event);
        } else {
          queue.addLast(event);
        }
        schedule = delivery == Delivery.EDT && !scheduled;
        if (schedule) {
          scheduled = true;
        }
        available.signal();
      } finally {
        lock.unlock();
      }
      if (schedule) {
        edt.execute(this::drainOnEdt);
      }
    }

    /**
     * Drops the oldest pending event and tells the subscriber once per overflow episode; detaches
     * it when it lost three queues' worth without ever draining.
     */
    private void overflow(Event event) {
      queue.pollFirst();
      dropped++;
      droppedSinceDrain++;
      queue.addLast(event);
      boolean detach = droppedSinceDrain >= DETACH_AFTER_DROPS;
      if (detach) {
        queue.clear();
        detaching = true;
      }
      if (detach || !noticed) {
        noticed = true;
        queue.addLast(
            Event.of(WeasisEvents.SUBSCRIBER_DROPPED)
                .put("subscriber", id) // NON-NLS
                .put("dropped", dropped) // NON-NLS
                .put("detached", detach) // NON-NLS
                .build()
                .numbered(event.seq()));
      }
      if (detach) {
        LOGGER.warn("Event subscriber {} ({}) detached after {} drops", id, owner, dropped);
      }
    }

    private void work() {
      while (active) {
        List<Event> batch;
        lock.lock();
        try {
          while (queue.isEmpty() && active) {
            available.await();
          }
          batch = new ArrayList<>(queue);
          queue.clear();
          caughtUp();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        } finally {
          lock.unlock();
        }
        deliver(batch);
      }
    }

    /** The queue is empty again: the overflow episode, if any, is over. Called under the lock. */
    private void caughtUp() {
      if (!detaching) {
        droppedSinceDrain = 0;
        noticed = false;
      }
    }

    private void drainOnEdt() {
      List<Event> batch;
      lock.lock();
      try {
        batch = new ArrayList<>(queue);
        queue.clear();
        caughtUp();
        scheduled = false;
      } finally {
        lock.unlock();
      }
      deliver(batch);
    }

    private void deliver(List<Event> batch) {
      for (Event event : batch) {
        if (!active) {
          return;
        }
        long start = System.nanoTime();
        try {
          handler.accept(event);
        } catch (RuntimeException e) {
          LOGGER.warn("Event subscriber {} ({}) failed on {}", id, owner, event.name(), e);
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (delivery == Delivery.EDT && millis > SLOW_HANDLER_MS) {
          LOGGER.warn(
              "Event subscriber {} ({}) held the EDT {} ms on {}", id, owner, millis, event.name());
        }
      }
      if (detaching) {
        detach();
      }
    }

    /** Hands over the notice of the detachment, which is what the queue holds now, then closes. */
    private void detach() {
      List<Event> rest;
      lock.lock();
      try {
        rest = new ArrayList<>(queue);
        queue.clear();
      } finally {
        lock.unlock();
      }
      for (Event event : rest) {
        try {
          handler.accept(event);
        } catch (RuntimeException e) {
          LOGGER.warn("Event subscriber {} ({}) failed on {}", id, owner, event.name(), e);
        }
      }
      close();
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String owner() {
      return owner;
    }

    @Override
    public EventFilter filter() {
      return filter;
    }

    @Override
    public Delivery delivery() {
      return delivery;
    }

    @Override
    public boolean isActive() {
      return active;
    }

    @Override
    public long dropped() {
      lock.lock();
      try {
        return dropped;
      } finally {
        lock.unlock();
      }
    }

    @Override
    public Subscription until(Component component) {
      if (component != null) {
        ownerComponent = component;
        displayability =
            e -> {
              if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                  && !component.isDisplayable()) {
                close();
              }
            };
        component.addHierarchyListener(displayability);
      }
      return this;
    }

    @Override
    public Subscription untilViewClosed(String viewId) {
      if (viewId != null) {
        viewWatch =
            subscribe(
                new EventFilter(List.of(VIEW_CLOSED), Map.of(VIEW_KEY, viewId)),
                Delivery.ASYNC,
                "until:" + id, // NON-NLS
                event -> close());
      }
      return this;
    }

    @Override
    public boolean isDangling() {
      Component component = ownerComponent;
      return component != null && !component.isDisplayable();
    }

    @Override
    public void close() {
      if (!active) {
        return;
      }
      active = false;
      subscribers.remove(this);
      lock.lock();
      try {
        queue.clear();
        available.signalAll();
      } finally {
        lock.unlock();
      }
      Component component = ownerComponent;
      if (component != null && displayability != null) {
        component.removeHierarchyListener(displayability);
      }
      Subscription watch = viewWatch;
      if (watch != null) {
        watch.close();
      }
    }
  }
}
