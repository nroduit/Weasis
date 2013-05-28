package org.weasis.dicom.viewer2d;

import java.awt.Toolkit;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;

import javax.swing.ImageIcon;
import javax.swing.JToggleButton;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.viewer2d.KOManager.SelectedImageFilter;

public class KeyObjectToolBar<DicomImageElement> extends WtoolBar {

    public static final ImageIcon KO_STAR_ICON = new ImageIcon(View2d.class.getResource("/icon/24x24/star_bw.png"));
    public static final ImageIcon KO_STAR_ICON_SELECTED;

    static {
        ImageFilter imageFilter = new SelectedImageFilter(new float[] { 1.0f, 0.78f, 0.0f }); // ORANGE
        ImageProducer imageProducer = new FilteredImageSource(KO_STAR_ICON.getImage().getSource(), imageFilter);
        KO_STAR_ICON_SELECTED = new ImageIcon(Toolkit.getDefaultToolkit().createImage(imageProducer));
    }

    public KeyObjectToolBar(int index) {
        super("KeyObject Tools", TYPE.tool, index);

        ActionState toggleKOSelectionAction = EventManager.getInstance().getAction(ActionW.KO_STATE);

        if (toggleKOSelectionAction instanceof ToggleButtonListener) {
            final JToggleButton toggleKOSelectionBtn = new JToggleButton();

            toggleKOSelectionBtn.setToolTipText("Toggle KO Selection");

            toggleKOSelectionBtn.setIcon(KO_STAR_ICON);
            toggleKOSelectionBtn.setSelectedIcon(KO_STAR_ICON_SELECTED);

            toggleKOSelectionAction.registerActionState(toggleKOSelectionBtn);
            add(toggleKOSelectionBtn);
        }
    }
}
