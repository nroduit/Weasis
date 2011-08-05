package org.weasis.core.ui.graphic;

public class Measurement {

    private final String name;
    private final int id;
    private final boolean quickComputing;
    private boolean computed;
    private boolean graphicLabel;
    private boolean defaultGraphicLabel;

    public Measurement(String name, int id, boolean quickComputing) {
        this(name, id, quickComputing, true, true);
    }

    public Measurement(String name, int id, boolean quickComputing, boolean computed, boolean graphicLabel) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null!");
        }
        this.name = name;
        this.id = id;
        this.quickComputing = quickComputing;
        this.computed = computed;
        this.graphicLabel = graphicLabel;
        this.defaultGraphicLabel = graphicLabel;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public boolean isComputed() {
        return computed;
    }

    public void setComputed(boolean computed) {
        this.computed = computed;
    }

    public boolean isGraphicLabel() {
        return graphicLabel;
    }

    public void setGraphicLabel(boolean graphicLabel) {
        this.graphicLabel = graphicLabel;
    }

    public void resetToGraphicLabelValue() {
        graphicLabel = defaultGraphicLabel;
    }

    public boolean isQuickComputing() {
        return quickComputing;
    }

}
