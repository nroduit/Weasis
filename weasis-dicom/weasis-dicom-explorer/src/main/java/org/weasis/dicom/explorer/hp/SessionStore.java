/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.LayeredDocument;

/**
 * The sessions of the user: the private document {@code hp.sessions.json}, with the retention of
 * the settings applied on every write — by number, the oldest unpinned sessions beyond the maximum,
 * and by time, every session not kept or restored for more than the maximum days, pinned or not. A
 * purge is counted, never detailed, in the log: a session is patient data.
 */
public final class SessionStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionStore.class);

  private final LayeredDocument document;
  private final java.util.function.IntSupplier max;
  private final java.util.function.IntSupplier maxDays;

  /** The store of the application: the user document and the retention settings. */
  public SessionStore() {
    this(
        HangingProtocolDocuments.sessions(),
        () -> Settings.get(HpSettings.SESSIONS_MAX),
        () -> Settings.get(HpSettings.SESSIONS_MAX_DAYS));
  }

  SessionStore(
      LayeredDocument document,
      java.util.function.IntSupplier max,
      java.util.function.IntSupplier maxDays) {
    this.document = document;
    this.max = max;
    this.maxDays = maxDays;
  }

  /** Every session, the most recently updated first. */
  public List<Session> all() {
    List<Session> sessions = new ArrayList<>();
    for (LayeredDocument.Entry entry : document.entries()) {
      try {
        sessions.add(Session.fromJson(entry.json()));
      } catch (RuntimeException e) {
        LOGGER.warn("Skipping an unreadable session {}", entry.id());
      }
    }
    sessions.sort(Comparator.comparing(Session::updated).reversed());
    return sessions;
  }

  public Optional<Session> find(String id) {
    return all().stream().filter(s -> s.id().equals(id)).findFirst();
  }

  /** The most recent session showing a study. */
  public Optional<Session> latestOf(String studyUid) {
    return all().stream().filter(s -> s.studies().contains(studyUid)).findFirst();
  }

  /** Stores a session and applies the retention. */
  public void keep(Session session) throws IOException {
    document.put(session.toJson());
    purge(Instant.now());
  }

  public boolean delete(String id) throws IOException {
    return document.remove(id);
  }

  /** Marks a session as used now, so the retention by time starts again. */
  public void touch(Session session) throws IOException {
    document.put(session.withUpdated(Instant.now()).toJson());
  }

  /**
   * Deletes the sessions the retention excludes: older than the maximum days (pinned too), then the
   * oldest unpinned ones beyond the maximum number.
   *
   * @return how many were deleted
   */
  public int purge(Instant now) throws IOException {
    List<Session> sessions = all();
    List<Session> toDelete = new ArrayList<>();
    int days = maxDays.getAsInt();
    if (days > 0) {
      Instant limit = now.minus(Duration.ofDays(days));
      for (Session s : sessions) {
        if (s.updated().isBefore(limit)) {
          toDelete.add(s);
        }
      }
      sessions.removeAll(toDelete);
    }
    int count = max.getAsInt();
    if (count > 0) {
      List<Session> unpinned = sessions.stream().filter(s -> !s.pinned()).toList();
      int excess = sessions.size() - count;
      // the oldest first: the list is newest first
      for (int i = unpinned.size() - 1; i >= 0 && excess > 0; i--, excess--) {
        toDelete.add(unpinned.get(i));
      }
    }
    for (Session s : toDelete) {
      document.remove(s.id());
    }
    if (!toDelete.isEmpty()) {
      LOGGER.info("Retention deleted {} session(s)", toDelete.size());
    }
    return toDelete.size();
  }
}
