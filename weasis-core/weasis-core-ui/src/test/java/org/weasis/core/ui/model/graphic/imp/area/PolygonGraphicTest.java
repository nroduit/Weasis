package org.weasis.core.ui.model.graphic.imp.area;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class PolygonGraphicTest extends GraphicTester<PolygonGraphic> {
    private static final String XML_0 = "/graphic/polygon/polygon.graphic.0.xml"; //$NON-NLS-1$
    private static final String XML_1 = "/graphic/polygon/polygon.graphic.1.xml"; //$NON-NLS-1$
    
    public static final String BASIC_TPL =
         "<polygon fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" //$NON-NLS-1$
       +     "<paint rgb=\"%s\"/>" //$NON-NLS-1$
       +     "<pts/>" //$NON-NLS-1$
       + "</polygon>"; //$NON-NLS-1$
    
    public static final PolygonGraphic COMPLETE_OBJECT =  new PolygonGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        COMPLETE_OBJECT.setFilled(Boolean.TRUE);
        COMPLETE_OBJECT.setLineThickness(2.0f);
        
        List<Point2D.Double> pts = Arrays.asList(
            new Point2D.Double(1132.5, 1100.0),
            new Point2D.Double(1300.5, 1033.0),
            new Point2D.Double(1345.5, 1089.0),
            new Point2D.Double(1329.5, 1296.0),
            new Point2D.Double(1034.5, 1292.0),
            new Point2D.Double(1249.5, 1469.0)
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
            PolygonGraphic.DEFAULT_FILLED, 
            PolygonGraphic.DEFAULT_LABEL_VISISIBLE,
            PolygonGraphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(), 
            WProperties.color2Hexadecimal(PolygonGraphic.DEFAULT_COLOR, true) 
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
    public PolygonGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
