package org.weasis.core.ui.model.graphic;

import java.util.List;

import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.MeasureItem;

public interface GraphicArea extends Graphic, ImageStatistics {

    List<MeasureItem> getImageStatistics(MeasurableLayer layer, Boolean releaseEvent);

}
