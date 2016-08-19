package org.weasis.acquire.dockable.components.actions.rectify;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.Border;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.rectify.lib.AbstractRectifyButton;
import org.weasis.acquire.dockable.components.actions.rectify.lib.OrientationSliderComponent;
import org.weasis.acquire.dockable.components.actions.rectify.lib.btn.Rotate270Button;
import org.weasis.acquire.dockable.components.actions.rectify.lib.btn.Rotate90Button;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.operations.impl.FlipActionListener;
import org.weasis.acquire.operations.impl.RectifyOrientationChangeListener;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

public class RectifyPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = 4041145212218086219L;

    private final Border border = BorderFactory.createEmptyBorder(5, 10, 5, 10);

    private final OrientationSliderComponent orientationPanel;
    private final AbstractRectifyButton rotate90btn;
    private final AbstractRectifyButton rotate270btn;
    private final JCheckBox flipCheckBox = new JCheckBox("Flip Horizontally (after rotation)");

    private final RectifyAction rectifyAction;
    private final FlipActionListener flipActionListener;

    public RectifyPanel(RectifyAction rectifyAction) {
        this.rectifyAction = Objects.requireNonNull(rectifyAction);
        setLayout(new BorderLayout());
        orientationPanel = new OrientationSliderComponent(this);
        rotate90btn = new Rotate90Button(rectifyAction);
        rotate270btn = new Rotate270Button(rectifyAction);
        flipActionListener = new FlipActionListener(rectifyAction);
        add(createContent(), BorderLayout.NORTH);
    }

    private JPanel createContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel btnContent = new JPanel();
        btnContent.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnContent.add(rotate90btn);
        btnContent.add(rotate270btn);

        panel.add(orientationPanel);
        panel.add(btnContent);

        JPanel flipPanel = new JPanel();
        flipPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 0));
        flipPanel.setBorder(border);
        flipPanel.add(flipCheckBox);
        flipCheckBox.addActionListener(flipActionListener);
        panel.add(flipPanel);

        return panel;
    }

    public RectifyAction getRectifyAction() {
        return rectifyAction;
    }

    @Override
    public boolean needValidationPanel() {
        return true;
    }

    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        ViewCanvas<ImageElement> view = EventManager.getInstance().getSelectedViewPane();
        AcquireImageValues next = info.getNextValues();
        next.setFlip(values.isFlip());
        next.setOrientation(values.getOrientation());
        next.setRotation(values.getRotation());
        Rectangle2D area = Optional.ofNullable((Rectangle2D) info.getNextValues().getCropZone())
            .orElse(view.getViewModel().getModelArea());
        next.setCropZone(RectifyAction.adaptToinitCropArea(area));

        flipCheckBox.removeActionListener(flipActionListener);
        RectifyOrientationChangeListener listener = orientationPanel.getListener();
        orientationPanel.removeChangeListener(listener);
        flipCheckBox.setSelected(next.isFlip());
        orientationPanel.setSliderValue(next.getOrientation());
        orientationPanel.addChangeListener(listener);
        flipCheckBox.addActionListener(flipActionListener);
        repaint();

        flipActionListener.applyNextValues();
        listener.applyNextValues();
        
        info.applyPreProcess(view);
    }

}
