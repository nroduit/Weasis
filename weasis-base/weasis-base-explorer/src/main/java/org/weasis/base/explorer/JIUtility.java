package org.weasis.base.explorer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;

import org.weasis.core.api.media.data.MediaElement;

public final class JIUtility {

    public static final String[] DATE_FORMAT = { "yyyyMMddHHmmssSSS", "yyyy-MMM-dd-HHmmssSSS" };
    private static final String osName = System.getProperty("os.name").toLowerCase();
    public static final String USER_ROOT = System.getProperty("user.home");
    public static final String ROOT_FOLDER;
    static {
        if (osName.startsWith("win")) {
            ROOT_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + "My Computer";
        } else {
            ROOT_FOLDER = File.separator;
        }
    }

    private static final Set<String> unwanted = new HashSet<String>();
    // TODO is using faster jpeg codec ?
    static {
        unwanted.add("com.sun.media.imageioimpl.plugins.jpeg.CLibJPEGImageReader");
        unwanted.add("com.sun.media.imageioimpl.plugins.jpeg.CLibJPEGImageWriter");
    }

    public static final FileTreeModel createTreeModel() {
        // Using "My Computer" as root.
        TreeNode rootNode = null;

        if (osName.startsWith("windows")) {
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

    public static String portablePath(final String path) {

        final StringBuffer result = new StringBuffer();
        // startIdx and idxOld delimit various chunks of aInput; these
        // chunks always end where aOldPattern begins
        int startIdx = 0;
        int idxOld = 0;
        while ((idxOld = path.indexOf("\\", startIdx)) >= 0) {
            // grab a part of aInput which does not include aOldPattern
            result.append(path.substring(startIdx, idxOld));
            // add aNewPattern to take place of aOldPattern
            result.append("/");

            // reset the startIdx to just after the current match, to see
            // if there are any further matches
            startIdx = idxOld + 1;
        }
        // the final chunk will go to the end of aInput
        result.append(path.substring(startIdx));

        return result.toString();
    }

    public static String systemPath(final String path) {

        if (File.separator.equals("\\")) {
            final StringBuffer result = new StringBuffer();
            // startIdx and idxOld delimit various chunks of aInput; these
            // chunks always end where aOldPattern begins
            int startIdx = 0;
            int idxOld = 0;
            while ((idxOld = path.indexOf("/", startIdx)) >= 0) {
                // grab a part of aInput which does not include aOldPattern
                result.append(path.substring(startIdx, idxOld));
                // add aNewPattern to take place of aOldPattern
                result.append("\\");

                // reset the startIdx to just after the current match, to see
                // if there are any further matches
                startIdx = idxOld + 1;
            }
            // the final chunk will go to the end of aInput
            result.append(path.substring(startIdx));
            return result.toString();
        }
        return path;
    }

    public static String length2KB(final long length) {
        final long kbCount = (length + 1024) / 1024;
        final String strlength = String.valueOf(kbCount);
        return String.valueOf((kbCount > 999 ? strlength.substring(0, strlength.length() - 3) + ","
            + strlength.substring(strlength.length() - 3) : strlength)
            + " KB ");
    }

    public final static File[] getRoots() {
        return constructRoots();
    }

    private final static File[] constructRoots() {
        File[] roots;
        final Vector<File> rootsVector = new Vector<File>();

        if (osName.toLowerCase().startsWith("win")) {
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
        } else if (osName.toLowerCase().startsWith("mac")) {
            for (final File root : (new File("/Volumes")).listFiles()) {
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

    public final static Icon getSystemIcon(final MediaElement dObj) {
        if (dObj.getFile().exists()) {
            return FileSystemView.getFileSystemView().getSystemIcon(dObj.getFile());
        } else {
            return FileSystemView.getFileSystemView().getSystemIcon(new File(System.getProperty("user.home")));
        }
    }

    public final static Icon getSystemIcon(final File f) {
        if (f.exists()) {
            return FileSystemView.getFileSystemView().getSystemIcon(f);
        } else {
            return FileSystemView.getFileSystemView().getSystemIcon(new File(System.getProperty("user.home")));
        }
    }

    public final static String suffix(final String name) {
        final int i = name.lastIndexOf('.');
        if (i > 0) {
            return name.toLowerCase().substring(i + 1);
        }
        return null;
    }

    public static final String getNumericPaddedString(final int value, final int radix, final int padding) {
        final String str = Integer.toString(value, radix);
        final StringBuffer strBuf = new StringBuffer();

        while ((padding > str.length()) && (strBuf.length() < (padding - str.length()))) {
            strBuf.append("0");
        }

        strBuf.append(str);
        return strBuf.toString();
    }

}
