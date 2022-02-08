/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.rectify;

import javax.swing.Icon;
import javax.swing.JButton;
import org.weasis.acquire.Messages;
import org.weasis.acquire.operations.impl.RotationActionListener;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;

public class Rotate90Button extends JButton {

  private static final int ANGLE = 90;
  private static final Icon ICON = ResourceUtil.getIcon(ActionIcon.ROTATE_CLOCKWISE);
  private static final String TOOL_TIP = Messages.getString("EditionTool.rotate.90");

  public Rotate90Button(RectifyAction rectifyAction) {
    super(ICON);
    setToolTipText(TOOL_TIP);
    addActionListener(new RotationActionListener(ANGLE, rectifyAction));
  }
}
