package org.weasis.dicom.explorer.pref.download;

import org.weasis.core.api.service.BundleTools;

/**
 * User: boraldo Date: 14.02.14 Time: 13:40
 */
public class SeriesDownloadPrefUtils {


    public static final String DOWNLOAD_IMMEDIATELY = "weasis.wado.download.immediately";

    public static boolean downloadImmediately() {
        return BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(DOWNLOAD_IMMEDIATELY, true);
    }

    public static void downloadImmediately(boolean downloadImmediately) {
        BundleTools.SYSTEM_PREFERENCES.putBooleanProperty(DOWNLOAD_IMMEDIATELY, downloadImmediately);
    }
}
