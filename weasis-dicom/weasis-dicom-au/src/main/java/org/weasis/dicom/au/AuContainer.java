package org.weasis.dicom.au;

import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;

public class AuContainer extends ImageViewerPlugin<DicomImageElement> implements PropertyChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuContainer.class);

    public static final List<SynchView> SYNCH_LIST = Collections.synchronizedList(new ArrayList<SynchView>());

    static {
        SYNCH_LIST.add(SynchView.NONE);
    }

    public static final List<GridBagLayoutModel> LAYOUT_LIST =
        Collections.synchronizedList(new ArrayList<GridBagLayoutModel>());

    public static final GridBagLayoutModel VIEWS_1x1 = new GridBagLayoutModel("1x1", //$NON-NLS-1$
        "1x1", 1, 1, AuView.class.getName(), new ImageIcon(ImageViewerPlugin.class //$NON-NLS-1$
            .getResource("/icon/22x22/layout1x1.png"))); //$NON-NLS-1$

    static {
        LAYOUT_LIST.add(VIEWS_1x1);
    }

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>(1));
    private static volatile boolean INI_COMPONENTS = false;

    static final ImageViewerEventManager<DicomImageElement> AU_EVENT_MANAGER =
        new ImageViewerEventManager<DicomImageElement>() {

            @Override
            public boolean updateComponentsListener(ViewCanvas<DicomImageElement> defaultView2d) {
                // Do nothing
                return true;
            }

            @Override
            public void resetDisplay() {
                // Do nothing
            }

            @Override
            public void setSelectedView2dContainer(ImageViewerPlugin<DicomImageElement> selectedView2dContainer) {
                this.selectedView2dContainer = selectedView2dContainer;
            }

            @Override
            public void keyTyped(KeyEvent e) {
                // Do nothing
            }

            @Override
            public void keyPressed(KeyEvent e) {
                // Do nothing
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // Do nothing
            }
        };
    protected AuView auview;

    public AuContainer() {
        this(VIEWS_1x1, null);
    }

    public AuContainer(GridBagLayoutModel layoutModel, String uid) {
        super(AU_EVENT_MANAGER, layoutModel, uid, AuFactory.NAME, AuFactory.ICON, null);
        setSynchView(SynchView.NONE);
        if (!INI_COMPONENTS) {
            INI_COMPONENTS = true;
            // Add standard toolbars
            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            String bundleName = context.getBundle().getSymbolicName();
            String componentName = InsertableUtil.getCName(this.getClass());
            String key = "enable"; //$NON-NLS-1$

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(AuToolBar.class), key, true)) {
                TOOLBARS.add(new AuToolBar<DicomImageElement>(10));
            }
        }
    }

    @Override
    public void setSelectedImagePaneFromFocus(ViewCanvas<DicomImageElement> defaultView2d) {
        setSelectedImagePane(defaultView2d);
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        return menuRoot;
    }

    @Override
    public List<DockableTool> getToolPanel() {
        return null;
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            eventManager.setSelectedView2dContainer(this);

            // Send event to select the related patient in Dicom Explorer.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
                dicomView.getDataExplorerModel().firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.Select, this, null, getGroupID()));
            }

        } else {
            eventManager.setSelectedView2dContainer(null);
        }
    }

    @Override
    public void close() {
        super.close();
        AuFactory.closeSeriesViewer(this);

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                if (auview != null) {
                    auview.dispose();
                }
            }
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object newVal = event.getNewValue();

            if (ObservableEvent.BasicAction.Remove.equals(action)) {
                if (newVal instanceof DicomSeries) {
                    if (auview != null && auview.getSeries() == newVal) {
                        close();
                    }
                } else if (newVal instanceof MediaSeriesGroup) {
                    MediaSeriesGroup group = (MediaSeriesGroup) newVal;
                    // Patient Group
                    if (TagW.PatientPseudoUID.equals(group.getTagID())) {
                        if (group.equals(getGroupID())) {
                            // Close the content of the plug-in
                            close();
                        }
                    }
                    // Study Group
                    else if (TagW.StudyInstanceUID.equals(group.getTagID())) {
                        if (event.getSource() instanceof DicomModel) {
                            DicomModel model = (DicomModel) event.getSource();
                            for (MediaSeriesGroup s : model.getChildren(group)) {
                                if (auview != null && auview.getSeries() == s) {
                                    close();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getViewTypeNumber(GridBagLayoutModel layout, Class defaultClass) {
        return 0;
    }

    @Override
    public boolean isViewType(Class defaultClass, String type) {
        if (defaultClass != null) {
            try {
                Class clazz = Class.forName(type);
                return defaultClass.isAssignableFrom(clazz);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public ViewCanvas<DicomImageElement> createDefaultView(String classType) {
        return null;
    }

    @Override
    public JComponent createUIcomponent(String clazz) {
        try {
            // FIXME use classloader.loadClass or injection
            Class cl = Class.forName(clazz);
            JComponent component = (JComponent) cl.newInstance();
            if (component instanceof SeriesViewerListener) {
                eventManager.addSeriesViewerListener((SeriesViewerListener) component);
            }
            if (component instanceof AuView) {
                auview = (AuView) component;
            }
            return component;
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return null;
    }

    @Override
    public synchronized WtoolBar getStatusBar() {
        return null;
    }

    @Override
    public synchronized List<Toolbar> getToolBar() {
        return TOOLBARS;
    }

    @Override
    public List<Action> getExportActions() {
        return null;
    }

    @Override
    public List<Action> getPrintActions() {
        return null;
    }

    public Series<?> getSeries() {
        if (auview != null) {
            return auview.getSeries();
        }
        return null;
    }

    @Override
    public void addSeries(MediaSeries<DicomImageElement> sequence) {
        if (auview != null && sequence instanceof Series && auview.getSeries() != sequence) {
            auview.setSeries((Series) sequence);
        }
    }

    @Override
    public void addSeriesList(List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
        if (seriesList != null && seriesList.size() > 0) {
            addSeries(seriesList.get(0));
        }
    }

    @Override
    public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
        // Do it in addSeries()
    }

    @Override
    public List<SynchView> getSynchList() {
        return SYNCH_LIST;
    }

    @Override
    public List<GridBagLayoutModel> getLayoutList() {
        return LAYOUT_LIST;
    }

}
