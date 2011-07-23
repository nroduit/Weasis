package org.weasis.core.ui.graphic.model;

import java.util.List;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.graphic.Graphic;

public interface GraphicsListener {

    void handle(List<Graphic> selectedGraphics, ImageElement img);
}
