package org.weasis.core.ui.model.graphic.imp.area;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class ThreePointsCircleGraphicTest extends GraphicTester<ThreePointsCircleGraphic> {
    private static final String XML_0 = "/graphic/threePointsCircle/threePointsCircle.graphic.0.xml";
    private static final String XML_1 = "/graphic/threePointsCircle/threePointsCircle.graphic.1.xml";
    
    static final String BASIC_TPL =
        "<threePointsCircle fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">"
       +     "<paint rgb=\"%s\"/>"
       +     "<pts/>"
       + "</threePointsCircle>";
    
    public static final ThreePointsCircleGraphic COMPLETE_OBJECT =  new ThreePointsCircleGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        COMPLETE_OBJECT.setFilled(Boolean.TRUE);
        
        List<Point2D.Double> pts = Arrays.asList(
            new Point2D.Double(1293.5, 1023.0),
            new Point2D.Double(1461.5, 1156.0),
            new Point2D.Double(1494.5, 1090.0)
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
            ThreePointsCircleGraphic.DEFAULT_FILLED,
            ThreePointsCircleGraphic.DEFAULT_LABEL_VISISIBLE, 
            ThreePointsCircleGraphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(), 
            WProperties.color2Hexadecimal(ThreePointsCircleGraphic.DEFAULT_COLOR, true) 
        };
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
    public ThreePointsCircleGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
