package org.weasis.acquire.dockable.components.util;

import java.util.Dictionary;
import java.util.Optional;
import java.util.StringJoiner;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;

public abstract class AbstractSliderComponent extends AbstractComponent {
    private static final long serialVersionUID = -1311547844550893305L;
    
    protected JSlider slider;
    
    public AbstractSliderComponent(AbstractAcquireActionPanel panel, String title) {
        super(panel, title);
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        slider = new JSlider(getMin(), getMax(), getDefaultValue());
        slider.setMajorTickSpacing(getMax());
        slider.setPaintTicks(true);
        slider.setLabelTable(getLabels());
        slider.setPaintLabels(true);
        FontTools.setFont10(slider);
        slider.setBorder(borderTitle); 
        
        add(slider);
    }
    
    @Override
    public String getDisplayTitle() {
        return new StringJoiner(StringUtil.COLON_AND_SPACE).add(title).add(Integer.toString(getSliderValue())).toString();
    }
    
    public int getSliderValue() {
        return Optional.ofNullable(slider).map(s -> s.getValue()).orElse(getDefaultValue());
    }
    
    public void setSliderValue(int value) {
        slider.setValue(value);
    }
    
    public void addChangeListener(ChangeListener listener) {
        slider.addChangeListener(listener);
    }
    
    public void removeChangeListener(ChangeListener listener) {
        slider.removeChangeListener(listener);
    }
    

    public abstract int getDefaultValue();
    
    public abstract int getMin();
    
    public abstract int getMax();
    
    public abstract Dictionary<Integer, JLabel> getLabels();
    
}
