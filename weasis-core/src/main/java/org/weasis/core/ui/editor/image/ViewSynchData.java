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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ViewSynchData extends SynchData {

  protected final Set<ManualSyncData> manualSyncDataSet = ConcurrentHashMap.newKeySet();

  private boolean orphan; // view that does not share frUID, not synced automatically
  private boolean canBeManuallySynced;
  private String frameOfReferenceUID;

  public ViewSynchData(Mode mode, Map<String, Boolean> actions, boolean synch) {
    super(mode, actions, synch);
    this.orphan = false;
    this.canBeManuallySynced = false;
  }

  public ViewSynchData(ViewSynchData synchData) {
    super(synchData);
    this.orphan = synchData.orphan;
    this.canBeManuallySynced = synchData.canBeManuallySynced;
    this.frameOfReferenceUID = synchData.frameOfReferenceUID;
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

  public void emptyManualSyncDataSet() {
    this.manualSyncDataSet.clear();
  }

  public Set<ManualSyncData> getManualSyncDataSet() {
    return manualSyncDataSet;
  }

  public ManualSyncData getManualSyncDataByPane(ViewCanvas<?> targetPane) {
    for (ManualSyncData data : manualSyncDataSet) {
      if (data.getTargetPane() == targetPane) {
        return data;
      }
    }
    return null;
  }

  public boolean isCanBeManuallySynced() {
    return canBeManuallySynced;
  }

  public void setCanBeManuallySynced(boolean canBeManuallySynced) {
    this.canBeManuallySynced = canBeManuallySynced;
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
