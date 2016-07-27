package org.weasis.core.ui.model.graphic.imp.angle;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class OpenAngleToolGraphicTest extends GraphicTester<OpenAngleToolGraphic> {
    private static final String XML_0 = "/graphic/openAngle/openAngle.graphic.0.xml";
    private static final String XML_1 = "/graphic/openAngle/openAngle.graphic.1.xml";
    
    public static final String BASIC_TPL = 
        "<openAngle fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">"
     +     "<paint rgb=\"%s\"/>"
     +     "<pts/>"
     + "</openAngle>";
    
    public static final OpenAngleToolGraphic COMPLETE_OBJECT =  new OpenAngleToolGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        
        List<Point2D.Double> pts = Arrays.asList(
            new Point2D.Double(1961.5, 1514.0),
            new Point2D.Double(2014.5, 1476.0),
            new Point2D.Double(1967.5, 1569.0),
            new Point2D.Double(2131.5, 1549.0)
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
            OpenAngleToolGraphic.DEFAULT_FILLED,
            OpenAngleToolGraphic.DEFAULT_LABEL_VISISIBLE, 
            OpenAngleToolGraphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(),
            WProperties.color2Hexadecimal(OpenAngleToolGraphic.DEFAULT_COLOR, true) 
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
    public OpenAngleToolGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
