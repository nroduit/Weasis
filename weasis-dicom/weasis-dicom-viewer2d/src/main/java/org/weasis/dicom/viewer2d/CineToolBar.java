/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.util.WtoolBar;

@SuppressWarnings("serial")
public class CineToolBar<DicomImageElement> extends WtoolBar {

  public CineToolBar(int index) {
    super(Messages.getString("CineToolBar.name"), index);

    ActionState sequence = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
    if (sequence instanceof SliderCineListener) {
      final SliderCineListener cineAction = (SliderCineListener) sequence;
      WProperties p = BundleTools.SYSTEM_PREFERENCES;
      if (p.getBooleanProperty("weasis.cinetoolbar.gotostart", true)) {
        final JButton rwdButton = new JButton();
        rwdButton.setToolTipText(Messages.getString("CineToolBar.start"));
        rwdButton.setIcon(
            new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-rwd.png")));
        rwdButton.addActionListener(e -> cineAction.setSliderValue(0));
        add(rwdButton);
        sequence.registerActionState(rwdButton);
      }

      if (p.getBooleanProperty("weasis.cinetoolbar.prev", true)) {
        final JButton prevButton = new JButton();
        prevButton.setToolTipText(Messages.getString("CineToolBar.prev"));
        prevButton.setIcon(
            new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-prev.png")));
        prevButton.addActionListener(
            e -> {
              cineAction.stop();
              cineAction.setSliderValue(cineAction.getSliderValue() - 1);
            });
        add(prevButton);
        sequence.registerActionState(prevButton);
      }

      if (p.getBooleanProperty("weasis.cinetoolbar.pause", true)) {
        final JButton pauseButton = new JButton();
        pauseButton.setActionCommand(ActionW.CINESTOP.cmd());
        pauseButton.setToolTipText(Messages.getString("CineToolBar.pause"));
        pauseButton.setIcon(
            new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-pause.png")));
        pauseButton.addActionListener(EventManager.getInstance());
        add(pauseButton);
        sequence.registerActionState(pauseButton);
      }

      if (p.getBooleanProperty("weasis.cinetoolbar.start", true)) {
        final JButton startButton = new JButton();
        startButton.setActionCommand(ActionW.CINESTART.cmd());
        startButton.setToolTipText(Messages.getString("CineToolBar.play"));
        startButton.setIcon(
            new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-play.png")));
        startButton.addActionListener(EventManager.getInstance());
        add(startButton);
        sequence.registerActionState(startButton);
      }

      if (p.getBooleanProperty("weasis.cinetoolbar.stop", true)) {
        final JButton stopButton = new JButton();
        stopButton.setToolTipText(Messages.getString("CineToolBar.stop"));
        stopButton.setIcon(
            new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-stop.png")));
        stopButton.addActionListener(
            e -> {
              cineAction.stop();
              cineAction.setSliderValue(0);
            });
        add(stopButton);
        sequence.registerActionState(stopButton);
      }

      if (p.getBooleanProperty("weasis.cinetoolbar.next", true)) {
        final JButton nextButton = new JButton();
        nextButton.setToolTipText(Messages.getString("CineToolBar.next"));
        nextButton.setIcon(
            new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-next.png")));
        nextButton.addActionListener(
            e -> {
              cineAction.stop();
              cineAction.setSliderValue(cineAction.getSliderValue() + 1);
            });
        add(nextButton);
        sequence.registerActionState(nextButton);
      }

      if (p.getBooleanProperty("weasis.cinetoolbar.gotoend", true)) {
        final JButton fwdButton = new JButton();
        fwdButton.setToolTipText(Messages.getString("CineToolBar.end"));
        fwdButton.setIcon(
            new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-fwd.png")));
        fwdButton.addActionListener(e -> cineAction.setSliderValue(Integer.MAX_VALUE));
        add(fwdButton);
        sequence.registerActionState(fwdButton);
      }
    }
  }
}
