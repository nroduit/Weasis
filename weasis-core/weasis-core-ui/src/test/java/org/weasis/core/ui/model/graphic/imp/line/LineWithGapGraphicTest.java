package org.weasis.core.ui.model.graphic.imp.line;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.test.testers.GraphicTester;

public class LineWithGapGraphicTest extends GraphicTester<LineWithGapGraphic> {
    private static final String XML_0 = "/graphic/lineWithGap/lineWithGap.graphic.0.xml";
    private static final String XML_1 = "/graphic/lineWithGap/lineWithGap.graphic.1.xml";
    
    static final String BASIC_TPL = 
        "<lineWithGap gapSize=\"%s\" fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">"
      +     "<paint rgb=\"%s\"/>"
      +     "<pts/>"
      + "</lineWithGap>";
    
    public static final LineWithGapGraphic COMPLETE_OBJECT =  new LineWithGapGraphic();
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
        return new Object[]{ 
            LineWithGapGraphic.DEFAULT_GAP_SIZE,
            LineWithGapGraphic.DEFAULT_FILLED,
            LineWithGapGraphic.DEFAULT_LABEL_VISISIBLE, 
            LineWithGapGraphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(),
            WProperties.color2Hexadecimal(LineWithGapGraphic.DEFAULT_COLOR, true) 
        };
    }

    @Override
    public void additionalTestsForDeserializeBasicGraphic(LineWithGapGraphic result, LineWithGapGraphic expected) {
        assertThat(result.getGapSize()).isEqualTo(LineWithGapGraphic.DEFAULT_GAP_SIZE);
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
    public LineWithGapGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
