/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.internal;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.media.opengl.Threading;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.dicom.viewer2d.EventManager;

import br.com.animati.texturedicom.ImageSeries;
import br.com.animati.texturedicom.cl.CLManager;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2015, 09 Feb
 */
public class Activator implements BundleActivator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    public static String CONFIG_HARDWARE = "Video card: Not detected!";
    public static String LOG_MESSAGE;
    public static String TRACE;

    /** Hardware acceleration config name. */
    public static final String HA_PROP_NAME = "enableHardwareAcceleration";
    public static final String CL_PROP_NAME = "enableOpenCL";

    /** Hardware acceleration crash flag. */
    public static final String CRASH_FLAG = "HAcrash";

    /** Will be true if hardware acceleration is to be tryed. */
    public static Boolean useHardwareAcceleration = true;
    public static boolean useOpenCL = true;
    private JDialog infoHALoading = null;
    public static boolean isAlive = false;

    @Override
    public void start(BundleContext context) throws Exception {
        LOGGER.info("Starting bundle Mpr 3D View [Animati].");

        Preferences dPrefs = BundlePreferences.getDefaultPreferences(context);
        if (dPrefs != null) {
            useHardwareAcceleration = dPrefs.getBoolean(HA_PROP_NAME, useHardwareAcceleration);
            useOpenCL = dPrefs.getBoolean(CL_PROP_NAME, useOpenCL);
            boolean hasCrashed = dPrefs.getBoolean(CRASH_FLAG, false);
            if (hasCrashed) {
                TRACE = "Has crashed before.";
            }
            // TODO disable for testing
            // if (hasCrashed && useHardwareAcceleration) {
            // LOGGER.info("Turning H Acceleration OFF, because has cashed before.");
            // useHardwareAcceleration = false;
            // dPrefs.putBoolean(HA_PROP_NAME, false);
            // useOpenCL = false;
            // dPrefs.flush();
            // }
        }

        if (useHardwareAcceleration) {
            if (!useOpenCL) {
                CLManager.OPENCL_ENABLED = false;
            }
            checkFor3dSupport();
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        LOGGER.info("Stopping bundle Mpr 3D View [Animati].");
        // Save preferences
        EventManager.getInstance().savePreferences(context);
        isAlive = false;
    }

    private void checkFor3dSupport() {
        if (useHardwareAcceleration) {
            setHACrashFlag(true);

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    infoHALoading = new JDialog(WeasisWin.getInstance().getFrame(), true);
                    JPanel panelInfo = new JPanel();
                    panelInfo.add(new JLabel(Messages.getString("Activator.messageHA")));
                    infoHALoading.add(panelInfo);
                    infoHALoading.setLocation(600, 400);
                    infoHALoading.pack();
                    infoHALoading.setVisible(true);
                }
            });

            Threading.invoke(true, new Runnable() {
                @Override
                public void run() {
                    support3D();
                    if (infoHALoading != null) {
                        infoHALoading.dispose();
                        infoHALoading = null;
                    }
                }
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
            setHACrashFlag(false);
            LOGGER.info("3D Support is active. Time of checking: " + (System.currentTimeMillis() - startTime) / 1000D
                + " seconds.");
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

            // Set acceleration to false and save it to props
            useHardwareAcceleration = false;
            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            Preferences dPrefs = BundlePreferences.getDefaultPreferences(context);
            if (dPrefs != null) {
                dPrefs.putBoolean(HA_PROP_NAME, false);
                try {
                    dPrefs.flush();
                } catch (BackingStoreException ex1) {
                }
            }
            if (TRACE == null) {
                TRACE = "Stack trace: null";
            }
        }
        LOGGER.info("Video card: " + CONFIG_HARDWARE);
    }

    private void setHACrashFlag(boolean crash) {
        final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        Preferences dPrefs = BundlePreferences.getDefaultPreferences(context);
        if (dPrefs != null) {
            try {
                dPrefs.putBoolean(CRASH_FLAG, crash);
                dPrefs.flush();
            } catch (BackingStoreException ex) {
                ex.printStackTrace();
            }
        }
    }

}
