/*
 * @copyright Copyright (c) 2012 Animati Sistemas de InformÃ¡tica Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import br.com.animati.texture.codec.ImageSeriesFactory;
import br.com.animati.texture.codec.TextureDicomSeries;
import br.com.animati.texture.mpr3dview.EventPublisher;
import br.com.animati.texture.mpr3dview.GUIManager;
import br.com.animati.texture.mpr3dview.GridViewUI;
import br.com.animati.texture.mpr3dview.ViewTexture;
import br.com.animati.texture.mpr3dview.internal.Activator;
import br.com.animati.texturedicom.ControlAxes;
import br.com.animati.texturedicom.ImageSeries;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.CancellationException;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.PresetWindowLevel;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2014, 18 feb.
 */
public class ViewsGrid3D extends ViewsGrid {

    private static final org.slf4j.Logger LOGGER =
            LoggerFactory.getLogger(ViewsGrid3D.class);
    
    protected ControlAxes controlAxes;

    public ViewsGrid3D() {
        super();
    }

    @Override
    public void addSeries(MediaSeries series, Comparator comparator)
            throws Exception {
        if (layoutModel.getId().startsWith("mpr")
                && Activator.useHardwareAcceleration) {
            ImageSeriesFactory factory = new ImageSeriesFactory();
            try {
                TextureDicomSeries imSeries =
                        factory.createImageSeries(series, comparator, false);
                controlAxes = new ControlAxes(imSeries);
                double[] op = imSeries.getOriginalSeriesOrientationPatient();
                setControlAxesBaseOrientation(imSeries.getSeries(), op);
                
                for (GridElement gridElement : viewsList) {
                    if (gridElement.getComponent() instanceof ViewTexture) {
                        ViewTexture vt = (ViewTexture) gridElement.getComponent();
                        vt.setSeries(imSeries);
                        vt.applyProfile(((GridViewUI) gridElement).getProfile(), controlAxes);   
                    } else {
                        LOGGER.error("No ViewTextures...");
                    }
                }
                setSelectedView(getViews().get(0));
                
            } catch (Exception ex) {
                LOGGER.error("Exception ao formar texture para o MPR! " + ex);
                ex.printStackTrace();
                //Ends up here if:
                    //Bad Video card driver.
                    //Video card memory full.
                throw ex; //Expected to be javax.media.opengl.GLException
            }
        } else { //TODO: vai vir aqui se for 1X1!
            controlAxes = null;
            int firstFreeViewIndex = getFirstFreeViewIndex();
            if (firstFreeViewIndex >= 0) {
                pushSeries(viewsList.get(firstFreeViewIndex), series,
                        comparator, true);
                //Need to get from list again, because it may have been replaced
                setSelectedView(viewsList.get(firstFreeViewIndex));
            } else {
                LOGGER.info("Nao tinha nenhum viewer livre!");
            }
        }
    }

