package org.weasis.core.ui.editor.image;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JToggleButton;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.WtoolBar;

public class RotationToolBar<E extends ImageElement> extends WtoolBar {

    public RotationToolBar(final ImageViewerEventManager<E> eventManager) {
        super(Messages.getString("RotationToolBar.rotationBar"), TYPE.tool); //$NON-NLS-1$
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }

        // TODO change icon
        final JButton jButtonRotate90 =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/rotate.png"))); //$NON-NLS-1$
        jButtonRotate90.setToolTipText(Messages.getString("RotationToolBar.90")); //$NON-NLS-1$
        jButtonRotate90.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
                if (rotateAction instanceof SliderChangeListener) {
                    final SliderChangeListener rotation = (SliderChangeListener) rotateAction;
                    rotation.setValue((rotation.getValue() + 90) % 360);
                }
            }
        });
        add(jButtonRotate90);

        // TODO change icon
        final JToggleButton jButtonFlip =
            new JToggleButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/flip.png"))); //$NON-NLS-1$
        jButtonFlip.setToolTipText(Messages.getString("RotationToolBar.flip")); //$NON-NLS-1$
        ActionState flipAction = eventManager.getAction(ActionW.FLIP);
        if (flipAction instanceof ToggleButtonListener) {
            ((ToggleButtonListener) flipAction).registerComponent(jButtonFlip);
        }
        add(jButtonFlip);

    }
}
