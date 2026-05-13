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
import org.weasis.core.util.LangUtil;

public class SynchData implements Copyable<SynchData> {

  public enum Mode {
    STACK,
    TILE
  }

  /** Domain-level synchronization state. Independent of any UI widget. */
  public enum SyncState {
    OFF,
    ON
  }

  protected final Map<String, Boolean> actions;
  protected final Mode mode;
  protected SyncState autoSyncState;

  /**
   * Indicates whether this SynchData has not been user-modified since the last {@code
   * updateAllListeners} call. When {@code true}, the sync configuration is in its default/computed
   * state. Set to {@code false} when the user manually toggles sync on/off.
   */
  private boolean original;

  protected SyncState manualSyncState;

  public SynchData(Mode mode, Map<String, Boolean> actions, boolean synch) {
    if (actions == null) {
      throw new IllegalArgumentException("A parameter is null!");
    }
    this.actions = actions;
    this.mode = mode;
    this.original = true;
    this.autoSyncState = synch ? SyncState.ON : SyncState.OFF;
    this.manualSyncState = SyncState.OFF;
  }

  public SynchData(SynchData synchData) {
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

  public SyncState getAutoSyncState() {
    return autoSyncState;
  }

  public void setAutoSyncState(SyncState state) {
    this.autoSyncState = state;
  }

  public SyncState getManualSyncState() {
    return manualSyncState;
  }

  public void setManualSyncState(SyncState state) {
    this.manualSyncState = state;
  }

  public boolean isSynchActivated() {
    return autoSyncState == SyncState.ON || manualSyncState == SyncState.ON;
  }

  public boolean isAutoSynchActivated() {
    return autoSyncState == SyncState.ON;
  }

  public boolean isManualSynchActivated() {
    return manualSyncState == SyncState.ON;
  }

  @Override
  public SynchData copy() {
    return new SynchData(this);
  }

  public boolean isOriginal() {
    return original;
  }

  public void setOriginal(boolean original) {
    this.original = original;
  }
}