    @Override
    public void addImage(DicomSeries series, DicomImageElement image) {
        boolean textureUpdated = false;
        boolean serieOnView = false;
        for (int v = 0; v < getViews().size(); v++) {
            GridElement gridElement = getViews().get(v);
            MediaSeries elmtSeries = gridElement.getSeries();
            if (elmtSeries != null && elmtSeries.equals(series)) {
                textureUpdated = pushImage(gridElement, series,
                        image, textureUpdated);
            } else if (elmtSeries == null) {
                try {
                    addSeries(series);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }
        }
    }
    
    protected GridElement getOneViewer(String type) {

         if (Activator.useHardwareAcceleration) {
            try {
                return new GridViewUI(new ViewTexture(null));
            } catch (Exception ex) {
                LOGGER.info("Could not create a ViewTexture: " + ex.toString());
                ex.printStackTrace();
            }
        }

        JOptionPane.showMessageDialog(this,
                "Cant make a new viewer (no hardware acceleration;).");
        return null;
    }
    

    @Override
    public void updateViewersWithSeries(final MediaSeries mediaSeries,
            final PropertyChangeEvent event) {
        if ("texture.orientationPatient".equals(event.getPropertyName())
                && controlAxes != null) {
            setControlAxesBaseOrientation(
                    mediaSeries, (double[]) event.getNewValue());
            //Reset to Axial
            String[] resets = new String[] {"resetToAxial"};
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    this, EventPublisher.VIEWER_DO_ACTION
                    + ActionW.RESET.cmd(), null, resets));
            
        }
        super.updateViewersWithSeries(mediaSeries, event);
    }
    
    protected void setControlAxesBaseOrientation(
            MediaSeries mediaSeries, double[] op) {
        
        if (controlAxes != null) {
            //set to null!
            controlAxes.unsetBaseOrientation();
        }

        if (controlAxes != null && controlAxes.getImageSeries() != null
                && controlAxes.getImageSeries() instanceof TextureDicomSeries) {
            TextureDicomSeries texture =
                    (TextureDicomSeries) controlAxes.getImageSeries();
            MediaSeries series = texture.getSeries();
            if (mediaSeries != null && mediaSeries.equals(series)) {
                if (op != null && op.length == 6) {
                    if (!texture.hasNegativeSliceSpacing()) {
                        controlAxes.setBaseOrientation(op[0], op[1], op[2],
                                op[3], op[4], op[5]);
                    }
                }
            }
        }
    }
    
    /**
     * Sets the global controlAxes object according to texture and
     * profile.
     * Sets to null if profile does not requires one.
     * @param texture
     * @param id 
     */
    protected void setControlAxes(ImageSeries texture, String layoutId) {
        if (texture != null && layoutId.startsWith("mpr")) {
            controlAxes = new ControlAxes(texture);
            setControlAxesBaseOrientation(
                    ((TextureDicomSeries) texture).getSeries(),
                    ((TextureDicomSeries) texture).getOriginalSeriesOrientationPatient());
        } else if (controlAxes != null){
            controlAxes.setControlledCanvas(0, null);
            controlAxes.setControlledCanvas(1, null);
            controlAxes.setControlledCanvas(2, null);
            controlAxes.watchingCanvases.clear();
            controlAxes = null;
        }
    }
    
    @Override
    public void setLayoutModel(GridBagLayoutModel model) {
        if (model == null) {
            model = ImageViewerPlugin.VIEWS_1x1;
        }
        try {
            layoutModel = (GridBagLayoutModel) model.clone();
        } catch (CloneNotSupportedException ex) {
            layoutModel = model;
        }

        ImageSeries texture = null;

        //Keep views countaining images
        ArrayList<GridElement> oldViews = new ArrayList<GridElement>();
        for (GridElement elmt : viewsList) {
            if (elmt.hasContent()) {
                oldViews.add(elmt);
                if (texture == null && elmt.getComponent() instanceof ViewTexture) {
                    texture = ((ViewTexture) elmt.getComponent()).getParentImageSeries();
                }
            } else {
                elmt.dispose();
            }
        }
        removeAll();
        viewsList.clear();

        //Remove viewers that will not be used:
        int nbview = getViewsNumber(layoutModel);
        if (oldViews.size() > nbview) {
            for (int i = oldViews.size() - 1; i >= nbview; i--) {
                GridElement remove = oldViews.remove(i);
                remove.dispose();
            }
        }

        //ControlAxes must be updated before "view-adds"
        setControlAxes(texture, layoutModel.getId());

        addElementsIntoGrid(layoutModel, oldViews, texture);

        if (layoutModel.getId().startsWith("mpr")) {
            //Reset to Axial
            String[] resets = new String[]{"resetToAxial"};
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    this, EventPublisher.VIEWER_DO_ACTION
                    + ActionW.RESET.cmd(), null, resets));
        }
        try {
            setSelectedView();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        validate();
    }

    private void addElementsIntoGrid(GridBagLayoutModel layoutModel, ArrayList<GridElement> oldViews, ImageSeries texture) {

        final LinkedHashMap<LayoutConstraints, Component> elements = layoutModel.getConstraints();
        Iterator<LayoutConstraints> enumVal = elements.keySet().iterator();
        MouseActions actions = GUIManager.getInstance().getMouseActions();
        while (enumVal.hasNext()) {
            LayoutConstraints next = enumVal.next();
            String type = next.getType();

                GridElement elmt;
                if (!oldViews.isEmpty()) {
                    elmt = oldViews.remove(0);
                } else {
                    elmt = getOneViewer(type);
                }

                viewsList.add(elmt);
                //Add to layout object to be managable by GridMouseAdapter
                elements.put(next, elmt.getComponent());
                //Add to grid to make visible
                add(elmt.getComponent(), next);
                //Set actions
            elmt.enableMouseAndKeyListener(actions);

                if (elmt instanceof GridViewUI) {
                    ((GridViewUI) elmt).setProfile(type);
                }
                if (elmt.getComponent() instanceof ViewTexture) {
                    ViewTexture vt = (ViewTexture) elmt.getComponent();
                    if (layoutModel.getId().startsWith("mpr")) {
                        vt.setSeries((TextureDicomSeries) texture);
                    }
                    vt.applyProfile(type, controlAxes);

                    //BugFix:
                    vt.forceResize();
                }
            
        }
    }

    private JComponent createUIcomponent(String component) {
        try {
            Class cl = Class.forName(component);
            JComponent createdComponent = (JComponent) cl.newInstance();
            return createdComponent;
        } catch (InstantiationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (ClassCastException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    /**
     * Must be done from grid, becouse the viewer can be replaced.
     * @param view View to change.
     * @param value Value that gives the new Comparator.
     */
    public void changeSeriesComparator(GridElement view, Object value) {
        Comparator sortComparator = null;
        //update action
        if (value instanceof Boolean) {
            view.setActionValue(ActionW.INVERSESTACK.cmd(), value);
            Object actionValue = view.getActionValue(ActionW.SORTSTACK.cmd());
            if (actionValue instanceof SeriesComparator) {
                if ((Boolean) value) {
                    sortComparator = ((SeriesComparator) actionValue).getReversOrderComparator();
                } else {
                    sortComparator = (SeriesComparator) actionValue;
                }
            }
        } else if (value instanceof SeriesComparator) {
            view.setActionValue(ActionW.SORTSTACK.cmd(), value);
            view.setActionValue(ActionW.INVERSESTACK.cmd(), false);
            sortComparator = (Comparator) value;
        } else if (value instanceof Comparator) {
            for (SeriesComparator sorter : SortSeriesStack.getValues()) {
                if (sorter.getReversOrderComparator().equals(value)) {
                    view.setActionValue(ActionW.SORTSTACK.cmd(), sorter);
                    view.setActionValue(ActionW.INVERSESTACK.cmd(), true);
                }
            }
            sortComparator = (Comparator) value;
        }

        //chage comparator
        if (sortComparator != null) {
            if (layoutModel.getId().startsWith("mpr")) {
                try {
                    //Para o MPR, estou setando cada viewer diretamente
                    addSeries(selectedView.getSeries(), sortComparator);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                pushSeries(selectedView, selectedView.getSeries(), sortComparator,
                        (selectedView.getComponent() instanceof ViewTexture));
            }
        }
    }
    
        
    /**
     * Must be done from grid, becouse the viewer can be replaced.
     * @param view View to set
     * @param series Series to set
     * @param sort Comparator for series
     * @param useTexture use false to avoid a ViewTexture.
     * @return Resulting view (given one can be replaced).
     */
    public GridElement pushSeries(GridElement view, MediaSeries series,
            Comparator sort, boolean useTexture) {
        MediaSeries seriesToClose = view.getSeries();
        GridViewUI gView = (GridViewUI) view;

        if (series == null) {
            view.setSeries(null);
            //gView.closingSeries(seriesToClose);
            return gView;
        }
        

        GridViewUI resultingView = gView;
        ViewCore core = (ViewCore) gView.getComponent();
        String className = core.getSeriesObjectClassName();
        if (MediaSeries.class.getName().equals(className)) {
            if ("NO.TEXTURE".equals(gView.getProfile())
                    || !Activator.useHardwareAcceleration || !useTexture) {
                //nao converter em textura!
                resultingView.setSeries(series);

            } else if (Activator.useHardwareAcceleration) {
                try {
                    //tentar criar textura
                    TextureDicomSeries texture =
                            new ImageSeriesFactory().createImageSeries(
                                    series, sort, false);

                    //deu certo: substituir viewer
                    ViewTexture viewTexture = new ViewTexture(null);
                    resultingView = new GridViewUI(viewTexture);
                    replaceView(view, resultingView);

                    viewTexture.setSeries(texture);

                } catch (Exception ex) {
                    LOGGER.info("Failed creating texture (173): " + ex.getMessage());
                    //adicionar ao view2d
                    resultingView.setSeries(series);
                }
            } else {
                resultingView.setSeries(series);
            }
        } else if (TextureDicomSeries.class.getName().equals(className)) {
            //texture type
//            if (!useTexture) {
//                //Substitui por outro view
//                if (isEnableDualSupport()) {
//                    ViewImage imageCore = new ViewImage();
//                    resultingView = new GridViewUI(imageCore);
//                    replaceView(view, resultingView);
//
//                    imageCore.setSeries(series);
//                    //return;
//                }
//            } else {

                TextureDicomSeries texture = null;
                try {
                    //tentar criar textura
                    texture = new ImageSeriesFactory().createImageSeries(
                            series, sort, false);
                    //deu certo: adicionar textura
                    resultingView.setSeries(null);
                    core.setSeries(texture);
                } catch (CancellationException ce) {
                    //viewCore.setSeries(texture);
                } catch (Exception ex) {
                    LOGGER.info(
                            "Failed creating texture (204): " + ex.getMessage());
                    ex.printStackTrace();
                //sem textura: substituir viewer...
                    //SE tiver suporte a dual viewer enabled!
//                    if (isEnableDualSupport()) {
//                        ViewImage imgCore = new ViewImage();
//                        resultingView = new GridViewUI(imgCore);
//                        replaceView(view, resultingView);
//
//                        imgCore.setSeries(series);
//                    } else {
//                        JOptionPane.showMessageDialog(this,
//                                "Failed creating texture: " + ex.getMessage());
//                    }
//                }
            }
        }

        resetBasicActions(resultingView);
        //resultingView.closingSeries(seriesToClose);
        
        return resultingView;
    }
    
    private boolean pushImage(GridElement gridElement, DicomSeries series,
            DicomImageElement image, boolean textureUpdated) {

        //A adição desta imagem muda o tipo de viewer?
//        boolean serieForTexture = DualViewerFactory.isSerieForTexture(series);
//        if (serieForTexture && gridElement.getComponent() instanceof ViewImage
//                && ((GridViewUI) gridElement).getProfile() == null){
//            Comparator sort = ((ViewImage) gridElement.getComponent()).getCurrentSortComparator();
//            pushSeries(gridElement, series, sort, serieForTexture);
//            return textureUpdated;
//        }

        if (gridElement.getComponent() instanceof ViewTexture
                && !textureUpdated) {
            TextureDicomSeries parentImageSeries =
                    (TextureDicomSeries) ((ViewTexture) gridElement.getComponent())
                    .getParentImageSeries();

            parentImageSeries.countObjects();
            SeriesComparator seriesComparator
                    = parentImageSeries.getSeriesComparator();
            if (SortSeriesStack.instanceNumber.equals(seriesComparator)) {
                Integer number = (Integer) image.getTagValue(TagW.InstanceNumber);

                //Em multiframes, por exemplo, vai acontecer
                //da textura ter que aumentar
                //aqui vai ter um place fora da textura.
            if ((number - 1) <= parentImageSeries.getSliceCount()) {
                    ImageSeriesFactory.loadOneImageToImageSeries(
                            image, parentImageSeries, number - 1);
                }
            }
            textureUpdated = true;
//        } else if (gridElement.getComponent() instanceof ViewImage) {
//            gridElement.setSeries(series);
        }
        return textureUpdated;
    }


    /**
     * Reset or apply actions for persistence.
     * @param view View to apply.
     */
    private void resetBasicActions(GridElement view) {
        //PAN: no persistence
        //((GridViewUI) view).center();

        Object bestFit = true;
        Object zoom = null;
        Object rotation = 0;
        Object flip = false;


        /* ZOOM **********
         *         Aplicar mesmo sem nada salvo anteriormente.
         * Best-fit == true && zoom == null : best-fit
         * Best-fit == true && zoom != null : best-fit
         * Best-fit == false && zoom == null : best-fit
         * Best-fit == false && zoom != null : usar zoom value
         */
//        if (Boolean.FALSE.equals(bestFit) && zoom != null
//                //Next is to avoid a location bug!
//                && !(view.getComponent() instanceof ViewImage
//                && view.getComponent().getWidth() == 0)) {
//            view.setActionValue(ActionW.ZOOM.cmd(), zoom);
//        } else {
            view.setActionValue(ActionW.ZOOM.cmd(), 0.0D);
//        }

        /* ROTATION & FLIP: Aplicar mesmo sem nada salvo anteriormente. */
        if (rotation instanceof Integer) {
            view.setActionValue(ActionW.ROTATION.cmd(), rotation);
        } else {
            view.setActionValue(ActionW.ROTATION.cmd(), 0);
        }
        view.setActionValue(ActionW.FLIP.cmd(), flip);

        repaint();
    }
       
}
