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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.weasis.core.util.LangUtil;

public class ViewSynchData extends SynchData {

  protected final Set<ManualSyncData> manualSyncDataSet = ConcurrentHashMap.newKeySet();

  /**
   * Per-view explicit overrides of the propagation rules. Any entry present here takes precedence
   * over {@link #getActions()} (which is the shared global template). The override map is owned by
   * this view, so it is not affected by default-resetting code paths (e.g. {@code
   * configureScrollOnlyActions}) that mutate the shared template, and it survives view selection
   * switches.
   */
  private final Map<String, Boolean> userActionOverrides = new HashMap<>();

  private boolean orphan; // view that does not share frUID, not synced automatically
  private boolean canBeManuallySynced;
  private boolean canBeAutoSynced;
  private String frameOfReferenceUID;

  public ViewSynchData(Mode mode, Map<String, Boolean> actions, boolean synch) {
    super(mode, actions, synch);
    this.orphan = false;
    this.canBeManuallySynced = false;
    this.canBeAutoSynced = true;
  }

  public ViewSynchData(ViewSynchData synchData) {
    super(synchData);
    this.orphan = synchData.orphan;
    this.canBeManuallySynced = synchData.canBeManuallySynced;
    this.canBeAutoSynced = synchData.canBeAutoSynced;
    this.frameOfReferenceUID = synchData.frameOfReferenceUID;
    this.userActionOverrides.putAll(synchData.userActionOverrides);
  }

  /**
   * Per-view overrides set by the user from the per-view sync options popup. Reads from this map
   * take precedence over the shared template (returned by {@link #getActions()}).
   */
  public Map<String, Boolean> getUserActionOverrides() {
    return userActionOverrides;
  }

  /** Set or clear an explicit per-view override for a single action command. */
  public void setUserActionOverride(String cmd, boolean enabled) {
    if (cmd != null) {
      userActionOverrides.put(cmd, enabled);
    }
  }

  /** Drop every per-view override, falling back to the shared template's values. */
  public void clearUserActionOverrides() {
    userActionOverrides.clear();
  }

  @Override
  public boolean isActionEnable(String action) {
    Boolean override = userActionOverrides.get(action);
    if (override != null) {
      return override;
    }
    return LangUtil.nullToFalse(getActions().get(action));
  }

  public boolean isOrphan() {
    return orphan;
  }

  public void setOrphan(boolean orphan) {
    this.orphan = orphan;
  }

  @Override
  public ViewSynchData copy() {
    ViewSynchData synchData = new ViewSynchData(this);
    // synchData.original = false;
    return synchData;
  }

  public void addManualSyncData(
      double sourceLocation, double targetLocation, ViewCanvas<?> targetPane) {
    this.manualSyncDataSet.add(new ManualSyncData(sourceLocation, targetLocation, targetPane));
  }

  public void removeManualSyncData(ManualSyncData manualSyncData) {
    this.manualSyncDataSet.remove(manualSyncData);
  }

  public void clearManualSyncData() {
    this.manualSyncDataSet.clear();
  }

  public Set<ManualSyncData> getManualSyncDataSet() {
    return manualSyncDataSet;
  }

  /**
   * @return the {@link ManualSyncData} entry whose target is the given pane (identity comparison),
   *     or {@code null} if this view is not manually synchronized with that pane.
   */
  public ManualSyncData getManualSyncDataByPane(ViewCanvas<?> targetPane) {
    for (ManualSyncData data : manualSyncDataSet) {
      if (data.getTargetPane() == targetPane) { // identity: panes are unique view instances
        return data;
      }
    }
    return null;
  }

  public boolean canBeManuallySynced() {
    return canBeManuallySynced;
  }

  public void setCanBeManuallySynced(boolean canBeManuallySynced) {
    this.canBeManuallySynced = canBeManuallySynced;
  }

  public boolean canBeAutoSynced() {
    return canBeAutoSynced;
  }

  public void setCanBeAutoSynced(boolean canBeAutoSynced) {
    this.canBeAutoSynced = canBeAutoSynced;
  }

  public String getFrameOfReferenceUID() {
    return frameOfReferenceUID;
  }

  public void setFrameOfReferenceUID(String frameOfReferenceUID) {
    this.frameOfReferenceUID = frameOfReferenceUID;
  }

  public static class ManualSyncData {

    protected double sourceLocation;
    protected double targetLocation;
    protected ViewCanvas<?> targetPane;

    public ManualSyncData(double sourceLocation, double targetLocation, ViewCanvas<?> targetPane) {
      this.sourceLocation = sourceLocation;
      this.targetLocation = targetLocation;
      this.targetPane = targetPane;
    }

    public double getSourceLocation() {
      return sourceLocation;
    }

    public void setSourceLocation(double sourceLocation) {
      this.sourceLocation = sourceLocation;
    }

    public double getTargetLocation() {
      return targetLocation;
    }

    public void setTargetLocation(double targetLocation) {
      this.targetLocation = targetLocation;
    }

    public ViewCanvas<?> getTargetPane() {
      return targetPane;
    }

    public void setTargetPane(ViewCanvas<?> targetPane) {
      this.targetPane = targetPane;
    }
  }
}
