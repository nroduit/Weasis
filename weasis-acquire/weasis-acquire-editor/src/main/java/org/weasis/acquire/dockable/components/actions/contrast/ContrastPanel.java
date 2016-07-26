package org.weasis.acquire.dockable.components.actions.contrast;

import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.contrast.comp.BrightnessComponent;
import org.weasis.acquire.dockable.components.actions.contrast.comp.ContrastComponent;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.operations.impl.AutoLevelListener;
import org.weasis.core.api.image.BrightnessOp;

public class ContrastPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = -3978989511436089997L;

    private final AbstractSliderComponent contrastPanel = new ContrastComponent(this);
    private final AbstractSliderComponent brightnessPanel = new BrightnessComponent(this);

    private JCheckBox autoLevelBtn = new JCheckBox("Auto Level");

    private AutoLevelListener autoLevelListener = new AutoLevelListener();

    private JPanel content = new JPanel(new GridLayout(3, 1, 0, 10));

    public ContrastPanel() {
        super();
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        autoLevelBtn.addActionListener(autoLevelListener);

        content.add(contrastPanel);
        content.add(brightnessPanel);
        content.add(autoLevelBtn);

        add(content, BorderLayout.NORTH);
    }

    @Override
    public void updateOperations() {
        AcquireImageInfo imageInfo = AcquireObject.getImageInfo();
        imageInfo.getPreProcessOpManager().removeAllImageOperationAction();

        BrightnessOp brightness = new BrightnessOp();
        brightness.setParam(BrightnessOp.P_BRIGTNESS_VALUE, (double) brightnessPanel.getSliderValue());
        brightness.setParam(BrightnessOp.P_CONTRAST_VALUE, (double) contrastPanel.getSliderValue());

        imageInfo.getNextValues().setBrightness(brightnessPanel.getSliderValue());
        imageInfo.getNextValues().setContrast(contrastPanel.getSliderValue());

        imageInfo.removePreProcessImageOperationAction(BrightnessOp.class);
        imageInfo.addPreProcessImageOperationAction(brightness);

        imageInfo.applyPreProcess(AcquireObject.getView());
        repaint();
    }

    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        contrastPanel.setSliderValue(values.getContrast());
        brightnessPanel.setSliderValue(values.getBrightness());
        autoLevelBtn.setSelected(values.isAutoLevel());
        repaint();
    }

}
