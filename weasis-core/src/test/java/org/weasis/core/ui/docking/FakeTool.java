/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import java.awt.Component;
import javax.swing.JPanel;

/** A tool for the shell tests: an id, a name, a position and an empty panel. */
final class FakeTool implements DockableTool {
  private final String id;
  private final String name;
  private final int position;
  private final Slot preferred;
  private final double share;
  private final JPanel component = new JPanel();
  private boolean enabled = true;
  private int widthToFit;
  private String family;

  FakeTool(String id, int position) {
    this(id, position, Slot.TOP);
  }

  FakeTool(String id, int position, Slot preferred) {
    this(id, position, preferred, 0);
  }

  FakeTool(String id, int position, Slot preferred, double share) {
    this.id = id;
    this.name = id.toUpperCase();
    this.position = position;
    this.preferred = preferred;
    this.share = share;
  }

  @Override
  public String getToolId() {
    return id;
  }

  @Override
  public Slot getPreferredSlot() {
    return preferred;
  }

  @Override
  public double getPreferredBottomShare() {
    return share;
  }

  @Override
  public void showDockable() {}

  @Override
  public void closeDockable() {}

  @Override
  public Component getToolComponent() {
    return component;
  }

  @Override
  public String getFamily() {
    return family;
  }

  /** Puts the tool in the family of that explorer; its own id makes it an explorer. */
  FakeTool family(String family) {
    this.family = family;
    return this;
  }

  /** An explorer heading its own family. */
  static FakeTool explorer(String id, int position) {
    return new FakeTool(id, position).family(id);
  }

  /** A tool serving an explorer, opening at the bottom. */
  static FakeTool companion(String id, int position, String explorer) {
    return new FakeTool(id, position, Slot.BOTTOM).family(explorer);
  }

  @Override
  public int getWidthToFit() {
    return widthToFit;
  }

  FakeTool widthToFit(int width) {
    this.widthToFit = width;
    return this;
  }

  @Override
  public int getDockableWidth() {
    return 250;
  }

  @Override
  public String getComponentName() {
    return name;
  }

  @Override
  public Type getPanelType() {
    return Type.TOOL;
  }

  @Override
  public boolean isComponentEnabled() {
    return enabled;
  }

  @Override
  public void setComponentEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public int getComponentPosition() {
    return position;
  }

  @Override
  public void setComponentPosition(int position) {}
}
