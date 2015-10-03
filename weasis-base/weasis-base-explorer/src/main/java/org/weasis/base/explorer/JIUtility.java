package org.weasis.base.explorer;

import java.io.File;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;

import org.weasis.core.api.media.data.MediaElement;

public final class JIUtility {

    public static final String[] DATE_FORMAT = { "yyyyMMddHHmmssSSS", "yyyy-MMM-dd-HHmmssSSS" }; //$NON-NLS-1$ //$NON-NLS-2$
    private static final String osName = System.getProperty("os.name").toLowerCase(); //$NON-NLS-1$
    public static final String USER_ROOT = System.getProperty("user.home"); //$NON-NLS-1$
    public static final String ROOT_FOLDER;

    static {
        if (osName.startsWith("win")) { //$NON-NLS-1$
            ROOT_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + "My Computer"; //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            ROOT_FOLDER = File.separator;
        }
    }

    public static FileTreeModel createTreeModel() {
        // Using "My Computer" as root.
        TreeNode rootNode = null;

        if (osName.startsWith("win")) { //$NON-NLS-1$
            // Create a temp "My Computer" folder.
            final File MY_COMPUTER_FOLDER_FILE = new File(ROOT_FOLDER);

            MY_COMPUTER_FOLDER_FILE.mkdirs();
            // Delete temp file when program exits.
            MY_COMPUTER_FOLDER_FILE.deleteOnExit();

            rootNode = new TreeNode(MY_COMPUTER_FOLDER_FILE);
            rootNode.setRoot(true);
            rootNode.explore();

        } else {

            final File rootFile = new File(ROOT_FOLDER);

            rootNode = new TreeNode(rootFile);
            rootNode.setRoot(true);
            rootNode.explore();
        }

        return new FileTreeModel(rootNode);
    }

    public static File[] getRoots() {
        return constructRoots();
    }

    private static File[] constructRoots() {
        File[] roots;
        final Vector<File> rootsVector = new Vector<File>();

        if (osName.toLowerCase().startsWith("win")) { //$NON-NLS-1$
            // Run through all possible mount points and check
            // for their existance.
            for (char c = 'C'; c <= 'Z'; c++) {
                final char device[] = { c, ':', '\\' };
                final String deviceName = new String(device);
                final File deviceFile = new File(deviceName);

                if ((deviceFile != null) && deviceFile.exists()) {
                    rootsVector.addElement(deviceFile);
                }
            }
        } else if (osName.toLowerCase().startsWith("mac")) { //$NON-NLS-1$
            for (final File root : (new File("/Volumes")).listFiles()) { //$NON-NLS-1$
                rootsVector.addElement(root);
            }
        } else {
            for (final File root : File.listRoots()) {
                rootsVector.addElement(root);
            }
        }
        roots = new File[rootsVector.size()];
        rootsVector.copyInto(roots);

        return roots;
    }

    public static Icon getSystemIcon(final MediaElement dObj) {
        if (dObj.getFile().exists()) {
            return FileSystemView.getFileSystemView().getSystemIcon(dObj.getFile());
        } else {
            return FileSystemView.getFileSystemView().getSystemIcon(new File(System.getProperty("user.home"))); //$NON-NLS-1$
        }
    }

    public static Icon getSystemIcon(final File f) {
        if (f.exists()) {
            return FileSystemView.getFileSystemView().getSystemIcon(f);
        } else {
            return FileSystemView.getFileSystemView().getSystemIcon(new File(System.getProperty("user.home"))); //$NON-NLS-1$
        }
    }

    public static String suffix(final String name) {
        final int i = name.lastIndexOf('.');
        if (i > 0) {
            return name.toLowerCase().substring(i + 1);
        }
        return null;
    }

    public static String getNumericPaddedString(final int value, final int radix, final int padding) {
        final String str = Integer.toString(value, radix);
        final StringBuilder strBuf = new StringBuilder();

        while ((padding > str.length()) && (strBuf.length() < (padding - str.length()))) {
            strBuf.append("0"); //$NON-NLS-1$
        }

        strBuf.append(str);
        return strBuf.toString();
    }

}
