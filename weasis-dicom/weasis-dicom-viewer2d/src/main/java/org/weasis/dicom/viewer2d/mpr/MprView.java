package org.weasis.dicom.viewer2d.mpr;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JProgressBar;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.InvalidShapeException;
import org.weasis.core.ui.graphic.LineWithGapGraphic;
import org.weasis.core.ui.graphic.PolygonGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.View2d;

public class MprView extends View2d {
    private static final Logger LOGGER = LoggerFactory.getLogger(MprView.class);

    public enum SliceOrientation {
        AXIAL, CORONAL, SAGITTAL
    };

    private SliceOrientation sliceOrientation;
    private JProgressBar progressBar;

    public MprView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        this.sliceOrientation = SliceOrientation.AXIAL;
        infoLayer.setDisplayPreferencesValue(AnnotationsLayer.PRELOADING_BAR, false);
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(DefaultView2d.zoomTypeCmd, ZoomType.BEST_FIT);
        // Get the radiologist way to see stack (means in axial, the first image is from feet and last image is in the
        // head direction)
        // TODO This option should be fixed
        actionsInView.put(ActionW.SORTSTACK.cmd(), SortSeriesStack.slicePosition);
        // Propagate the preset
        actionsInView.put(ActionW.DEFAULT_PRESET.cmd(), false);
    }

    public SliceOrientation getSliceOrientation() {
        return sliceOrientation;
    }

    public void setType(SliceOrientation sliceOrientation) {
        this.sliceOrientation = sliceOrientation == null ? SliceOrientation.AXIAL : sliceOrientation;
    }

    @Override
    protected void setImage(DicomImageElement img) {
        super.setImage(img);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (series == null) {
            return;
        }
        final String command = evt.getPropertyName();
        if (command.equals(ActionW.CROSSHAIR.cmd())) {
            Object point = evt.getNewValue();
            if (point instanceof Point2D) {

                Point2D p = (Point2D) point;
                Point3d p3 = this.getImage().getSliceGeometry().getPosition(p);
                // double [] xxx = (double [])this.getImage().getTagValue(TagW.SlicePosition);
                // System.out.println(p3.x+","+p3.y+","+p3.z+","+xxx[0]+","+xxx[1]+","+xxx[2]);
                ImageViewerPlugin<DicomImageElement> container = this.eventManager.getSelectedView2dContainer();
                if (container instanceof MPRContainer) {
                    ArrayList<DefaultView2d<DicomImageElement>> viewpanels =
                        ((MPRContainer) container).getImagePanels();
                    for (DefaultView2d<DicomImageElement> v : viewpanels) {
                        if (v.getSeries() == null) {
                            continue;
                        }
                        DefaultView2d<DicomImageElement> selImg = container.getSelectedImagePane();
                        if (v instanceof MprView) {
                            if (v != selImg) {
                                Vector3d vn = v.getImage().getSliceGeometry().getNormal();
                                vn.absolute();
                                double location = p3.x * vn.x + p3.y * vn.y + p3.z * vn.z;
                                // if (MprView.SliceOrientation.SAGITTAL == ((MprView) v).type) {
                                // location = -location;
                                // }
                                DicomImageElement img =
                                    v.getSeries().getNearestImage(location, 0,
                                        (Filter<DicomImageElement>) actionsInView.get(ActionW.FILTERED_SERIES.cmd()),
                                        v.getCurrentSortComparator());
                                if (img != null) {
                                    PresetWindowLevel preset =
                                        (PresetWindowLevel) actionsInView.get(ActionW.PRESET.cmd());
                                    // When series synchronization, do not synch preset from other series
                                    v.setActionsInView(ActionW.PRESET.cmd(), img.containsPreset(preset) ? preset : null);
                                    v.setActionsInView(ActionW.WINDOW.cmd(), actionsInView.get(ActionW.WINDOW.cmd()));
                                    v.setActionsInView(ActionW.LEVEL.cmd(), actionsInView.get(ActionW.LEVEL.cmd()));
                                    ((MprView) v).setImage(img);
                                }
                            }
                        }
                    }
                    for (DefaultView2d<DicomImageElement> v : viewpanels) {
                        if (v instanceof MprView) {
                            ((MprView) v).computeCrosshair(p3);
                            v.repaint();
                        }
                    }
                }
            }
        }
    }

    private void computeCrosshair(Point3d p3) {
        DicomImageElement image = this.getImage();
        AbstractLayer layer = getLayerModel().getLayer(AbstractLayer.CROSSLINES);
        if (image != null && layer != null) {
            layer.deleteAllGraphic();
            GeometryOfSlice sliceGeometry = image.getSliceGeometry();
            if (sliceGeometry != null) {
                Point2D p = sliceGeometry.getImagePosition(p3);
                Tuple3d dimensions = sliceGeometry.getDimensions();
                boolean axial = SliceOrientation.AXIAL.equals(this.getSliceOrientation());
                double y = axial ? p.getY() - p.getX() : p.getY();
                Point2D centerPt = new Point2D.Double(p.getX(), y);

                List<Point2D.Double> pts = new ArrayList<Point2D.Double>();
                pts.add(new Point2D.Double(p.getX(), 0.0));
                pts.add(new Point2D.Double(p.getX(), dimensions.x));

                Color color1 = SliceOrientation.SAGITTAL.equals(this.getSliceOrientation()) ? Color.GREEN : Color.BLUE;
                addMPRline(layer, pts, color1, centerPt);

                List<Point2D.Double> pts2 = new ArrayList<Point2D.Double>();
                Color color2 = axial ? Color.GREEN : Color.RED;
                pts2.add(new Point2D.Double(0.0, y));
                pts2.add(new Point2D.Double(dimensions.y, y));
                addMPRline(layer, pts2, color2, centerPt);
            }
        }
    }

    private void addMPRline(AbstractLayer layer, List<Point2D.Double> pts, Color color, Point2D center) {
        if (pts != null && pts.size() > 0 && layer != null) {
            try {
                Graphic graphic =
                    pts.size() == 2 ? new LineWithGapGraphic(pts.get(0), pts.get(1), 1.0f, color, false, center, 75)
                        : new PolygonGraphic(pts, color, 1.0f, false, false);
                layer.addGraphic(graphic);
            } catch (InvalidShapeException e) {
                LOGGER.error(e.getMessage());
            }

        }
    }

    @Override
    protected MouseActionAdapter getMouseAdapter(String action) {
        if (action.equals(ActionW.CROSSHAIR.cmd())) {
            return getAction(ActionW.CROSSHAIR);
        } else {
            return super.getMouseAdapter(action);
        }
    }

    // TODO repaint progress bar ?

    // @Override
    // protected Rectangle drawExtendedActions(Graphics2D g2d) {
    // Rectangle rect = super.drawExtendedActions(g2d);
    // // To avoid concurrency issue
    // final JProgressBar bar = progressBar;
    // if (bar != null && bar.isVisible()) {
    // int shiftx = getWidth() / 2 - bar.getWidth() / 2;
    // int shifty = getHeight() / 2 - bar.getHeight() / 2;
    // g2d.translate(shiftx, shifty);
    // bar.paint(g2d);
    // g2d.translate(-shiftx, -shifty);
    // }
    // return rect;
    // }

    public void setProgressBar(JProgressBar bar) {
        this.progressBar = bar;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

}
