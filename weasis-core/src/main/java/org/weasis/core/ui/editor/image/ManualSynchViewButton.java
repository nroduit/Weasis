/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.Icon;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.editor.image.SynchData.SyncState;

/**
 * Per-view overlay button activating manual synchronization with another view (different
 * FrameOfReferenceUID, same orientation). Click opens a series-picker popup when several candidates
 * are eligible.
 *
 * <p>The button only renders the current {@link SyncState}; all state transitions are owned by
 * {@link DefaultView2d#updateSynchState()} which is the single source of truth.
 */
public class ManualSynchViewButton extends ViewButton {
  private static final FlatSVGIcon BASE_ICON =
      ResourceUtil.getIcon(ResourceUtil.ActionIcon.HAND, 20, 20);
  private static final Color BASE_COLOR = new Color(0x6E6E6E);

  private static final Map<SyncState, FlatSVGIcon> ICONS = new EnumMap<>(SyncState.class);

  static {
    ICONS.put(SyncState.OFF, derive(IconColor.ACTIONS_RED.getColor()));
    ICONS.put(SyncState.ON, derive(IconColor.ACTIONS_GREEN.getColor()));
  }

  private static FlatSVGIcon derive(Color color) {
    return GuiUtils.getDerivedIcon(BASE_ICON, new ColorFilter().add(BASE_COLOR, color));
  }

  protected SyncState state = SyncState.OFF;

  public ManualSynchViewButton(ShowPopup popup) {
    super(popup, ICONS.get(SyncState.OFF), "manualsync"); // NON-NLS
  }

  public SyncState getState() {
    return state;
  }

  public void setState(SyncState state) {
    this.state = state;
  }

  @Override
  public Icon getIcon() {
    return ICONS.get(state);
  }
}
