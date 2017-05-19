package org.weasis.core.ui.model.graphic.imp;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class PointGraphicTest extends GraphicTester<PointGraphic> {
    private static final String XML_0 = "/graphic/point/point.graphic.0.xml"; //$NON-NLS-1$
    private static final String XML_1 = "/graphic/point/point.graphic.1.xml"; //$NON-NLS-1$
    
    static final String BASIC_TPL = 
        "<point pointSize=\"%s\" fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" //$NON-NLS-1$
      +     "<paint rgb=\"%s\"/>" //$NON-NLS-1$
      +     "<pts/>" //$NON-NLS-1$
      + "</point>"; //$NON-NLS-1$
    
    
    public static final PointGraphic COMPLETE_OBJECT =  new PointGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        COMPLETE_OBJECT.setLineThickness(3.0f);
        COMPLETE_OBJECT.setColorPaint(Color.RED);
        
        List<Point2D.Double> pts = Arrays.asList(new Point2D.Double(1665.5, 987.0));
        COMPLETE_OBJECT.setPts(pts);
    }
    
    @Override
    public String getTemplate() {
        return BASIC_TPL;
    }
    
    @Override
    public Object[] getParameters() {
        return new Object[]{
            PointGraphic.DEFAULT_POINT_SIZE,
            PointGraphic.DEFAULT_FILLED, 
            PointGraphic.DEFAULT_LABEL_VISISIBLE,
            PointGraphic.DEFAULT_LINE_THICKNESS, 
            getGraphicUuid(), 
            WProperties.color2Hexadecimal(PointGraphic.DEFAULT_COLOR, true) 
        };
    }

    @Override
    public void additionalTestsForDeserializeBasicGraphic(PointGraphic result, PointGraphic expected) {
        assertThat(result.getPointSize()).isEqualTo(PointGraphic.DEFAULT_POINT_SIZE);
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
    public PointGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
    
    @Override
    public void additionalTestsForDeserializeCompleteGraphic(PointGraphic result, PointGraphic expected) {
        assertThat(result.getPointSize()).isEqualTo(expected.getPointSize());
    }
}
