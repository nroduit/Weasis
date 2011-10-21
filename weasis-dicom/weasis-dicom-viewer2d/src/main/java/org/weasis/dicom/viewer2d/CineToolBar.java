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
        super("Cine Toolbar", TYPE.tool);

        ActionState sequence = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener) {
            final SliderCineListener cineAction = (SliderCineListener) sequence;
            final JButton rwdButton = new JButton();
            rwdButton.setToolTipText("Cine Go to Start");
            rwdButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-rwd.png"))); //$NON-NLS-1$
            rwdButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    cineAction.setValue(0);
                }
            });
            add(rwdButton);

            final JButton prevButton = new JButton();
            prevButton.setToolTipText("Cine Previous Image");
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
            pauseButton.setToolTipText("Cine Pause");
            pauseButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-pause.png"))); //$NON-NLS-1$
            pauseButton.addActionListener(EventManager.getInstance());
            add(pauseButton);

            final JButton startButton = new JButton();
            startButton.setActionCommand(ActionW.CINESTART.cmd());
            startButton.setToolTipText("Cine Play");
            startButton.setIcon(new ImageIcon(CineToolBar.class.getResource("/icon/24x24/player-play.png"))); //$NON-NLS-1$
            startButton.addActionListener(EventManager.getInstance());
            add(startButton);

            final JButton stopButton = new JButton();
            stopButton.setToolTipText("Cine Stop");
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
            nextButton.setToolTipText("Cine Next Image");
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
            fwdButton.setToolTipText("Cine Go to End");
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
