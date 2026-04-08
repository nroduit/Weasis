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
import java.util.Objects;

public class ViewSynchData extends SynchData {

  protected ManualSyncData manualSyncData;

  private boolean orphan; // view that does not share frUID, not synced automatically
  private boolean canBeManuallySynced;

  public ViewSynchData(Mode mode, Map<String, Boolean> actions, boolean synch) {
    if (actions == null) {
      throw new IllegalArgumentException("A parameter is null!");
    }
    super(mode, actions, synch);
    this.orphan = false;
        this.canBeManuallySynced = false;
  }

  public ViewSynchData(ViewSynchData synchData) {
    // Deep copy ?
    Objects.requireNonNull(synchData);
    super(synchData);
    this.orphan = synchData.orphan;
    this.canBeManuallySynced = synchData.canBeManuallySynced;
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

  public void setManualSyncData(double sourceLocation, double targetLocation, ViewCanvas<?> targetPane) {
    this.manualSyncData = new ManualSyncData(sourceLocation, targetLocation, targetPane);
  }

  public void removeManualSyncData() {
    this.manualSyncData = null;
  }

  public ManualSyncData getManualSyncData() {
    return manualSyncData;
  }

    public boolean isCanBeManuallySynced() {
        return canBeManuallySynced;
    }

    public void setCanBeManuallySynced(boolean canBeManuallySynced) {
        this.canBeManuallySynced = canBeManuallySynced;
    }

    public class ManualSyncData {

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
