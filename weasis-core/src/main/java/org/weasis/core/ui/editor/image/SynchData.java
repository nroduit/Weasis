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
  protected State state;
  protected double sourceLocation;
  protected double targetLocation;
  protected String targetFrameOfReferenceUID;

  private boolean original;
  private boolean orphan;

  public SynchData(Mode mode, Map<String, Boolean> actions, boolean synch) {
    if (actions == null) {
      throw new IllegalArgumentException("A parameter is null!");
    }
    this.actions = actions;
    this.mode = mode;
    this.original = true;
    this.state = synch ? State.ON : State.OFF;
    this.orphan = false;
  }

  public SynchData(SynchData synchData) {
    // Deep copy ?
    Objects.requireNonNull(synchData);
    this.actions = new HashMap<>(synchData.actions);
    this.mode = synchData.mode;
    this.original = synchData.original;
    this.state = synchData.state;
    this.sourceLocation = synchData.sourceLocation;
    this.targetLocation = synchData.targetLocation;
    this.targetFrameOfReferenceUID = synchData.targetFrameOfReferenceUID;
    this.orphan = synchData.orphan;
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

  public State getState() {
    return state;
  }

  public void setState(State state) {
    this.state = state;
  }

  public boolean isOrphan() {
    return orphan;
  }

  public void setOrphan(boolean orphan) {
    this.orphan = orphan;
  }

  public String getTargetFrameOfReferenceUID() {
    return targetFrameOfReferenceUID;
  }

  public void setTargetFrameOfReferenceUID(String targetFrameOfReferenceUID) {
    this.targetFrameOfReferenceUID = targetFrameOfReferenceUID;
  }

  /**
   * @deprecated Use {@link #getState()} instead
   */
  @Deprecated
  public boolean isSynch() {
    return state == State.ON;
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

  /*public void setOriginal(boolean original) {
    this.original = original;
  }

  public boolean isManual() {
    return manual;
  }

  public void setManual(boolean manual) {
    this.manual = manual;
  }

  public double getSourceLocation() {
    return sourceLocation;
  }*/

  public void setSourceLocation(double sourceLocation) {
    this.sourceLocation = sourceLocation;
  }

  public double getTargetLocation() {
    return targetLocation;
  }

  public void setTargetLocation(double targetLocation) {
    this.targetLocation = targetLocation;
  }
}
