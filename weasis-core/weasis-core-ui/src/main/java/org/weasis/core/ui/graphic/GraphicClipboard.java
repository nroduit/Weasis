package org.weasis.core.ui.graphic;

import java.util.List;

public class GraphicClipboard {
    private List<Graphic> graphics;

    public synchronized List<Graphic> getGraphics() {
        return graphics;
    }

    public synchronized void setGraphics(List<Graphic> graphics) {
        this.graphics = graphics;
    }

}
