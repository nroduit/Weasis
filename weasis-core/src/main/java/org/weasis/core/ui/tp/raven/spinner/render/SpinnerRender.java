/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.spinner.render;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * SpinnerRender is an interface that defines the methods that a SpinnerRender should implement.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/spinner-progress">spinner-progress</a>
 */
public interface SpinnerRender {
  boolean isDisplayStringAble();

  boolean isPaintComplete();

  void paintCompleteIndeterminate(
      Graphics2D g2, Component component, Rectangle rec, float last, float f, float p);

  void paintIndeterminate(Graphics2D g2, Component component, Rectangle rec, float f);

  void paintDeterminate(Graphics2D g2, Component component, Rectangle rec, float p);

  int getInsets();
}
