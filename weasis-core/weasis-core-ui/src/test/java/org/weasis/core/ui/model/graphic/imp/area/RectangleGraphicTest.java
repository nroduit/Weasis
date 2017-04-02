package org.weasis.core.ui.model.graphic.imp.area;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class RectangleGraphicTest extends GraphicTester<RectangleGraphic> {
    private static final String XML_0 = "/graphic/rectangle/rectangle.graphic.0.xml"; //$NON-NLS-1$
    private static final String XML_1 = "/graphic/rectangle/rectangle.graphic.1.xml"; //$NON-NLS-1$
    
    public static final String BASIC_TPL = 
            "<rectangle fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" //$NON-NLS-1$
         +     "<paint rgb=\"%s\"/>" //$NON-NLS-1$
         +     "<pts/>" //$NON-NLS-1$
         + "</rectangle>"; //$NON-NLS-1$
    
    public static final RectangleGraphic COMPLETE_OBJECT =  new RectangleGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        COMPLETE_OBJECT.setFilled(Boolean.TRUE);
        
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
            RectangleGraphic.DEFAULT_FILLED, 
            RectangleGraphic.DEFAULT_LABEL_VISISIBLE,
            RectangleGraphic.DEFAULT_LINE_THICKNESS, 
            getGraphicUuid(), 
            WProperties.color2Hexadecimal(RectangleGraphic.DEFAULT_COLOR, true) 
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
    public RectangleGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
