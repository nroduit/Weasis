/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import br.com.animati.texture.mpr3dview.EventPublisher;
import br.com.animati.texture.mpr3dview.GUIManager;
import br.com.animati.texture.mpr3dview.GridViewUI;
import br.com.animati.texture.mpr3dview.ViewTexture;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JSeparator;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DynamicMenu;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 25 Sep.
 */
public abstract class AbstractViewsContainer extends ViewerPlugin<DicomImageElement>
        implements PropertyChangeListener {
    
    /** Grid holds all views and layouts them. */
    protected final ViewsGrid3D grid;
    
    protected final List<ActionDataModel> actions = new ArrayList<ActionDataModel>();   
    
    /**
     * Inits a container with a layout (or null to use default).
     *
     * @param layout First layout.
     */
    public AbstractViewsContainer(final String name, final ImageIcon icon, 
            GridBagLayoutModel layout) {
        super(null, name, icon, null);
        
        grid = new ViewsGrid3D();
        grid.setLayoutModel(layout);
        add(grid, BorderLayout.CENTER);
    }
    
    @Override public List<Action> getExportActions() { return null; }
    @Override public WtoolBar getStatusBar() { return null; }
    public void addSeriesList(List seriesList, boolean removeOldSeries) {
        addSeries((MediaSeries<DicomImageElement>) seriesList.get(0));
    }
    
    @Override
    public List<Action> getPrintActions() {
        return null;
    }

    
    public ViewsGrid getViewsGrid() {
        return grid;
    }
    
    public void setPluginName(String title) {
        if (title != null) {
            if (title.length() > 30) {
                this.setToolTipText(title);
                title = title.substring(0, 30);
                title = title.concat("...");
            }
            super.setPluginName(title);
        }
    }
    
    @Override
    public void removeSeries(MediaSeries series) {
        //TODO
    }
    
    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            GUIManager.getInstance().setSelectedViewerPlugin(this);
            setMouseActions(GUIManager.getInstance().getMouseActions());

            grid.setSelectedView();
            List<GridElement> views = grid.getViews();
            for (GridElement gridElement : views) {
                if (gridElement instanceof ViewTexture) {
                    ((ViewTexture) gridElement).forceResize();
                }
            }
            
            DicomModel dicomView = GUIManager.getInstance().getActiveDicomModel();
            if (dicomView != null) {
                dicomView.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.Select,
                        this, null, getGroupID()));
            }
        }
    }
    
    @Override
    public void close() {
        super.close();
        // Unregister the PropertyChangeListener
        DicomModel dm = GUIManager.getInstance().getActiveDicomModel();
        dm.removePropertyChangeListener(this);

        //Close grid
        grid.dispose();
    }
    
    
    @Override
    public void propertyChange(final PropertyChangeEvent event) {
        String propName = event.getPropertyName();
        if (propName == null) {
            return;
        }
        if (propName.startsWith(EventPublisher.ALL_VIEWERS_DO_ACTION)) {
            for (GridElement ge : grid.getViews()) {
                ge.propertyChange(event);
            }
        } else {
            if ((EventPublisher.CONTAINER_DO_ACTION + ActionW.LAYOUT.cmd()).equals(propName)
                    && event.getNewValue() instanceof GridBagLayoutModel) {
                changeLayoutModel((GridBagLayoutModel) event.getNewValue());
            } else {
                if (propName.startsWith("texture") && event.getNewValue() instanceof MediaSeries) {
                    MediaSeries ms = (MediaSeries) event.getNewValue();
                    grid.updateViewersWithSeries(ms, event);
                    if ("texture.loadComplete".equals(propName)) {
                        handleLoadCompleteEvent(event);
                    }
                } else {
                    if (propName.startsWith(EventPublisher.VIEWER_DO_ACTION)
                            && grid.getSelectedView() != null) {
                        grid.getSelectedView().propertyChange(event);
                    } else {
                        if (event instanceof ObservableEvent) {
                            handlesObservEvent(event);
                        } else {
                            if ("DicomModel.AddImage".equals(propName)) {
                                handlesAddImage(event);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void handlesObservEvent(final PropertyChangeEvent event){
        ObservableEvent obEvt = (ObservableEvent) event;
        ObservableEvent.BasicAction action = obEvt.getActionCommand();
        if (ObservableEvent.BasicAction.Remove.equals(action)) {
            removeContent(obEvt);
        }
    }
    
    private void handlesAddImage(final PropertyChangeEvent event){
        if (!(event.getNewValue() instanceof SeriesEvent)) {
            return;
        }
        SeriesEvent event2 = (SeriesEvent) event.getNewValue();
        SeriesEvent.Action action2 = event2.getActionCommand();
        Object source = event2.getSource();
        Object param = event2.getParam();
        if (SeriesEvent.Action.AddImage.equals(action2)) {
            if (source instanceof DicomSeries
                    && param instanceof DicomImageElement) {
                DicomSeries series = (DicomSeries) source;
                DicomModel dm = GUIManager.getInstance().getActiveDicomModel();
                MediaSeriesGroup parent = dm.getParent(
                        series, DicomModel.patient);
                if (parent != null && parent.equals(getGroupID())) {
                    grid.addImage(series, (DicomImageElement) param);
                }
            }
        }
    }
    
    protected abstract void removeContent(ObservableEvent obEvt);
    protected abstract void handleLoadCompleteEvent(PropertyChangeEvent event);
    
    public Object getActionValue(final String command) {
        for (ActionDataModel model : actions) {
            if (model.getActionW().cmd().equals(command)) {
                return model.getActionValue();
            }
        }
        return null;
    }
    
    public Object getActionData(final String command) {
        for (ActionDataModel model : actions) {
            if (model.getActionW().cmd().equals(command)) {
                return model.getActionData();
            }
        }
        return null;
    }

    public boolean isActionEnabled(final String command) {
        for (ActionDataModel model : actions) {
            if (model.getActionW().cmd().equals(command)) {
                return model.isActionEnabled();
            }
        }
        return false;
    }
    
    public abstract List<GridBagLayoutModel> getLayoutList();
    public abstract GridBagLayoutModel getOriginalLayoutModel();
    public abstract List<ActionW> getMouseButtonActions();
    public abstract ActionW[] getMouseScrollActions();
    
    protected DynamicMenu getComparatorMenu() {
        final DynamicMenu smenu = new DynamicMenu("Sort serie by") {
            @Override
            public void popupMenuWillBecomeVisible() {
                if (grid.getSelectedView() != null) {
                    GridElement view = grid.getSelectedView();
                    Object[] objects = (Object[]) view.getActionData(
                            ActionW.SORTSTACK.cmd());
                    Object selected = view.getActionValue(
                            ActionW.SORTSTACK.cmd());
                    final Boolean isInverse = (Boolean) view.getActionValue(
                            ActionW.INVERSESTACK.cmd());
                    if (objects != null) {
                        final DefaultComboBoxModel model
                                = new DefaultComboBoxModel(objects);
                        final JCheckBoxMenuItem invMenu
                                = new JCheckBoxMenuItem("Inverse", isInverse);

                        model.setSelectedItem(selected);
                        model.addListDataListener(new ListDataListener() {
                            @Override
                            public void intervalAdded(ListDataEvent e) {
                                /* Empty */ }

                            @Override
                            public void intervalRemoved(ListDataEvent e) {
                                /* Empty */ }

                            @Override
                            public void contentsChanged(ListDataEvent e) {
                                SeriesComparator selectedItem
                                        = (SeriesComparator) model.getSelectedItem();
                                Comparator result = selectedItem;
                                if (invMenu.isSelected()) {
                                    result = selectedItem.getReversOrderComparator();
                                }
                                EventPublisher.getInstance().publish(
                                        new PropertyChangeEvent(model,
                                                EventPublisher.VIEWER_DO_ACTION
                                                + ActionW.SORTSTACK.cmd(),
                                                null, result));
                            }
                        });
                        GroupRadioMenu radioMenu = new GroupRadioMenu();
                        radioMenu.setModel(model);

                        invMenu.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                SeriesComparator selectedItem
                                        = (SeriesComparator) model.getSelectedItem();
                                Comparator result = selectedItem;
                                if (invMenu.isSelected()) {
                                    result = selectedItem.getReversOrderComparator();
                                }
                                EventPublisher.getInstance().publish(
                                        new PropertyChangeEvent(model,
                                                EventPublisher.VIEWER_DO_ACTION
                                                + ActionW.SORTSTACK.cmd(),
                                                null, result));
                            }
                        });

                        for (RadioMenuItem radioitem : radioMenu.getRadioMenuItemListCopy()) {
                            add(radioitem);
                        }
                        add(new JSeparator());
                        add(invMenu);
                    }
                }
            }
        };
        
        smenu.addPopupMenuListener();
        return smenu;
    }
    
    /* ------- Access methods delegated to grid. ------------------------- */

    @Override
    public List<MediaSeries<DicomImageElement>> getOpenSeries() {
        return grid.getOpenSeries();
    }
    
    public List<GridElement> getViews() {
        return grid.getViews();
    }

    public GridElement getSelectedPane() {
        return grid.getSelectedView();
    }

    public boolean isContainingView(Object givenView) {
        return grid.isContainingView(givenView);
    }

    public void setSelectedImagePaneFromFocus(GridElement givenView) {
        setSelectedImagePane(givenView);
    }

    public void setSelectedImagePane(GridElement givenView) {
        grid.setSelectedView(givenView);
    }

    public synchronized void setMouseActions(MouseActions mouseActions) {
        grid.setMouseActions(mouseActions);
    }

    public void changeLayoutModel(GridBagLayoutModel layoutModel) {
        for (ActionDataModel model : actions) {
            if (model.getActionW().cmd().equals(ActionW.LAYOUT.cmd())) {
                model.setActionValue(layoutModel);
            }
        }
        grid.setLayoutModel(layoutModel);
        
        // Centraliza imagens quando o layout for trocado.
        for (GridElement elmt : grid.getViews()) {
            if (elmt instanceof GridViewUI) {
                ((GridViewUI) elmt).center();
            }
        }
        EventPublisher.getInstance().publish(new PropertyChangeEvent(
                this, EventPublisher.CONTAINER_ACTION_CHANGED
                + ActionW.LAYOUT.cmd(), null,
                getOriginalLayoutModel()));
        
    }

    public GridBagLayoutModel getLayoutModel() {
        return grid.getLayoutModel();
    }
    
    public boolean isContainingElement(GridElement element) {
        return grid.isContainingView(element);
    }
    
    public MediaSeries<DicomImageElement> getSelectedSeries() {
        return getSelectedPane().getSeries();
    }

}
