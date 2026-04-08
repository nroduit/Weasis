package org.weasis.core.ui.editor.image;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;

public class DefaultSynchManager<E extends ImageElement> extends SynchManager<E> {

  public DefaultSynchManager(ImageViewerEventManager<E> eventManager) {
      super(eventManager);
  }

    @Override
    public boolean hasSameOrientation(MediaSeries series1, MediaSeries series2) {
        return true;
    }
}
