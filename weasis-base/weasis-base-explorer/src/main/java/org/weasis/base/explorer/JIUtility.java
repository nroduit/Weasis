package org.weasis.base.explorer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;

import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.MediaElement;

public final class JIUtility {

    private static final File USER_HOME = new File(System.getProperty("user.home"));
    public static final String ROOT_FOLDER;

    static {
        if (AppProperties.OPERATING_SYSTEM.startsWith("win")) { //$NON-NLS-1$
            ROOT_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + "rootFolder"; //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            ROOT_FOLDER = File.separator;
        }
    }

    private JIUtility() {
    }

    public static FileTreeModel createTreeModel() {
        Path rootPath;
        if (AppProperties.OPERATING_SYSTEM.startsWith("win")) { //$NON-NLS-1$
            final File winRootFoler = new File(ROOT_FOLDER);
            winRootFoler.mkdirs();
            winRootFoler.deleteOnExit();
            rootPath = winRootFoler.toPath();
        } else {
            rootPath = Paths.get(ROOT_FOLDER);
        }

        TreeNode rootNode = new TreeNode(rootPath);
        rootNode.setRoot(true);
        rootNode.explore();

        return new FileTreeModel(rootNode);
    }

    public static Icon getSystemIcon(final MediaElement dObj) {
        return getSystemIcon(dObj.getFileCache().getOriginalFile().get());
    }

    public static Icon getSystemIcon(final File f) {
        if (f != null && f.exists()) {
            return FileSystemView.getFileSystemView().getSystemIcon(f);
        } else {
            return FileSystemView.getFileSystemView().getSystemIcon(USER_HOME); // $NON-NLS-1$
        }
    }

}
