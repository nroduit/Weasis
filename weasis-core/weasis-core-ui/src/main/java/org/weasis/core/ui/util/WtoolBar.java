/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JToolBar;
import org.weasis.core.api.gui.Insertable;

@SuppressWarnings("serial")
public class WtoolBar extends JToolBar implements Toolbar {

  private final String barName;

  private int barPosition = 100;
  private Insertable attachedInsertable;

  public WtoolBar(String barName, int position) {
    FlowLayout flowLayout = new FlowLayout();
    flowLayout.setVgap(0);
    flowLayout.setHgap(0);
    flowLayout.setAlignment(FlowLayout.LEADING);
    setLayout(flowLayout);
    this.barName = barName;
    this.barPosition = position;
    this.setAlignmentX(LEFT_ALIGNMENT);
    this.setAlignmentY(TOP_ALIGNMENT);
    // Force toolbar to have the same color of the container
    this.setBackground(FlatUIUtils.getUIColor("Panel.background", Color.DARK_GRAY));
    addSeparator();
  }

  @Override
  public void addSeparator() {
    add(new JToolBar.Separator(new Dimension(3, 32)));
  }

  @Override
  public Type getType() {
    return Type.TOOLBAR;
  }

  @Override
  public int getComponentPosition() {
    return barPosition;
  }

  @Override
  public void setComponentPosition(int position) {
    this.barPosition = position;
  }

  public Insertable getAttachedInsertable() {
    return attachedInsertable;
  }

  public void setAttachedInsertable(Insertable attachedInsertable) {
    this.attachedInsertable = attachedInsertable;
  }

  @Override
  public String toString() {
    return "WtoolBar " + getName(); // NON-NLS
  }

  @Override
  public String getComponentName() {
    return barName;
  }

  @Override
  public final WtoolBar getComponent() {
    return this;
  }

  @Override
  public boolean isComponentEnabled() {
    return isEnabled();
  }

  @Override
  public void setComponentEnabled(boolean enabled) {
    if (isComponentEnabled() != enabled) {
      setEnabled(enabled);
    }
  }
}
