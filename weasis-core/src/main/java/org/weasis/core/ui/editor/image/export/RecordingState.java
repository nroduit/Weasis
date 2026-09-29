/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The recording in progress, if any, for the interface that shows it. One recording at a time: a
 * second one is refused while the first runs.
 */
public final class RecordingState {

  private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
  private static volatile AnimationRecorder active;

  private RecordingState() {}

  public static Optional<AnimationRecorder> active() {
    return Optional.ofNullable(active);
  }

  public static boolean isRecording() {
    return active != null;
  }

  /** Listeners run on the thread that starts or stops the recording, which is the EDT. */
  public static void addChangeListener(Runnable listener) {
    LISTENERS.add(listener);
  }

  public static void removeChangeListener(Runnable listener) {
    LISTENERS.remove(listener);
  }

  /** Claims the slot; {@code false} when another recording is running. */
  static boolean start(AnimationRecorder recorder) {
    if (active != null) {
      return false;
    }
    active = recorder;
    LISTENERS.forEach(Runnable::run);
    return true;
  }

  static void stop(AnimationRecorder recorder) {
    if (active == recorder) {
      active = null;
      LISTENERS.forEach(Runnable::run);
    }
  }
}
