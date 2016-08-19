package org.weasis.acquire.dockable.components.actions.contrast.comp;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.StringJoiner;

import javax.swing.JLabel;

import org.weasis.acquire.dockable.components.actions.contrast.ContrastPanel;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.core.api.util.StringUtil;

public class ContrastComponent extends AbstractSliderComponent {
    private static final long serialVersionUID = -8952577162679680694L;
    
    public static final int CONTRAST_VALUE = 100;
    public static final int CONTRAST_MIN = 1;
    public static final int CONTRAST_MAX = 200;
    
    private static final Hashtable<Integer, JLabel> labels = new Hashtable<>();
    
    static {
        labels.put(CONTRAST_MIN, new JLabel("0.01"));
        labels.put(CONTRAST_VALUE, new JLabel("1"));
        labels.put(CONTRAST_MAX, new JLabel("2"));
    }
    
    public ContrastComponent(ContrastPanel panel) {
        super(panel, "Contrasts");
        addChangeListener(panel);
    }

    @Override
    public String getDisplayTitle() {
        return new StringJoiner(StringUtil.COLON).add(title).add(Float.toString(getSliderValue() / 100f)).toString();
    }
    
    @Override
    public int getDefaultValue() {
        return CONTRAST_VALUE;
    }

    @Override
    public int getMin() {
        return CONTRAST_MIN;
    }

    @Override
    public int getMax() {
        return CONTRAST_MAX;
    }

    @Override
    public Dictionary<Integer, JLabel> getLabels() {
        return labels;
    }
    
}
