package org.weasis.core.ui.graphic;

public class Measurement {
    // private final int id;
    private final String name;
    private final boolean quickComputing;
    private boolean computed;
    private boolean graphicLabel;

    public Measurement(String name, boolean quickComputing) {
        this(name, quickComputing, true, true);
    }

    public Measurement(String name, boolean quickComputing, boolean computed, boolean graphicLabel) {
        if (name == null)
            throw new IllegalArgumentException("Name cannot be null!");
        this.name = name;
        this.quickComputing = quickComputing;
        this.computed = computed;
        this.graphicLabel = graphicLabel;
    }

    public String getName() {
        return name;
    }

    public boolean isComputed() {
        return computed;
    }

    public void setComputed(boolean computed) {
        this.computed = computed;
        if (!computed && graphicLabel) {
            graphicLabel = false;
        }
    }

    public boolean isGraphicLabel() {
        return graphicLabel;
    }

    public void setGraphicLabel(boolean graphicLabel) {
        this.graphicLabel = graphicLabel;
        if (graphicLabel && !computed) {
            computed = true;
        }
    }

    public boolean isQuickComputing() {
        return quickComputing;
    }

}
