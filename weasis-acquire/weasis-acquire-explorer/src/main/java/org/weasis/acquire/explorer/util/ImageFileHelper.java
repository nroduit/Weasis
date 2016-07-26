package org.weasis.acquire.explorer.util;

import static javax.swing.JFileChooser.FILES_AND_DIRECTORIES;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImageFileHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageFileHelper.class);

    private static final  String GLOBAL_FILE_DESCRIPTION = "All Image Files";
    private static final Map<String, String> readerFileExtensionMap = createReaderFileExtensionMap();


    private ImageFileHelper() {
    }

    private static Map<String, String> createReaderFileExtensionMap() {
        // TODO - replace by a linked hashMap
        Map<String, String> extensionMap = new HashMap<>();

        for (String extension : ImageIO.getReaderFileSuffixes()) {
            if (extension.length() > 0) {
                extension = extension.toLowerCase(Locale.ENGLISH);
                String description = extension.toUpperCase(Locale.ENGLISH) + " - Image Files (." + extension + ")";
                extensionMap.put(extension, description);
            }
        }
        return extensionMap;
    }

    public static boolean isFileFormatSupported(File file) {
        if (file.isFile()) {
            String fileName = file.getName().toLowerCase(Locale.ENGLISH);
            int index = fileName.lastIndexOf('.');
            return (index > 0 && index < fileName.length() - 1)
                ? readerFileExtensionMap.containsKey(fileName.substring(index + 1)) : false;
        }
        return false;
    }

    public static Set<String> getReaderFileExtensionSet() {
        return readerFileExtensionMap.keySet();
    }

    public static String getReaderFileDescription(String extension) {
        return readerFileExtensionMap.get(extension);
    }


    public static ImageIcon createImageIcon(URL url) {
        return (url != null) ? new ImageIcon(url) : null;
    }

    /**
     * @return null if Canceled
     */
    public static String openImageFileChooser(String path) {// ,Component parent){

        JFileChooser fc = new JFileChooser(path);

        fc.setName("openImageFileChooser");
        fc.setDialogType(javax.swing.JFileChooser.OPEN_DIALOG);
        fc.setControlButtonsAreShown(true);
        fc.setAcceptAllFileFilterUsed(true);
        fc.setFileSelectionMode(FILES_AND_DIRECTORIES);

        for (String extension : getReaderFileExtensionSet()) {
            String description = getReaderFileDescription(extension);
            fc.addChoosableFileFilter(new FileNameExtensionFilter(description, extension));
        }

        fc.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory();
            }

            @Override
            public String getDescription() {
                return GLOBAL_FILE_DESCRIPTION;
            }
        });

        int returnVal = fc.showOpenDialog(null);
        String returnStr = null;

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                returnStr = fc.getSelectedFile().toString();
            } catch (SecurityException e) {
                LOGGER.warn("system property value cannot be accessed", e);
            }
        }
        return returnStr;
    }

}
