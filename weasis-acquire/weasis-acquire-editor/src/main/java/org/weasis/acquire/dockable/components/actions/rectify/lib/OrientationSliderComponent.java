package org.weasis.acquire.dockable.components.actions.rectify.lib;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.JLabel;

import org.weasis.acquire.dockable.components.actions.rectify.RectifyPanel;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.acquire.operations.impl.RectifyOrientationChangeListener;

public class OrientationSliderComponent extends AbstractSliderComponent {
    private static final long serialVersionUID = -4238024766089795426L;

    private static final int RECTIFY_ORIENTATION_MIN = -5;
    private static final int RECTIFY_ORIENTATION_MAX = 5;
    private static final int RECTIFY_ORIENTATION_DEFAULT = 0;
    
    private static final Hashtable<Integer, JLabel> labels = new Hashtable<>();
    
    static {
        labels.put(RECTIFY_ORIENTATION_MIN, new JLabel(Integer.toString(RECTIFY_ORIENTATION_MIN)));
        labels.put(RECTIFY_ORIENTATION_DEFAULT, new JLabel(Integer.toString(RECTIFY_ORIENTATION_DEFAULT)));
        labels.put(RECTIFY_ORIENTATION_MAX, new JLabel(Integer.toString(RECTIFY_ORIENTATION_MAX)));
    }
    
    public OrientationSliderComponent(RectifyPanel panel) {
        super(panel, "Orientation");
        addChangeListener(new RectifyOrientationChangeListener());
    }

    @Override
    public int getDefaultValue() {
        return RECTIFY_ORIENTATION_DEFAULT;
    }

    @Override
    public int getMin() {
        return RECTIFY_ORIENTATION_MIN;
    }

    @Override
    public int getMax() {
        return RECTIFY_ORIENTATION_MAX;
    }

    @Override
    public Dictionary<Integer, JLabel> getLabels() {
        return labels;
    }
    
    @Override
    public String getDisplayTitle() {
        return super.getDisplayTitle() + "°";
    }
}
