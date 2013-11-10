package org.weasis.dicom.viewer2d.mpr;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.CrosshairListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.print.DicomPrintDialog;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.LutToolBar;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.ResetTools;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.View2dFactory;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class MPRContainer extends ImageViewerPlugin<DicomImageElement> implements PropertyChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MPRContainer.class);

    public static final List<SynchView> SYNCH_LIST = Collections.synchronizedList(new ArrayList<SynchView>());

    static SynchView DEFAULT_MPR;

    static {
        SYNCH_LIST.add(SynchView.NONE);

        HashMap<String, Boolean> actions = new HashMap<String, Boolean>();
        actions.put(ActionW.SCROLL_SERIES.cmd(), true);
        actions.put(ActionW.RESET.cmd(), true);
        actions.put(ActionW.ZOOM.cmd(), true);
        actions.put(ActionW.WINDOW.cmd(), true);
        actions.put(ActionW.LEVEL.cmd(), true);
        actions.put(ActionW.PRESET.cmd(), true);
        actions.put(ActionW.LUT_SHAPE.cmd(), true);
        actions.put(ActionW.LUT.cmd(), true);
        actions.put(ActionW.INVERSELUT.cmd(), true);
        actions.put(ActionW.FILTER.cmd(), true);
        DEFAULT_MPR = new SynchView("MPR synch", "mpr", SynchData.Mode.Stack, //$NON-NLS-1$ //$NON-NLS-2$
            new ImageIcon(SynchView.class.getResource("/icon/22x22/tile.png")), actions); //$NON-NLS-1$

        SYNCH_LIST.add(DEFAULT_MPR);
    }

    public static final GridBagLayoutModel VIEWS_2x1_mpr = new GridBagLayoutModel(
        new LinkedHashMap<LayoutConstraints, Component>(3), "mpr", "Orthogonal MPR", null);
    static {
        LinkedHashMap<LayoutConstraints, Component> constraints = VIEWS_2x1_mpr.getConstraints();
        constraints.put(new LayoutConstraints(MprView.class.getName(), 0, 0, 0, 1, 2, 0.5, 1.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints(MprView.class.getName(), 1, 1, 0, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints(MprView.class.getName(), 2, 1, 1, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);

    }

    public static final List<GridBagLayoutModel> LAYOUT_LIST = Collections
        .synchronizedList(new ArrayList<GridBagLayoutModel>());
    static {
        LAYOUT_LIST.add(VIEWS_2x1_mpr);
        LAYOUT_LIST.add(VIEWS_2x2_f2);
        LAYOUT_LIST.add(VIEWS_2_f1x2);
        LAYOUT_LIST.add(VIEWS_1x2);
        LAYOUT_LIST.add(VIEWS_2x1);
    }

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>());
    public static final List<DockableTool> TOOLS = View2dContainer.TOOLS;
    private static volatile boolean INI_COMPONENTS = false;

    private volatile Thread process;
    private volatile String lastCommand;

    public MPRContainer() {
        this(VIEWS_1x1);
    }

    public MPRContainer(GridBagLayoutModel layoutModel) {
        super(EventManager.getInstance(), layoutModel, MPRFactory.NAME, MPRFactory.ICON, null);
        setSynchView(SynchView.NONE);
        if (!INI_COMPONENTS) {
            INI_COMPONENTS = true;
            // Add standard toolbars
            WProperties props = (WProperties) BundleTools.SYSTEM_PREFERENCES.clone();
            props.putBooleanProperty("weasis.toolbar.synchbouton", false);

            EventManager evtMg = EventManager.getInstance();
            TOOLBARS.add(View2dContainer.TOOLBARS.get(0));
            TOOLBARS.add(new MeasureToolBar(evtMg, 11));
            TOOLBARS.add(new ZoomToolBar(evtMg, 20));
            TOOLBARS.add(new RotationToolBar(evtMg, 30));
            TOOLBARS.add(new LutToolBar<DicomImageElement>(40));

            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            Preferences prefs = BundlePreferences.getDefaultPreferences(context);
            if (prefs != null) {
                String className = this.getClass().getSimpleName().toLowerCase();
                InsertableUtil.applyPreferences(TOOLBARS, prefs, context.getBundle().getSymbolicName(),
                    className, Type.TOOLBAR);
            }
        }
    }

    @Override
    public void setSelectedImagePaneFromFocus(DefaultView2d<DicomImageElement> defaultView2d) {
        setSelectedImagePane(defaultView2d);
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();
            menuRoot.setText(View2dFactory.NAME);

            List<Action> actions = getPrintActions();
            if (actions != null) {
                JMenu printMenu = new JMenu(Messages.getString("View2dContainer.print")); //$NON-NLS-1$
                for (Action action : actions) {
                    JMenuItem item = new JMenuItem(action);
                    printMenu.add(item);
                }
                menuRoot.add(printMenu);
            }

            // ActionState viewingAction = eventManager.getAction(ActionW.VIEWINGPROTOCOL);
            // if (viewingAction instanceof ComboItemListener) {
            // menuRoot.add(((ComboItemListener) viewingAction).createMenu(Messages
            //                    .getString("View2dContainer.view_protocols"))); //$NON-NLS-1$
            // }
            // ActionState presetAction = eventManager.getAction(ActionW.PRESET);
            // if (presetAction instanceof ComboItemListener) {
            // JMenu menu =
            // ((ComboItemListener) presetAction).createUnregisteredRadioMenu(Messages
            //                        .getString("View2dContainer.presets"));//$NON-NLS-1$
            // for (Component mitem : menu.getMenuComponents()) {
            // RadioMenuItem ritem = ((RadioMenuItem) mitem);
            // PresetWindowLevel preset = (PresetWindowLevel) ritem.getObject();
            // if (preset.getKeyCode() > 0) {
            // ritem.setAccelerator(KeyStroke.getKeyStroke(preset.getKeyCode(), 0));
            // }
            // }
            // menuRoot.add(menu);
            // }
            // ActionState lutShapeAction = eventManager.getAction(ActionW.LUT_SHAPE);
            // if (lutShapeAction instanceof ComboItemListener) {
            // menuRoot.add(((ComboItemListener) lutShapeAction).createMenu("LUT_Shape"));
            // }
            ActionState lutAction = eventManager.getAction(ActionW.LUT);
            if (lutAction instanceof ComboItemListener) {
                JMenu menu = ((ComboItemListener) lutAction).createMenu(Messages.getString("View2dContainer.lut")); //$NON-NLS-1$
                ActionState invlutAction = eventManager.getAction(ActionW.INVERSELUT);
                if (invlutAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) invlutAction).createMenu(Messages
                        .getString("View2dContainer.inv_lut"))); //$NON-NLS-1$
                }
                menuRoot.add(menu);
            }
            ActionState filterAction = eventManager.getAction(ActionW.FILTER);
            if (filterAction instanceof ComboItemListener) {
                JMenu menu =
                    ((ComboItemListener) filterAction).createMenu(Messages.getString("View2dContainer.filter")); //$NON-NLS-1$
                menuRoot.add(menu);
            }
            ActionState stackAction = eventManager.getAction(ActionW.SORTSTACK);
            if (stackAction instanceof ComboItemListener) {
                JMenu menu =
                    ((ComboItemListener) stackAction).createMenu(Messages.getString("View2dContainer.sort_stack")); //$NON-NLS-1$
                ActionState invstackAction = eventManager.getAction(ActionW.INVERSESTACK);
                if (invstackAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) invstackAction).createMenu(Messages
                        .getString("View2dContainer.inv_stack"))); //$NON-NLS-1$
                }
                menuRoot.add(menu);
            }
            ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
            if (rotateAction instanceof SliderChangeListener) {
                menuRoot.add(new JSeparator());
                JMenu menu = new JMenu(Messages.getString("View2dContainer.orientation")); //$NON-NLS-1$
                JMenuItem menuItem = new JMenuItem(Messages.getString("ResetTools.reset")); //$NON-NLS-1$
                final SliderChangeListener rotation = (SliderChangeListener) rotateAction;
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue(0);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.-90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() - 90 + 360) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.+90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 90) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.+180")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 180) % 360);
                    }
                });
                menu.add(menuItem);
                ActionState flipAction = eventManager.getAction(ActionW.FLIP);
                if (flipAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) flipAction).createMenu(Messages
                        .getString("View2dContainer.flip_h"))); //$NON-NLS-1$
                    menuRoot.add(menu);
                }
            }

            menuRoot.add(new JSeparator());
            menuRoot.add(ResetTools.createUnregisteredJMenu());

        }
        return menuRoot;
    }

    @Override
    public List<DockableTool> getToolPanel() {
        return TOOLS;
    }

    @Override
    public void setSelected(boolean selected) {
        final ViewerToolBar toolBar = getViewerToolBar();
        if (selected) {
            if (toolBar != null) {
                String command = ActionW.CROSSHAIR.cmd();
                MouseActions mouseActions = eventManager.getMouseActions();
                String lastAction = mouseActions.getAction(MouseActions.LEFT);
                if (!command.equals(lastAction)) {
                    lastCommand = lastAction;
                    mouseActions.setAction(MouseActions.LEFT, command);
                    setMouseActions(mouseActions);
                    toolBar.changeButtonState(MouseActions.LEFT, command);
                }
            }

            eventManager.setSelectedView2dContainer(this);

            // Send event to select the related patient in Dicom Explorer.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
                dicomView.getDataExplorerModel().firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.Select, this, null, getGroupID()));
            }

        } else {
            if (lastCommand != null && toolBar != null) {
                MouseActions mouseActions = eventManager.getMouseActions();
                if (ActionW.CROSSHAIR.cmd().equals(mouseActions.getAction(MouseActions.LEFT))) {
                    mouseActions.setAction(MouseActions.LEFT, lastCommand);
                    setMouseActions(mouseActions);
                    toolBar.changeButtonState(MouseActions.LEFT, lastCommand);
                    lastCommand = null;
                }
            }
            eventManager.setSelectedView2dContainer(null);
        }

    }

    @Override
    public void close() {
        if (process != null) {
            final Thread t = process;
            process = null;
            t.interrupt();
        }
        super.close();
        MPRFactory.closeSeriesViewer(this);
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                for (DefaultView2d v : view2ds) {
                    v.dispose();
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
                    DicomSeries dicomSeries = (DicomSeries) newVal;
                    for (DefaultView2d<DicomImageElement> v : view2ds) {
                        MediaSeries<DicomImageElement> s = v.getSeries();
                        if (dicomSeries.equals(s)) {
                            v.setSeries(null);
                        }
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
                                for (DefaultView2d<DicomImageElement> v : view2ds) {
                                    MediaSeries series = v.getSeries();
                                    if (s.equals(series)) {
                                        v.setSeries(null);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.Replace.equals(action)) {
                if (newVal instanceof Series) {
                    Series series = (Series) newVal;
                    for (DefaultView2d<DicomImageElement> v : view2ds) {
                        MediaSeries<DicomImageElement> s = v.getSeries();
                        if (series.equals(s)) {
                            // It will reset MIP view
                            v.setSeries(series, null);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getViewTypeNumber(GridBagLayoutModel layout, Class defaultClass) {
        return View2dFactory.getViewTypeNumber(layout, defaultClass);
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
    public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
        return new MprView(eventManager);
    }

    @Override
    public JComponent createUIcomponent(String clazz) {
        if (isViewType(DefaultView2d.class, clazz)) {
            return createDefaultView(clazz);
        }

        try {
            // FIXME use classloader.loadClass or injection
            Class cl = Class.forName(clazz);
            JComponent component = (JComponent) cl.newInstance();
            if (component instanceof SeriesViewerListener) {
                eventManager.addSeriesViewerListener((SeriesViewerListener) component);
            }
            return component;

        } catch (InstantiationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        }

        catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (ClassCastException e1) {
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
        ArrayList<Action> actions = new ArrayList<Action>(1);
        final String title = Messages.getString("View2dContainer.print_layout"); //$NON-NLS-1$
        AbstractAction printStd =
            new AbstractAction(title, new ImageIcon(ImageViewerPlugin.class.getResource("/icon/16x16/printer.png"))) { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent e) {
                    Window parent = WinUtil.getParentWindow(MPRContainer.this);
                    PrintDialog dialog = new PrintDialog(parent, title, eventManager);
                    JMVUtils.showCenterScreen(dialog, parent);
                }
            };
        actions.add(printStd);

        final String title2 = Messages.getString("View2dContainer.dcm_print"); //$NON-NLS-1$
        AbstractAction printStd2 = new AbstractAction(title2, null) {

            @Override
            public void actionPerformed(ActionEvent e) {
                Window parent = WinUtil.getParentWindow(MPRContainer.this);
                DicomPrintDialog dialog = new DicomPrintDialog(parent, title2, eventManager);
                JMVUtils.showCenterScreen(dialog, parent);
            }
        };
        actions.add(printStd2);
        return actions;
    }

    public MprView getMprView(SliceOrientation sliceOrientation) {
        for (DefaultView2d v : view2ds) {
            if (v instanceof MprView) {
                if (sliceOrientation != null && sliceOrientation.equals(((MprView) v).getSliceOrientation())) {
                    return (MprView) v;
                }
            }
        }
        return null;
    }

    @Override
    public void addSeries(MediaSeries<DicomImageElement> sequence) {
        if (process != null) {
            final Thread t = process;
            process = null;
            t.interrupt();
        }
        // TODO Should be init elsewhere
        for (int i = 0; i < view2ds.size(); i++) {
            DefaultView2d<DicomImageElement> val = view2ds.get(i);
            if (val instanceof MprView) {
                SliceOrientation sliceOrientation;
                switch (i) {
                    case 1:
                        sliceOrientation = SliceOrientation.CORONAL;
                        break;
                    case 2:
                        sliceOrientation = SliceOrientation.SAGITTAL;
                        break;
                    default:
                        sliceOrientation = SliceOrientation.AXIAL;
                        break;
                }
                ((MprView) val).setType(sliceOrientation);
            }
        }

        final MprView view = selectLayoutPositionForAddingSeries(sequence);
        if (view != null) {
            view.setSeries(sequence);

            String title = (String) sequence.getTagValue(TagW.PatientName);
            if (title != null) {
                if (title.length() > 30) {
                    this.setToolTipText(title);
                    title = title.substring(0, 30);
                    title = title.concat("..."); //$NON-NLS-1$
                }
                this.setPluginName(title);
            }
            view.repaint();
            process = new Thread("Building MPR views") {
                @Override
                public void run() {
                    try {
                        SeriesBuilder.createMissingSeries(this, MPRContainer.this, view);

                        // Following actions need to be executed in EDT thread
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                ActionState synch = EventManager.getInstance().getAction(ActionW.SYNCH);
                                if (synch instanceof ComboItemListener) {
                                    ((ComboItemListener) synch).setSelectedItem(MPRContainer.DEFAULT_MPR);
                                }
                                ActionState cross = EventManager.getInstance().getAction(ActionW.CROSSHAIR);
                                if (cross instanceof CrosshairListener) {
                                    ((CrosshairListener) cross).setPoint(view.getImageCoordinatesFromMouse(
                                        view.getWidth() / 2, view.getHeight() / 2));
                                }
                            }
                        });

                    } catch (final Exception e) {
                        e.printStackTrace();
                        // Following actions need to be executed in EDT thread
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                for (DefaultView2d v : view2ds) {
                                    if (v != view && v instanceof MprView) {
                                        JProgressBar bar = ((MprView) v).getProgressBar();
                                        if (bar == null) {
                                            bar = new JProgressBar();
                                            Dimension dim = new Dimension(v.getWidth() / 2, 30);
                                            bar.setSize(dim);
                                            bar.setPreferredSize(dim);
                                            bar.setMaximumSize(dim);
                                            bar.setValue(0);
                                            bar.setStringPainted(true);
                                            ((MprView) v).setProgressBar(bar);
                                        }
                                        bar.setString(e.getMessage());
                                        v.repaint();
                                    }
                                }
                            }
                        });
                    }
                }

            };
            process.start();
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

    public MprView selectLayoutPositionForAddingSeries(MediaSeries s) {
        if (s != null) {
            Object img = s.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
            if (img instanceof DicomImageElement) {
                double[] v = (double[]) ((DicomImageElement) img).getTagValue(TagW.ImageOrientationPatient);
                if (v != null && v.length == 6) {
                    String orientation =
                        ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v[0], v[1], v[2], v[3],
                            v[4], v[5]);
                    SliceOrientation sliceOrientation = SliceOrientation.AXIAL;
                    if (ImageOrientation.LABELS[3].equals(orientation)) {
                        sliceOrientation = SliceOrientation.CORONAL;
                    } else if (ImageOrientation.LABELS[2].equals(orientation)) {
                        sliceOrientation = SliceOrientation.SAGITTAL;
                    }
                    MprView view = getMprView(sliceOrientation);
                    if (view != null) {
                        setSelectedImagePane(view);
                        return view;
                    }
                }
            }
        }
        return null;
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
