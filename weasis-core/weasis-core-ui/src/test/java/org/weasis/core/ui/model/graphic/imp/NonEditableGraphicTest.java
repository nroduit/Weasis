package org.weasis.core.ui.model.graphic.imp;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class NonEditableGraphicTest extends GraphicTester<NonEditableGraphic> {
    private static final String XML_0 = "/graphic/nonEditable/nonEditable.graphic.0.xml";
    private static final String XML_1 = "/graphic/nonEditable/nonEditable.graphic.1.xml";
    
    public static final String BASIC_TPL =
        "<nonEditable fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">"
        +   "<paint rgb=\"%s\"/>"
        +   "<pts/>"
        + "</nonEditable>";

    public static final NonEditableGraphic COMPLETE_OBJECT =  new NonEditableGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        List<Point2D.Double> pts = Arrays.asList(
            new Point2D.Double(1665.5, 987.0),
            new Point2D.Double(1601.5, 1037.0)
        );
        COMPLETE_OBJECT.setPts(pts);
    }
    
    @Override
    public String getTemplate() {
        return BASIC_TPL;
    }

    @Override
    public Object[] getParameters() {
        return new Object[]{
            NonEditableGraphic.DEFAULT_FILLED, 
            NonEditableGraphic.DEFAULT_LABEL_VISISIBLE,
            NonEditableGraphic.DEFAULT_LINE_THICKNESS, 
            getGraphicUuid(), 
            WProperties.color2Hexadecimal(NonEditableGraphic.DEFAULT_COLOR, true)
        };
    }

    @Override
    public void additionalTestsForDeserializeBasicGraphic(NonEditableGraphic result, NonEditableGraphic expected) {
        assertThat(result.getUIName()).isEmpty();
        assertThat(result.getDescription()).isEmpty();
    }

    @Override
    public String getXmlFilePathCase0() {
        return XML_0;
    }

    @Override
    public String getXmlFilePathCase1() {
        return XML_1;
    }

    @Override
    public NonEditableGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }

}
