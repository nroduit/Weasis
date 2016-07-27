package org.weasis.core.ui.model.graphic.imp.area;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.test.testers.GraphicTester;

public class SelectGraphicTest extends GraphicTester<SelectGraphic> {
    private static final String XML_0 = "/graphic/select/select.graphic.0.xml";
    private static final String XML_1 = "/graphic/select/select.graphic.1.xml";
    
    public static final String BASIC_TPL =
          "<selectGraphic fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">"
        +     "<paint rgb=\"%s\"/>"
        +     "<pts/>"
        + "</selectGraphic>";
    
    public static final SelectGraphic COMPLETE_OBJECT =  new SelectGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        
        List<Point2D.Double> pts = Arrays.asList(
            new Point2D.Double(1440.5, 1161.0),
            new Point2D.Double(1769.5, 1328.0),
            new Point2D.Double(1769.5, 1161.0),
            new Point2D.Double(1440.5, 1328.0),
            new Point2D.Double(1605.0, 1161.0),
            new Point2D.Double(1605.0, 1328.0),
            new Point2D.Double(1769.5, 1244.5),
            new Point2D.Double(1440.5, 1244.5)
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
            SelectGraphic.DEFAULT_FILLED,
            SelectGraphic.DEFAULT_LABEL_VISISIBLE, 
            SelectGraphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(), 
            WProperties.color2Hexadecimal(Color.WHITE, true) 
        };
    }

    @Override
    public void additionalTestsForDeserializeBasicGraphic(SelectGraphic result, SelectGraphic expected) {
        assertThat(result.isGraphicComplete()).isFalse();
    }
    
    @Override
    protected void checkDefaultValues(Graphic result) {
        assertThat(result.getSelected()).isTrue();
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
    public SelectGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
