/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
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
        super(Messages.getString("CineToolBar.name"), index); //$NON-NLS-1$

        ActionState sequence = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener) {
            final SliderCineListener cineAction = (SliderCineListener) sequence;
            WProperties p = BundleTools.SYSTEM_PREFERENCES;
            if (p.getBooleanProperty("weasis.cinetoolbar.gotostart", true)) { //$NON-NLS-1$
                final JButton rwdButton = new JButton();
                rwdButton.setToolTipText(Messages.getString("CineToolBar.start")); //$NON-NLS-1$
                rwdButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-rwd.png"))); //$NON-NLS-1$
                rwdButton.addActionListener(e -> cineAction.setSliderValue(0));
                add(rwdButton);
                sequence.registerActionState(rwdButton);
            }

            if (p.getBooleanProperty("weasis.cinetoolbar.prev", true)) { //$NON-NLS-1$
                final JButton prevButton = new JButton();
                prevButton.setToolTipText(Messages.getString("CineToolBar.prev")); //$NON-NLS-1$
                prevButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-prev.png"))); //$NON-NLS-1$
                prevButton.addActionListener(e -> {
                    cineAction.stop();
                    cineAction.setSliderValue(cineAction.getSliderValue() - 1);
                });
                add(prevButton);
                sequence.registerActionState(prevButton);
            }

            if (p.getBooleanProperty("weasis.cinetoolbar.pause", true)) { //$NON-NLS-1$
                final JButton pauseButton = new JButton();
                pauseButton.setActionCommand(ActionW.CINESTOP.cmd());
                pauseButton.setToolTipText(Messages.getString("CineToolBar.pause")); //$NON-NLS-1$
                pauseButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-pause.png"))); //$NON-NLS-1$
                pauseButton.addActionListener(EventManager.getInstance());
                add(pauseButton);
                sequence.registerActionState(pauseButton);
            }

            if (p.getBooleanProperty("weasis.cinetoolbar.start", true)) { //$NON-NLS-1$
                final JButton startButton = new JButton();
                startButton.setActionCommand(ActionW.CINESTART.cmd());
                startButton.setToolTipText(Messages.getString("CineToolBar.play")); //$NON-NLS-1$
                startButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-play.png"))); //$NON-NLS-1$
                startButton.addActionListener(EventManager.getInstance());
                add(startButton);
                sequence.registerActionState(startButton);
            }

            if (p.getBooleanProperty("weasis.cinetoolbar.stop", true)) { //$NON-NLS-1$
                final JButton stopButton = new JButton();
                stopButton.setToolTipText(Messages.getString("CineToolBar.stop")); //$NON-NLS-1$
                stopButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-stop.png"))); //$NON-NLS-1$
                stopButton.addActionListener(e -> {
                    cineAction.stop();
                    cineAction.setSliderValue(0);
                });
                add(stopButton);
                sequence.registerActionState(stopButton);
            }

            if (p.getBooleanProperty("weasis.cinetoolbar.next", true)) { //$NON-NLS-1$
                final JButton nextButton = new JButton();
                nextButton.setToolTipText(Messages.getString("CineToolBar.next")); //$NON-NLS-1$
                nextButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-next.png"))); //$NON-NLS-1$
                nextButton.addActionListener(e -> {
                    cineAction.stop();
                    cineAction.setSliderValue(cineAction.getSliderValue() + 1);
                });
                add(nextButton);
                sequence.registerActionState(nextButton);
            }

            if (p.getBooleanProperty("weasis.cinetoolbar.gotoend", true)) { //$NON-NLS-1$
                final JButton fwdButton = new JButton();
                fwdButton.setToolTipText(Messages.getString("CineToolBar.end")); //$NON-NLS-1$
                fwdButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-fwd.png"))); //$NON-NLS-1$
                fwdButton.addActionListener(e -> cineAction.setSliderValue(Integer.MAX_VALUE));
                add(fwdButton);
                sequence.registerActionState(fwdButton);
            }
        }
    }

}
