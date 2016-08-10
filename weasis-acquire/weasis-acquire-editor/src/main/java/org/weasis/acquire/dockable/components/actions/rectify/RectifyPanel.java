package org.weasis.acquire.dockable.components.actions.rectify;

import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.rectify.lib.AbstractRectifyButton;
import org.weasis.acquire.dockable.components.actions.rectify.lib.OrientationSliderComponent;
import org.weasis.acquire.dockable.components.actions.rectify.lib.btn.FlipButton;
import org.weasis.acquire.dockable.components.actions.rectify.lib.btn.Rotate270Button;
import org.weasis.acquire.dockable.components.actions.rectify.lib.btn.Rotate90Button;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.operations.impl.FlipActionListener;
import org.weasis.acquire.operations.impl.RotationActionListener;

public class RectifyPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = 4041145212218086219L;

    private JPanel content = new JPanel(new GridLayout(2, 1, 10, 10));
    private JPanel btnContent = new JPanel(new GridLayout(1, 3, 35, 35));
    
    private final AbstractSliderComponent orientationPanel = new OrientationSliderComponent(this);
    private final AbstractRectifyButton rotate90btn = new Rotate90Button();
    private final AbstractRectifyButton rotate270btn = new Rotate270Button();
    private final AbstractRectifyButton flipBtn = new FlipButton();
    
    public RectifyPanel() {
        super();
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        
        btnContent.setBorder(BorderFactory.createEmptyBorder(20, 35, 20, 35));
        
        btnContent.add(rotate90btn);
        btnContent.add(rotate270btn);
        btnContent.add(flipBtn);
        
        content.add(orientationPanel);
        content.add(btnContent);

        add(content, BorderLayout.NORTH);
    }
    
    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        orientationPanel.setSliderValue(values.getOrientation());
        ((RotationActionListener) rotate90btn.getActionListener()).setValue(values.getRotation());
        ((FlipActionListener) flipBtn.getActionListener()).setValue(values.isFlip());
        repaint();
    }
}
