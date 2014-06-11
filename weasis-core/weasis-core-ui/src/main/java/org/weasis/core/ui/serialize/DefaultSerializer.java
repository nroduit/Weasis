package org.weasis.core.ui.serialize;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.simpleframework.xml.convert.Registry;
import org.simpleframework.xml.convert.RegistryStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.graphic.model.GraphicList;

public class DefaultSerializer {
    private static final DefaultSerializer instance = new DefaultSerializer();

    private final Persister serializer;
    private final Map<String, Class<?>> classMap;

    private DefaultSerializer() {
        this.classMap = new HashMap<String, Class<?>>();
        classMap.put("angle", org.weasis.core.ui.graphic.AngleToolGraphic.class); //$NON-NLS-1$
        classMap.put("annotation", org.weasis.core.ui.graphic.AnnotationGraphic.class); //$NON-NLS-1$
        classMap.put("pixelInfo", org.weasis.core.ui.graphic.PixelInfoGraphic.class); //$NON-NLS-1$
        classMap.put("cobbAngle", org.weasis.core.ui.graphic.CobbAngleToolGraphic.class); //$NON-NLS-1$
        classMap.put("openAngle", org.weasis.core.ui.graphic.OpenAngleToolGraphic.class); //$NON-NLS-1$
        classMap.put("ellipse", org.weasis.core.ui.graphic.EllipseGraphic.class); //$NON-NLS-1$
        classMap.put("fourPointsAngle", org.weasis.core.ui.graphic.FourPointsAngleToolGraphic.class); //$NON-NLS-1$
        classMap.put("line", org.weasis.core.ui.graphic.LineGraphic.class); //$NON-NLS-1$
        classMap.put("perpendicularLine", org.weasis.core.ui.graphic.PerpendicularLineGraphic.class); //$NON-NLS-1$
        classMap.put("lineWithGap", org.weasis.core.ui.graphic.LineWithGapGraphic.class); //$NON-NLS-1$
        classMap.put("ParallelLine", org.weasis.core.ui.graphic.ParallelLineGraphic.class); //$NON-NLS-1$
        classMap.put("point", org.weasis.core.ui.graphic.PointGraphic.class); //$NON-NLS-1$
        classMap.put("polygon", org.weasis.core.ui.graphic.PolygonGraphic.class); //$NON-NLS-1$
        classMap.put("polyline", org.weasis.core.ui.graphic.PolylineGraphic.class); //$NON-NLS-1$
        classMap.put("rectangle", org.weasis.core.ui.graphic.RectangleGraphic.class); //$NON-NLS-1$
        classMap.put("threePointsCircle", org.weasis.core.ui.graphic.ThreePointsCircleGraphic.class); //$NON-NLS-1$

        Registry registry = new Registry();
        Strategy strategy = new RegistryStrategy(registry);
        this.serializer = new Persister(strategy);

        try {
            registry.bind(Color.class, ColorConverter.class);
            registry.bind(Point2D.Double.class, Point2DConverter.class);
            registry.bind(GraphicList.class, new GraphicListConverter());

        } catch (Exception e1) {
            e1.printStackTrace();
        }

    }

    public static final DefaultSerializer getInstance() {
        return instance;
    }

    public Persister getSerializer() {
        return serializer;
    }

    public Map<String, Class<?>> getClassMap() {
        return classMap;
    }

    public static void readMeasurementGraphics(ImageElement img, File destinationFile) {
        File gpxFile = new File(destinationFile.getPath() + ".xml"); //$NON-NLS-1$

        if (gpxFile.canRead()) {
            try {
                GraphicList list = DefaultSerializer.getInstance().getSerializer().read(GraphicList.class, gpxFile);
                img.setTag(TagW.MeasurementGraphics, list);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void writeMeasurementGraphics(ImageElement img, File destinationFile) {
        GraphicList list = (GraphicList) img.getTagValue(TagW.MeasurementGraphics);
        if (list != null && list.list.size() > 0) {
            File gpxFile = new File(destinationFile.getParent(), destinationFile.getName() + ".xml"); //$NON-NLS-1$
            try {
                DefaultSerializer.getInstance().getSerializer().write(list, gpxFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
