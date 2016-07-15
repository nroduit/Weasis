package org.weasis.core.ui.model.graphic.imp.angle;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class FourPointsAngleToolGraphicTest extends GraphicTester<FourPointsAngleToolGraphic> {
    private static final String XML_0 = "/graphic/fourPointsAngle/fourPointsAngle.graphic.0.xml";
    private static final String XML_1 = "/graphic/fourPointsAngle/fourPointsAngle.graphic.1.xml";
    
    public static final String BASIC_TPL = 
        "<fourPointsAngle fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">"
      +     "<paint rgb=\"%s\"/>"
      +     "<pts/>"
      + "</fourPointsAngle>";
    
    public static final FourPointsAngleToolGraphic COMPLETE_OBJECT =  new FourPointsAngleToolGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        
        List<Point2D.Double> pts = Arrays.asList(
            new Point2D.Double(1294.5, 1528.0),
            new Point2D.Double(1456.5, 1498.0),
            new Point2D.Double(1181.5, 1553.0),
            new Point2D.Double(1430.5, 1638.0),
            new Point2D.Double(1438.5, 1525.0),
            new Point2D.Double(1367.5, 1717.0),
            new Point2D.Double(1498.5, 1556.0),
            new Point2D.Double(1540.5, 1643.0)
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
            FourPointsAngleToolGraphic.DEFAULT_FILLED,
            FourPointsAngleToolGraphic.DEFAULT_LABEL_VISISIBLE, 
            FourPointsAngleToolGraphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(), 
            WProperties.color2Hexadecimal(FourPointsAngleToolGraphic.DEFAULT_COLOR, true) 
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
    public FourPointsAngleToolGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
