package org.weasis.acquire.explorer;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.dcm4che3.data.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.core.api.image.AutoLevelsOp;
import org.weasis.core.api.image.BrightnessOp;
import org.weasis.core.api.image.CropOp;
import org.weasis.core.api.image.FlipOp;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.layer.Layer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-11 - ylar - Creation
 */
public class AcquireImageInfo {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(AcquireImageInfo.class);

    private final ImageElement image;
    private Serie serie = Serie.DEFAULT_SERIE;
    private final Attributes attributes;
    private Layer layer;
    private AcquireImageStatus status;

    private final SimpleOpManager preProcessOpManager;
    private final SimpleOpManager postProcessOpManager;

    private AcquireImageValues defaultValues;
    private AcquireImageValues currentValues;
    private AcquireImageValues nextValues;
    private final List<AcquireImageValues> steps;

    private String comment;

    public List<AcquireImageValues> getSteps() {
        return steps;
    }

    public AcquireImageInfo(ImageElement image) {
        if (image == null) {
            throw new IllegalArgumentException("image cannot be null");
        }
        this.setStatus(AcquireImageStatus.TO_PUBLISH);
        this.image = image;
        this.attributes = new Attributes();
        this.preProcessOpManager = new SimpleOpManager();
        this.postProcessOpManager = new SimpleOpManager();
        this.postProcessOpManager.addImageOperationAction(new CropOp());
        this.postProcessOpManager.addImageOperationAction(new RotationOp());
        this.postProcessOpManager.addImageOperationAction(new FlipOp());
        this.postProcessOpManager.addImageOperationAction(new BrightnessOp());
        this.postProcessOpManager.addImageOperationAction(new AutoLevelsOp());
        this.postProcessOpManager.addImageOperationAction(new ZoomOp());

        defaultValues = new AcquireImageValues();
        currentValues = (AcquireImageValues) defaultValues.clone();
        nextValues = (AcquireImageValues) defaultValues.clone();

        steps = new ArrayList<>();
        steps.add(currentValues);
    }

    public String getUID() {
        return (String) image.getTagValue(TagD.getUID(Level.INSTANCE));
    }

    public ImageElement getImage() {
        return image;
    }

