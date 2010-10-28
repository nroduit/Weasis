package org.weasis.base.explorer;

import java.io.File;
import java.util.Arrays;

import javax.swing.tree.DefaultMutableTreeNode;

public class TreeNode extends DefaultMutableTreeNode {

    private static final long serialVersionUID = -4938264429078819992L;

    private boolean explored = false;
    private boolean root = false;

    public TreeNode(final File file) {
        setUserObject(file);
    }

    @Override
    public boolean getAllowsChildren() {
        return isDirectory();
    }

    @Override
    public boolean isLeaf() {
        if (!this.explored) {
            return false;
        }
        if ((this.children != null) && (this.children.size() > 0)) {
            return false;
        }
        return true;
    }

    public File getFile() {
        final Object obj = getUserObject();
        if (obj instanceof String) {
            final File file = new File((File) ((TreeNode) this.parent).getUserObject(), (String) obj);
            setUserObject(file);
        }
        return (File) getUserObject();
    }

    public boolean isExplored() {
        return this.explored;
    }

    public boolean isDirectory() {
        final File file = getFile();

        return file.isDirectory();
    }

    public void refresh() {
        this.explored = false;
        this.removeAllChildren();
        explore();
    }

    public void explore() {
        final File file = getFile();

        if (!isDirectory()) {
            return;
        }

        if (!isExplored()) {
            File[] files = null;
            if (isRoot()) {
                files = JIUtility.getRoots();
            } else {
                files = file.listFiles(new FolderFilter());
                if (files != null) {
                    Arrays.sort(files, new NameSortingComparator<File>());
                }
            }
            if (files != null) {
                for (final File element : files) {
                    add(new TreeNode(element));
                }
            }
            this.explored = true;
        }
    }

    @Override
    public String toString() {
        final File file = getFile();
        return file.getName().length() > 0 ? file.getName() : file.getPath();
    }

    /**
     * Gets size of this file object.
     */
    public long getSize() {
        final File file = getFile();

        if (!file.canRead()) {
            return 0;
        }
        if (!isDirectory()) {
            return (file.length());
        }

        final File[] children = file.listFiles();

        long size = 0;
        if (children != null) {
            for (final File element : children) {
                size += element.length();
            }
        }
        return size;
    }

    /**
     * @return the root
     */
    @Override
    public final boolean isRoot() {
        return this.root;
    }

    /**
     * @param root
     *            the root to set
     */
    public final void setRoot(final boolean root) {
        this.root = root;
    }
}
