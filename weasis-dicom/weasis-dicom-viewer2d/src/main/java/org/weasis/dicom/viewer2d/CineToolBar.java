package org.weasis.dicom.viewer2d;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.ui.util.WtoolBar;

public class CineToolBar<DicomImageElement> extends WtoolBar {
    private final Logger LOGGER = LoggerFactory.getLogger(CineToolBar.class);

    public CineToolBar() {
        super(Messages.getString("CineToolBar.name"), TYPE.tool); //$NON-NLS-1$

        ActionState sequence = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener) {
            final SliderCineListener cineAction = (SliderCineListener) sequence;
            final JButton rwdButton = new JButton();
            rwdButton.setToolTipText(Messages.getString("CineToolBar.start")); //$NON-NLS-1$
            rwdButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-rwd.png"))); //$NON-NLS-1$
            rwdButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    cineAction.setValue(0);
                }
            });
            add(rwdButton);

            final JButton prevButton = new JButton();
            prevButton.setToolTipText(Messages.getString("CineToolBar.prev")); //$NON-NLS-1$
            prevButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-prev.png"))); //$NON-NLS-1$
            prevButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    cineAction.stop();
                    cineAction.setValue(cineAction.getValue() - 1);
                }
            });
            add(prevButton);

            final JButton pauseButton = new JButton();
            pauseButton.setActionCommand(ActionW.CINESTOP.cmd());
            pauseButton.setToolTipText(Messages.getString("CineToolBar.pause")); //$NON-NLS-1$
            pauseButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-pause.png"))); //$NON-NLS-1$
            pauseButton.addActionListener(EventManager.getInstance());
            add(pauseButton);

            final JButton startButton = new JButton();
            startButton.setActionCommand(ActionW.CINESTART.cmd());
            startButton.setToolTipText(Messages.getString("CineToolBar.play")); //$NON-NLS-1$
            startButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-play.png"))); //$NON-NLS-1$
            startButton.addActionListener(EventManager.getInstance());
            add(startButton);

            final JButton stopButton = new JButton();
            stopButton.setToolTipText(Messages.getString("CineToolBar.stop")); //$NON-NLS-1$
            stopButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-stop.png"))); //$NON-NLS-1$
            stopButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    cineAction.stop();
                    cineAction.setValue(0);
                }
            });
            add(stopButton);
            final JButton nextButton = new JButton();
            nextButton.setToolTipText(Messages.getString("CineToolBar.next")); //$NON-NLS-1$
            nextButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-next.png"))); //$NON-NLS-1$
            nextButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    cineAction.stop();
                    cineAction.setValue(cineAction.getValue() + 1);
                }
            });
            add(nextButton);

            final JButton fwdButton = new JButton();
            fwdButton.setToolTipText(Messages.getString("CineToolBar.end")); //$NON-NLS-1$
            fwdButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-fwd.png"))); //$NON-NLS-1$
            fwdButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    // No need to know the last series index
                    cineAction.setValue(Integer.MAX_VALUE);
                }
            });
            add(fwdButton);

        }
    }

}