    public Layer getLayer() {
        return layer;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public SimpleOpManager getPreProcessOpManager() {
        return this.preProcessOpManager;
    }

    public SimpleOpManager getPostProcessOpManager() {
        return this.postProcessOpManager;
    }

    private void addImageOperationAction(SimpleOpManager manager, ImageOpNode action) {
        manager.addImageOperationAction(action);
    }

    private void removeImageOpertationAction(SimpleOpManager manager, Class<? extends ImageOpNode> cls) {
        for (ImageOpNode op : manager.getOperations()) {
            if (cls.isInstance(op)) {
                manager.removeImageOperationAction(op);
                break;
            }
        }
    }

    public void addPreProcessImageOperationAction(ImageOpNode action) {
        addImageOperationAction(preProcessOpManager, action);
    }

    public void removePreProcessImageOperationAction(Class<? extends ImageOpNode> cls) {
        removeImageOpertationAction(preProcessOpManager, cls);
    }

    public void addPostProcessImageOperationAction(ImageOpNode action) {
        addImageOperationAction(postProcessOpManager, action);
    }

    public void applyPostProcess(ViewCanvas<ImageElement> view) {
        boolean dirty = isDirty();

        if (dirty) {
            if (nextValues.getFullRotation() != currentValues.getFullRotation()) {
                postProcessOpManager.setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE,
                    nextValues.getFullRotation());
            }
            if (!Objects.equals(nextValues.getCropZone(),currentValues.getCropZone()) ) {
                Rectangle area = nextValues.getCropZone();
                postProcessOpManager.setParamValue(CropOp.OP_NAME, CropOp.P_AREA, area);
                postProcessOpManager.setParamValue(CropOp.OP_NAME, CropOp.P_SHIFT_TO_ORIGIN, true);

                if (view != null && area != null && !area.equals(view.getViewModel().getModelArea())) {
                    ((DefaultViewModel) view.getViewModel()).adjustMinViewScaleFromImage(area.width, area.height);
                    view.getViewModel().setModelArea(new Rectangle(0, 0, area.width, area.height));
                    view.setActionsInView(DefaultView2d.PROP_LAYER_OFFSET, new Point(area.x, area.y));
                    view.resetZoom();
                }
            }
            if (nextValues.getBrightness() != currentValues.getBrightness()
                || nextValues.getContrast() != currentValues.getContrast()) {
                postProcessOpManager.setParamValue(BrightnessOp.OP_NAME, BrightnessOp.P_BRIGTNESS_VALUE,
                    (double) nextValues.getBrightness());
                postProcessOpManager.setParamValue(BrightnessOp.OP_NAME, BrightnessOp.P_CONTRAST_VALUE,
                    (double) nextValues.getContrast());
            }

            postProcessOpManager.setParamValue(AutoLevelsOp.OP_NAME, AutoLevelsOp.P_AUTO_LEVEL,
                nextValues.isAutoLevel());
            postProcessOpManager.setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, nextValues.isFlip());

            if (nextValues.getRatio() != currentValues.getRatio()) {
                postProcessOpManager.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_X, nextValues.getRatio());
                postProcessOpManager.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_Y, nextValues.getRatio());
                postProcessOpManager.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_INTERPOLATION, ZoomOp.INTERPOLATIONS[1]);
            }

            if (view != null) {
                // Reset preprocess cache
                postProcessOpManager.resetLastNodeOutputImage();
                view.getImageLayer().setImage(image, postProcessOpManager);
                updateTags(view.getImage());
            }
            preProcessOpManager.removeAllImageOperationAction();

            // Next value become the current value. Register the step.
            currentValues = nextValues;
            nextValues = (AcquireImageValues) currentValues.clone();
            steps.add((AcquireImageValues) currentValues.clone());
        }
    }

    public void applyPreProcess(ViewCanvas<ImageElement> view) {
        for (ImageOpNode action : postProcessOpManager.getOperations()) {
            if (preProcessOpManager.getNode(action.getName()) == null) {
                preProcessOpManager.addImageOperationAction(action.copy());
            }
        }

        if (view != null) {
            view.getImageLayer().setImage(view.getImage(), preProcessOpManager);
        }
    }

    public void removeLayer(ViewCanvas<ImageElement> view) {
        if (view != null) {
            GraphicModel gm = view.getGraphicManager();
            gm.deleteByLayerType(LayerType.ACQUIRE);
            gm.setCreateGraphic(null);
            view.getJComponent().repaint();
        }

    }

    private void updateTags(ImageElement image) {
        this.image.setTag(TagW.ImageWidth, image.getTagValue(TagW.ImageWidth));
        this.image.setTag(TagW.ImageHeight, image.getTagValue(TagW.ImageHeight));
    }

    public void clearPreProcess() {
        preProcessOpManager.removeAllImageOperationAction();
    }

    public AcquireImageValues getNextValues() {
        return nextValues;
    }

    public AcquireImageValues getCurrentValues() {
        return currentValues;
    }

    public AcquireImageValues getDefaultValues() {
        return defaultValues;
    }

    public boolean isDirtyFromDefault() {
        return !defaultValues.equals(nextValues);
    }

    public boolean isDirty() {
        return !currentValues.equals(nextValues);
    }

    public AcquireImageValues restore(ViewCanvas<ImageElement> view) {
        image.setPixelSpacingUnit(defaultValues.getCalibrationUnit());
        image.setPixelSize(defaultValues.getCalibrationRatio());

        postProcessOpManager.setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE, defaultValues.getOrientation());
        postProcessOpManager.setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, defaultValues.isFlip());
        postProcessOpManager.setParamValue(CropOp.OP_NAME, CropOp.P_AREA, null);
        postProcessOpManager.setParamValue(CropOp.OP_NAME, CropOp.P_SHIFT_TO_ORIGIN, null);
        postProcessOpManager.setParamValue(BrightnessOp.OP_NAME, BrightnessOp.P_BRIGTNESS_VALUE,
            (double) defaultValues.getBrightness());
        postProcessOpManager.setParamValue(BrightnessOp.OP_NAME, BrightnessOp.P_CONTRAST_VALUE,
            (double) defaultValues.getContrast());
        postProcessOpManager.setParamValue(AutoLevelsOp.OP_NAME, AutoLevelsOp.P_AUTO_LEVEL,
            defaultValues.isAutoLevel());

        if (view != null) {
            view.getImageLayer().setImage(image, postProcessOpManager);
        }

        steps.clear();
        steps.add(defaultValues);
        currentValues = (AcquireImageValues) defaultValues.clone();
        nextValues = (AcquireImageValues) defaultValues.clone();

        return defaultValues;
    }

    public Serie getSerie() {
        return serie;
    }

    public void setSerie(Serie serie) {
        this.serie = serie;
    }

    @Override
    public String toString() {
        return Optional.ofNullable(image).map(ImageElement::getName).orElse("");
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public AcquireImageStatus getStatus() {
        return status;
    }

    public void setStatus(AcquireImageStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public static final Consumer<AcquireImageInfo> changeStatus(AcquireImageStatus status) {
        return imgInfo -> imgInfo.setStatus(status);
    }
}
