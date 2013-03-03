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
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.WtoolBar;

public class ZoomToolBar<E extends ImageElement> extends WtoolBar {

    public ZoomToolBar(final ImageViewerEventManager<E> eventManager, int index) {
        super(Messages.getString("ZoomToolBar.zoomBar"), TYPE.tool, index); //$NON-NLS-1$
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }

        final JButton jButtonActualZoom =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/zoom-original.png"))); //$NON-NLS-1$
        jButtonActualZoom.setToolTipText(Messages.getString("ViewerToolBar.zoom_1")); //$NON-NLS-1$
        jButtonActualZoom.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ActionState zoom = eventManager.getAction(ActionW.ZOOM);
                if (zoom instanceof SliderChangeListener) {
                    ((SliderChangeListener) zoom).setValue(eventManager.viewScaleToSliderValue(1.0));
                }
            }
        });
        add(jButtonActualZoom);

        final JButton jButtonBestFit =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/zoom-bestfit.png"))); //$NON-NLS-1$
        jButtonBestFit.setToolTipText(Messages.getString("ViewerToolBar.zoom_b")); //$NON-NLS-1$
        jButtonBestFit.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                // Pass the value 0.0 (convention: best fit zoom value) directly to the property change, otherwise the
                // value is adjusted by the BoundedRangeModel
                eventManager.firePropertyChange(ActionW.ZOOM.cmd(), null, 0.0);
                AuditLog.LOGGER.info("action:{} val:0.0", ActionW.ZOOM.cmd()); //$NON-NLS-1$
            }
        });
        add(jButtonBestFit);

        final JToggleButton jButtonLens =
            new JToggleButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/zoom-lens.png"))); //$NON-NLS-1$
        jButtonLens.setToolTipText(Messages.getString("ViewerToolBar.show_lens")); //$NON-NLS-1$
        ActionState lens = eventManager.getAction(ActionW.LENS);
        if (lens instanceof ToggleButtonListener) {
            ((ToggleButtonListener) lens).registerComponent(jButtonLens);
        }
        add(jButtonLens);

    }

}
