/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.weasis.core.api.util.Copyable;
import org.weasis.core.ui.editor.image.SynchViewButton.State;
import org.weasis.core.util.LangUtil;

public class SynchData implements Copyable<SynchData> {

  public enum Mode {
    STACK,
    TILE
  }
  protected final Map<String, Boolean> actions;
  protected final Mode mode;
  protected State autoSyncState;

  private boolean original;// view that does not share frUID, not synced automatically
  protected State manualSyncState;

  public SynchData(Mode mode, Map<String, Boolean> actions, boolean synch) {
    if (actions == null) {
      throw new IllegalArgumentException("A parameter is null!");
    }
    this.actions = actions;
    this.mode = mode;
    this.original = true;
    this.autoSyncState = synch ? State.ON : State.OFF;
    this.manualSyncState = State.OFF;
  }

  public SynchData(SynchData synchData) {
    // Deep copy ?
    Objects.requireNonNull(synchData);
    this.actions = new HashMap<>(synchData.actions);
    this.mode = synchData.mode;
    this.original = synchData.original;
    this.autoSyncState = synchData.autoSyncState;
    this.manualSyncState = synchData.manualSyncState;
  }

  public Map<String, Boolean> getActions() {
    return actions;
  }

  public boolean isActionEnable(String action) {
    return LangUtil.nullToFalse(actions.get(action));
  }

  public Mode getMode() {
    return mode;
  }

  public State getAutoSyncState() {
    return autoSyncState;
  }

  public void setAutoSyncState(State state) {
    this.autoSyncState = state;
  }

  public State getManualSyncState() {
    return manualSyncState;
  }

  public void setManualSyncState(State state) {
    this.manualSyncState = state;
  }

  public boolean isSynchActivated() {
    return autoSyncState == State.ON || manualSyncState == State.ON;
  }

  public boolean isAutoSynchActivated() {
    return autoSyncState == State.ON;
  }

  public boolean isManualSynchActivated() {
    return manualSyncState == State.ON;
  }

  @Override
  public SynchData copy() {
    SynchData synchData = new SynchData(this);
    // synchData.original = false;
    return synchData;
  }

  public boolean isOriginal() {
    return original;
  }

  public void setOriginal(boolean original) {
    this.original = original;
  }
}
