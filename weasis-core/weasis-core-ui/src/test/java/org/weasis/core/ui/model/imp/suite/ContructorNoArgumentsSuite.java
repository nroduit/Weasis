package org.weasis.core.ui.model.imp.suite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.Test;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;
import org.weasis.core.ui.test.utils.ModelListHelper;

public class ContructorNoArgumentsSuite extends ModelListHelper {

    @Test
    public void testXmlModelList() throws Exception {
        GraphicModel actual = new XmlGraphicModel();

        assertThat(actual).isNotNull();
        assertThat(actual).isInstanceOfAny(DefaultUUID.class, GraphicModel.class, AbstractGraphicModel.class,
            XmlGraphicModel.class);

        assertThat(actual.getReferencedSeries()).isEmpty();
        assertThat(actual.getLayers()).isEmpty();
        assertThat(actual.getModels()).isEmpty();
        assertThat(actual.getAllGraphics()).isEmpty();
        assertThat(actual.groupLayerByType()).isEmpty();
        assertThat(actual.getSelectedDragableGraphics()).isEmpty();
        assertThat(actual.getSelectedGraphics()).isEmpty();
        assertThat(actual.getGraphicSelectionListeners()).isEmpty();

        assertThat(actual.getSelectGraphic()).isEqualTo(Optional.empty());

        assertThat(actual.getLayerCount()).isEqualTo(0);
    }
}
