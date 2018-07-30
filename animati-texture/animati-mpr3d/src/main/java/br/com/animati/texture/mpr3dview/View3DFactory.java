/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview;

import java.awt.Component;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;

import com.jogamp.opengl.Threading;

import br.com.animati.texture.mpr3dview.internal.Messages;
import br.com.animati.texturedicom.ImageSeries;
import br.com.animati.texturedicom.cl.CLManager;

/**
 *
 * @author Gabriela Carla Bauermann (gabriela@animati.com.br)
 * @version 2013, 16 Jul.
 */

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class, immediate = false)
public class View3DFactory implements SeriesViewerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(View3DFactory.class);

    public static final String NAME = "MPR 3D";
    public static final ImageIcon ICON = new ImageIcon(View3DContainer.class.getResource("/icon/16x16/mpr3d.png"));

    public static String SORT_OPT = "animatitexture.sortOpt";
    public static String CONFIG_HARDWARE = "Video card: Not detected!";
    public static String LOG_MESSAGE;
    public static String TRACE;

    public static final String CONFIG_FILENAME = "config-3d.properties";
    /**
     * Hardware acceleration config name.
     */
    public static final String HA_PROP_NAME = "enableHardwareAcceleration";
    public static final String CL_PROP_NAME = "enableOpenCL";

    /**
     * Hardware acceleration crash flag.
     */
    public static final String CRASH_FLAG = "HAcrash";

    // Debug functions
    public static boolean showModelArea = false;
    public static boolean showMeasurementsOnFrame = false;

    /**
     * Will be true if hardware acceleration is to be tried.
     */
    private static boolean useHardwareAcceleration = true;
    private static boolean useOpenCL = true;
    private static boolean sortOpt = false;
    private static boolean validLicense = true;

    static final WProperties localConfiguration = new WProperties();

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return NAME;
    }

    @Override
    public SeriesViewer createSeriesViewer(Map<String, Object> properties) {

        if (isUseHardwareAcceleration()) {
            GridBagLayoutModel model = View3DContainer.VIEWS_2x1_mpr;
            String uid = null;
            if (properties != null) {
                Object obj = properties.get(org.weasis.core.api.image.GridBagLayoutModel.class.getName());
                if (obj instanceof GridBagLayoutModel) {
                    model = (GridBagLayoutModel) obj;
                } else {
                    obj = properties.get(ViewTexture.class.getName());
                    if (obj instanceof Integer) {
                        ActionState layout = GUIManager.getInstance().getAction(ActionW.LAYOUT);
                        if (layout instanceof ComboItemListener) {
                            Object[] list = ((ComboItemListener) layout).getAllItem();
                            for (Object m : list) {
                                if (m instanceof GridBagLayoutModel) {
                                    if (getViewTypeNumber((GridBagLayoutModel) m, ViewTexture.class) >= (Integer) obj) {
                                        model = (GridBagLayoutModel) m;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                // Set UID
                Object val = properties.get(ViewerPluginBuilder.UID);
                if (val instanceof String) {
                    uid = (String) val;
                }
            }
            View3DContainer instance = new View3DContainer(model, uid, getUIName(), getIcon(), null);
            if (properties != null) {
                Object obj = properties.get(DataExplorerModel.class.getName());
                if (obj instanceof DicomModel) {
                    // Register the PropertyChangeListener
                    DicomModel m = (DicomModel) obj;
                    m.addPropertyChangeListener(instance);
                }
            }

            return instance;
        }

        showHANotAvailableMsg(UIManager.BASE_AREA);
        return null;
    }

    public static int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
        int val = 0;
        if (layout != null && defaultClass != null) {
            Iterator<LayoutConstraints> enumVal = layout.getConstraints().keySet().iterator();
            while (enumVal.hasNext()) {
                try {
                    Class<?> clazz = Class.forName(enumVal.next().getType());
                    if (defaultClass.isAssignableFrom(clazz)) {
                        val++;
                    }
                } catch (Exception e) {
                    LOGGER.error("Checking view", e); //$NON-NLS-1$
                }
            }
        }
        return val;
    }

    public static void closeSeriesViewer(View3DContainer view3dContainer) {
        // Unregister the PropertyChangeListener
        DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
        if (dicomView != null) {
            dicomView.getDataExplorerModel().removePropertyChangeListener(view3dContainer);
        }
    }

    @Override
    public boolean canReadMimeType(String mimeType) {
        return DicomMediaIO.SERIES_MIMETYPE.equals(mimeType);
    }

    @Override
    public boolean isViewerCreatedByThisFactory(SeriesViewer viewer) {
        if (viewer instanceof View3DContainer) {
            return true;
        }
        return false;
    }

    @Override
    public int getLevel() {
        // Deixa em segundo, por enquanto:
        return 10;
    }

    @Override
    public boolean canAddSeries() {
        return false;
    }

    @Override
    public boolean canExternalizeSeries() {
        return true;
    }

    @Override
    public List<Action> getOpenActions() {
        return null;
    }

    public static void showHANotAvailableMsg(Component parent) {
        String msg = validLicense ? Messages.getString("View3DFactory.error.HANotAvailable") : "The 3D plugin license is not valid!";
        JOptionPane.showMessageDialog(parent, msg);
    }

    private void checkFor3dSupport() {
        if (useHardwareAcceleration) {
            localConfiguration.putBooleanProperty(CRASH_FLAG, true);

            Threading.invoke(false, () -> {
                support3D();
            }, null);
        }
    }

    private void support3D() {
        try {
            long startTime = System.currentTimeMillis();
            LOGGER.info("Checking 3D support.");
            ImageSeries.setDRMSerial("BGJ3P-V2TYC-DHXW7"); // development serial
            ImageSeries.checkFor3dSupport();
            if (ImageSeries.getGPUDefinition() != null) {
                CONFIG_HARDWARE = ImageSeries.getGPUDefinition().toString();
            }
            localConfiguration.putBooleanProperty(CRASH_FLAG, false);
            LOGGER.info("{} 3D Support is active. Time of checking: {}  seconds.", AuditLog.MARKER_PERF,
                (System.currentTimeMillis() - startTime) / 1000D);
        } catch (Exception ex) {
            LOG_MESSAGE = "Cant get context: " + ex.getMessage();
            LOGGER.info("Cant get context: " + ex.getMessage());
            if (ImageSeries.getGPUDefinition() != null) {
                CONFIG_HARDWARE = ImageSeries.getGPUDefinition().toString();
            }
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            TRACE = sw.toString();

            useHardwareAcceleration = false;
            if ("Unauthorized access".equals(ex.getMessage())) {
                validLicense = false;
            } else {
                // Set acceleration to false and save it to props
                localConfiguration.putBooleanProperty(HA_PROP_NAME, useHardwareAcceleration);
            }

            if (TRACE == null) {
                TRACE = "Stack trace: null";
            }
        }
        LOGGER.info("Video card: " + CONFIG_HARDWARE);
    }

    public static boolean isUseHardwareAcceleration() {
        return useHardwareAcceleration;
    }

    public static boolean isUseOpenCL() {
        return useOpenCL;
    }

    public static boolean isSortOpt() {
        return sortOpt;
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) throws Exception {
        LOGGER.info(" Mpr 3D View [Animati] is activated"); //$NON-NLS-1$
        // Must be done very early:
        System.setProperty("jogl.1thread", "worker");

        FileUtil.readProperties(new File(BundlePreferences.getDataFolder(context.getBundleContext()), CONFIG_FILENAME), // $NON-NLS-1$
            localConfiguration);

        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.force.3d", false)) { //$NON-NLS-1$
            localConfiguration.putBooleanProperty(CRASH_FLAG, false);
            localConfiguration.putBooleanProperty(HA_PROP_NAME, true);
            localConfiguration.putBooleanProperty(CL_PROP_NAME, true);
        }

        sortOpt = localConfiguration.getBooleanProperty(SORT_OPT, false);
        useHardwareAcceleration = localConfiguration.getBooleanProperty(HA_PROP_NAME, true);
        useOpenCL = localConfiguration.getBooleanProperty(CL_PROP_NAME, true);
        boolean hasCrashed = localConfiguration.getBooleanProperty(CRASH_FLAG, false);
        if (hasCrashed) {
            TRACE = "Has crashed before.";
        }

        if (hasCrashed && useHardwareAcceleration) {
            LOGGER.info("Turning H Acceleration OFF, because has cashed before.");
            useHardwareAcceleration = false;
            localConfiguration.putBooleanProperty(HA_PROP_NAME, useHardwareAcceleration);
            useOpenCL = false;
        }

        if (useHardwareAcceleration) {
            if (!useOpenCL) {
                CLManager.OPENCL_ENABLED = false;
            }
            checkFor3dSupport();
        }

    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info(" Mpr 3D View [Animati] is deactivated"); //$NON-NLS-1$
        FileUtil.storeProperties(new File(BundlePreferences.getDataFolder(context.getBundleContext()), CONFIG_FILENAME), // $NON-NLS-1$
            localConfiguration, null);
    }
}
