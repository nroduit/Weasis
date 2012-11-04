package org.weasis.dicom.viewer2d.mpr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JProgressBar;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.PolygonGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.Tools;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.geometry.LocalizerPoster;
import org.weasis.dicom.viewer2d.View2d;

public class MprView extends View2d {
    public enum Type {
        AXIAL, CORONAL, SAGITTAL
    };

    private Type type;
    private JProgressBar progressBar;

    public MprView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        this.type = Type.AXIAL;
        infoLayer.setDisplayPreferencesValue(AnnotationsLayer.PRELOADING_BAR, false);
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        // Get the radiologist way to see stack (means in axial, the first image is from feet and last image is in the
        // head direction)
        // TODO This option should be fixed
        actionsInView.put(ActionW.SORTSTACK.cmd(), SortSeriesStack.slicePosition);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type == null ? Type.AXIAL : type;
    }

    @Override
    protected void setImage(DicomImageElement img, boolean bestFit) {
        super.setImage(img, bestFit);
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
                                    ((MprView) v).setImage(img, true);
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
        AbstractLayer layer = getLayerModel().getLayer(Tools.CROSSLINES.getId());
        if (image != null && layer != null) {
            layer.deleteAllGraphic();
            GeometryOfSlice sliceGeometry = image.getSliceGeometry();
            if (sliceGeometry != null) {
                Point2D p = sliceGeometry.getImagePosition(p3);
                Tuple3d dimensions = sliceGeometry.getDimensions();

                List<Point2D> pts = new ArrayList<Point2D>();
                pts.add(new Point2D.Double(p.getX(), 0.0));
                pts.add(new Point2D.Double(p.getX(), dimensions.x));

                Color color1 = Type.SAGITTAL.equals(this.getType()) ? Color.GREEN : Color.BLUE;
                addMPRline(layer, pts, color1);

                List<Point2D> pts2 = new ArrayList<Point2D>();
                boolean axial = Type.AXIAL.equals(this.getType());
                Color color2 = axial ? Color.GREEN : Color.RED;
                double y = axial ? p.getY() - p.getX() : p.getY();
                pts2.add(new Point2D.Double(0.0, y));
                pts2.add(new Point2D.Double(dimensions.y, y));
                addMPRline(layer, pts2, color2);
            }
        }
    }

    private void addMPRline(AbstractLayer layer, List<Point2D> pts, Color color) {
        if (pts != null && pts.size() > 0 && layer != null) {
            if (pts.size() == 2) {

                // TODO cut the central part of the line
                // Point2D pt1 = pts.get(0);
                // Point2D pt2 = pts.get(1);
                // if (pt1.getY() - pt2.getY() < 0.0001) {
                // double l1b = p.getX() - 50.0;
                // double l2a = p.getX() + 50.0;
                // if (pt1.getX() > pt2.getX()) {
                // Point2D tmp = pt2;
                // pt2 = pt1;
                // pt1 = tmp;
                // }
                // if (pt1.getX() < l1b) {
                // pts.set(0, new Point2D.Double(pt1.getX(), pt1.getY()));
                // pts.set(1, new Point2D.Double(l1b, pt1.getY()));
                //
                // if (l2a < pt2.getX()) {
                // List<Point2D> pts2 = new ArrayList<Point2D>(2);
                // pts2.add(new Point2D.Double(l2a, pt2.getY()));
                // pts2.add(new Point2D.Double(pt2.getX(), pt2.getY()));
                // LineGraphic line2 = new LineGraphic(pts2, 1.0f, color, false);
                // layer.addGraphic(line2);
                // }
                // } else {
                //
                // }
                // }

                LineGraphic line1 = new LineGraphic(pts, 1.0f, color, false);
                layer.addGraphic(line1);
            } else {
                PolygonGraphic graphic = new PolygonGraphic(pts, color, 1.0f, false, false);
                layer.addGraphic(graphic);
            }
        }
    }

    @Override
    protected void addCrossline(DicomImageElement selImage, LocalizerPoster localizer, boolean fill) {
        // Do not display crosslines
    }

    @Override
    protected MouseActionAdapter getMouseAdapter(String action) {
        if (action.equals(ActionW.CROSSHAIR.cmd())) {
            return getAction(ActionW.CROSSHAIR);
        } else {
            return super.getMouseAdapter(action);
        }
    }

    @Override
    protected void drawExtendedAtions(Graphics2D g2d) {
        super.drawExtendedAtions(g2d);
        // To avoid concurrency issue
        final JProgressBar bar = progressBar;
        if (bar != null && bar.isVisible()) {
            // Draw in the bottom right corner of thumbnail space;
            int shiftx = getWidth() / 2 - bar.getWidth() / 2;
            int shifty = getHeight() / 2 - bar.getHeight() / 2;
            g2d.translate(shiftx, shifty);
            bar.paint(g2d);
            g2d.translate(-shiftx, -shifty);
        }
    }

    public void setProgressBar(JProgressBar bar) {
        this.progressBar = bar;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

}
