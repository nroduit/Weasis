package org.weasis.core.ui.model.graphic.imp.line;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class LineGraphicTest extends GraphicTester<LineGraphic> {
    private static final String XML_0 = "/graphic/line/line.graphic.0.xml"; //$NON-NLS-1$
    private static final String XML_1 = "/graphic/line/line.graphic.1.xml"; //$NON-NLS-1$
    
    public static final String BASIC_TPL = 
        "<line fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" //$NON-NLS-1$
      +     "<paint rgb=\"%s\"/>" //$NON-NLS-1$
      +     "<pts/>" //$NON-NLS-1$
      + "</line>"; //$NON-NLS-1$
    
    public static final LineGraphic COMPLETE_OBJECT =  new LineGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        COMPLETE_OBJECT.setColorPaint(Color.BLACK);
        
        List<Point2D.Double> pts = Arrays.asList(
            new Point2D.Double(1028.5, 1110.0),
            new Point2D.Double(1231.5, 1285.0)
        );
        COMPLETE_OBJECT.setPts(pts); 
    }
    
    @Override
    public String getTemplate() {
        return BASIC_TPL;
    }

    @Override
    public Object[] getParameters() {
        return new Object[] { 
            LineGraphic.DEFAULT_FILLED, 
            LineGraphic.DEFAULT_LABEL_VISISIBLE,
            LineGraphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(),
            WProperties.color2Hexadecimal(LineGraphic.DEFAULT_COLOR, true) 
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
    public LineGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
