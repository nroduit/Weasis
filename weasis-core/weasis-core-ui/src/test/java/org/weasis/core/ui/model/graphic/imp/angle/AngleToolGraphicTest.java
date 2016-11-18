package org.weasis.core.ui.model.graphic.imp.angle;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class AngleToolGraphicTest extends GraphicTester<AngleToolGraphic> {
    private static final String XML_0 = "/graphic/angle/angle.graphic.0.xml"; //$NON-NLS-1$
    private static final String XML_1 = "/graphic/angle/angle.graphic.1.xml"; //$NON-NLS-1$
    
    public static final String BASIC_TPL = 
        "<angle fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" //$NON-NLS-1$
      +     "<paint rgb=\"%s\"/>" //$NON-NLS-1$
      +     "<pts/>" //$NON-NLS-1$
      + "</angle>"; //$NON-NLS-1$
    
    public static final AngleToolGraphic COMPLETE_OBJECT =  new AngleToolGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        
        List<Point2D.Double> pts = Arrays.asList(
            new Point2D.Double(1345.5, 815.0),
            new Point2D.Double(1313.5, 954.0),
            new Point2D.Double(1423.5, 924.0)
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
            AngleToolGraphic.DEFAULT_FILLED, 
            AngleToolGraphic.DEFAULT_LABEL_VISISIBLE,
            AngleToolGraphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(), 
            WProperties.color2Hexadecimal(AngleToolGraphic.DEFAULT_COLOR, true) 
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
    public AngleToolGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
