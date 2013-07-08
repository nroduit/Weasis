package org.weasis.base.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.util.TitleMenuItem;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;

public class DefaultExplorer extends PluginTool implements DataExplorerView {

    private static final JIExplorerContext treeContext = new JIExplorerContext();

    public static final String BUTTON_NAME = "Explorer";
    public static final String NAME = "Media Explorer";
    public static final String P_LAST_DIR = "last.dir";
    private static final String PREFERENCE_NODE = "view";

    protected FileTreeModel model;
    protected TreePath clickedPath;
    protected final JIThumbnailListPane jilist;

    protected boolean changed;
    private final JTree tree;
    private JPanel jRootPanel = new JPanel();

    public DefaultExplorer(final FileTreeModel model) {
        super(BUTTON_NAME, NAME, POSITION.WEST, ExtendedMode.MINIMIZED, PluginTool.Type.EXPLORER, 10);
        if (model == null) {
            throw new IllegalArgumentException();
        }
        setDockableWidth(400);

        this.tree = new JTree(model);
        this.jilist = new JIThumbnailListPane(model);
        this.changed = false;

        this.model = model;
        tree.putClientProperty("JTree.lineStyle", "Angled");

        final TreeRenderer renderer = new TreeRenderer();
        tree.setCellRenderer(renderer);

        tree.addTreeSelectionListener(new JITreeDiskSelectionAdapter());
        tree.addTreeExpansionListener(new JITreeDiskExpansionAdapter());
        tree.addTreeWillExpandListener(new JITreeDiskWillExpandAdapter());

        tree.addMouseListener(new PopupTrigger());

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setAlignmentX((float) 0.5);
        tree.setShowsRootHandles(false);
        // setTransferHandler(new NodeTransferHandler());
        tree.setDragEnabled(false);

        // gotoLastDirectory();
        JScrollPane treePane = new JScrollPane(tree);
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treePane, jilist);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(200);
        // Provide minimum sizes for the two components in the split pane
        Dimension minimumSize = new Dimension(150, 150);
        treePane.setMinimumSize(minimumSize);
        treePane.setMinimumSize(minimumSize);

        // jRootPanel.setPreferredSize(new Dimension(500, 700));
        jRootPanel.setLayout(new BorderLayout());
        jRootPanel.add(splitPane, BorderLayout.CENTER);
    }

    @Override
    public Component getToolComponent() {
        return jRootPanel;
    }

    protected void iniLastPath() {
        File prefDir = null;
        Preferences prefs =
            BundlePreferences.getDefaultPreferences(FrameworkUtil.getBundle(this.getClass()).getBundleContext());
        if (prefs == null) {
            prefDir = new File(System.getProperty("user.home"));
        } else {
            Preferences p = prefs.node(PREFERENCE_NODE);
            prefDir = new File(p.get(P_LAST_DIR, System.getProperty("user.home")));
        }

        if (prefDir.canRead() && prefDir.isDirectory()) {
            final TreeNode selectedTreeNode = findNodeForDir(prefDir);

            if (selectedTreeNode != null) {
                expandPaths(prefDir);
            }
        }
    }

    protected void saveLastPath() {
        File dir = getCurrentDir();
        if (dir != null) {
            Preferences prefs =
                BundlePreferences.getDefaultPreferences(FrameworkUtil.getBundle(this.getClass()).getBundleContext());
            if (prefs != null) {
                Preferences p = prefs.node(PREFERENCE_NODE);
                BundlePreferences.putStringPreferences(p, P_LAST_DIR, dir.getAbsolutePath());
            }
        }
    }

    public static JIExplorerContext getTreeContext() {
        return treeContext;
    }

    @Override
    public String toString() {
        return NAME;
    }

    public DefaultMutableTreeNode getTreeNode(final TreePath path) {
        return (TreeNode) (path.getLastPathComponent());
    }

    public TreeNode getSelectedNode() {
        final TreePath path = tree.getSelectionPath();
        if (path != null) {
            return (TreeNode) path.getLastPathComponent();
        } else {
            return null;
        }
    }

    public File getCurrentDir() {
        final TreePath path = tree.getSelectionPath();

        if (path != null) {
            return ((TreeNode) path.getLastPathComponent()).getFile();
        }
        return null;
    }

    public DiskFileList getJIList() {
        return this.jilist;
    }

    public void updateSelectionPath(final File file) {
        final TreePath path = tree.getSelectionPath();
        final TreeNode node = (TreeNode) path.getLastPathComponent();
        node.setUserObject(file);
        getTreeModel().nodeStructureChanged(node);
    }

    public void refresh() {
        final TreePath path = tree.getSelectionPath();
        final TreeNode node = (TreeNode) path.getLastPathComponent();
        node.refresh();
        getTreeModel().reload(node);

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setBackground(Color.white);
        setAlignmentX((float) 0.5);
        tree.setShowsRootHandles(false);
    }

    public final TreeNode findChildNode(final TreeNode parentTreeNode, final File selectedSubDir) {
        // log.info("findChildNode " + selectedSubDir.getPath());
        if (!parentTreeNode.isExplored()) {
            parentTreeNode.explore();
        }

        final int count = tree.getModel().getChildCount(parentTreeNode);

        for (int i = 0; i < count; i++) {
            final Object oneChild = tree.getModel().getChild(parentTreeNode, i);

            if (oneChild instanceof TreeNode) {
                final File file = (File) ((TreeNode) oneChild).getUserObject();

                if (file.equals(selectedSubDir)) {
                    return (TreeNode) oneChild;
                }
            }
        }
        return null;
    }

    public final TreeNode findNodeForDir(final File dir) {
        // log.info("findNodeForDir " + dir.getPath());

        TreeNode parentNode = (TreeNode) tree.getModel().getRoot();

        if (!parentNode.isExplored()) {
            parentNode.explore();
        }

        final File parentFile = (File) (parentNode).getUserObject();
        final String dirPath = dir.getAbsolutePath();

        if (parentFile.equals(new File(JIUtility.ROOT_FOLDER))) {
            final int count = tree.getModel().getChildCount(parentNode);

            for (int i = 0; i < count; i++) {
                final Object oneChild = tree.getModel().getChild(parentNode, i);
                final String onePath = ((TreeNode) oneChild).toString();

                if (dirPath.startsWith(onePath)) {
                    parentNode = (TreeNode) oneChild;
                    break;
                }
            }
        } else if (!dirPath.startsWith(parentFile.getAbsolutePath())) {
            return null;
        }

        final Iterator<String> iter = parsePath(dir).iterator();

        boolean pathNotFound = false;
        if (iter.hasNext()) {
            iter.next();

            while (iter.hasNext() && !pathNotFound) {
                if (!parentNode.isExplored()) {
                    parentNode.explore();
                }

                final String nextPath = iter.next();

                pathNotFound = true;
                final int count = tree.getModel().getChildCount(parentNode);

                for (int i = 0; i < count; i++) {
                    final Object oneChild = tree.getModel().getChild(parentNode, i);
                    final String onePath = ((TreeNode) oneChild).toString();

                    if (onePath.equals(nextPath)) {
                        parentNode = (TreeNode) oneChild;
                        pathNotFound = false;
                        break;
                    }
                }
            }

            if (pathNotFound) {
                // log.info("findNodeForDir NULL");
                return null;
            } else {
                // log.info("findNodeForDir " + parentNode);
                return parentNode;
            }
        }
        return null;
    }

    public static final List<String> parsePath(final File selectedDir) {
        // First parse the given directory path into separate path names/fields.
        final List<String> paths = new ArrayList<String>();
        final String selectedAbsPath = selectedDir.getAbsolutePath();
        int beginIndex = 0;
        int endIndex = selectedAbsPath.indexOf(File.separator);

        // For the first path name, attach the path separator.
        // For Windows, it should be like 'C:\', for Unix, it should be like '/'.
        paths.add(selectedAbsPath.substring(beginIndex, endIndex + 1));
        beginIndex = endIndex + 1;
        endIndex = selectedAbsPath.indexOf(File.separator, beginIndex);
        while (endIndex != -1) {
            // For other path names, do not attach the path separator.
            paths.add(selectedAbsPath.substring(beginIndex, endIndex));
            beginIndex = endIndex + 1;
            endIndex = selectedAbsPath.indexOf(File.separator, beginIndex);
        }
        final String lastPath = selectedAbsPath.substring(beginIndex, selectedAbsPath.length());

        if ((lastPath != null) && (lastPath.length() != 0)) {
            paths.add(lastPath);
        }

        return paths;
    }

    public DefaultTreeModel getTreeModel() {
        return this.model;
    }

    public void setTreeModel(final FileTreeModel model) {
        this.model = model;
    }

    final class PopupTrigger extends MouseAdapter {

        @Override
        public void mousePressed(final MouseEvent evt) {
            showPopup(evt);
        }

        @Override
        public void mouseReleased(final MouseEvent evt) {
            showPopup(evt);
        }

        private void showPopup(final MouseEvent evt) {
            // Context menu
            if (SwingUtilities.isRightMouseButton(evt)) {
                JPopupMenu popupMenu = DefaultExplorer.this.buidContexMenu(evt);
                if (popupMenu != null) {
                    popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
                }
            }
        }
    }

    private void jTreeDiskValueChanged(final TreeSelectionEvent e) {

        final TreeNode selectedTreeNode = (TreeNode) getTreeNode(e.getPath());

        // Refresh the address field.
        if (selectedTreeNode != null) {
            final Vector<TreeNode> vec = new Vector<TreeNode>(1);
            vec.add(selectedTreeNode);

            getTreeContext().setSelectedDirNodes(vec, selectedTreeNode);

            // Stop loading of all Icons if in progress
            // JIThumbnailCache.getInstance().setProcessAllIcons(false);

            final File selectedDir = ((TreeNode) tree.getSelectionPath().getLastPathComponent()).getFile();
            if (selectedDir != null) {
                this.jilist.loadDirectory(selectedDir);
            }

        }
    }

    public JPopupMenu buidContexMenu(final MouseEvent e) {

        try {
            JPopupMenu popupMenu = new JPopupMenu();
            TitleMenuItem itemTitle = new TitleMenuItem("Selected Path", popupMenu.getInsets());
            popupMenu.add(itemTitle);
            popupMenu.addSeparator();

            final int x = e.getX();
            final int y = e.getY();
            final TreePath path = tree.getPathForLocation(x, y);
            if (path == null) {
                return null;
            }

            JMenuItem menuItem = new JMenuItem(new AbstractAction(tree.isExpanded(path) ? "Collapse" : "Expand") {

                @Override
                public void actionPerformed(final ActionEvent e) {
                    if (DefaultExplorer.this.clickedPath == null) {
                        return;
                    }
                    if (tree.isExpanded(DefaultExplorer.this.clickedPath)) {
                        tree.collapsePath(DefaultExplorer.this.clickedPath);
                    } else {
                        tree.expandPath(DefaultExplorer.this.clickedPath);
                    }
                }
            });
            popupMenu.add(menuItem);

            menuItem = new JMenuItem(new AbstractAction("Refresh") {

                @Override
                public void actionPerformed(final ActionEvent e) {
                    repaint();
                    refresh();
                }
            });
            popupMenu.add(menuItem);
            popupMenu.addSeparator();

            boolean importAction = false;
            JMenu scan = new JMenu("Import to");
            JMenu scansub = new JMenu("Import subfolders to");

            synchronized (UIManager.EXPLORER_PLUGINS) {
                List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
                for (final DataExplorerView dataExplorerView : explorers) {
                    if (dataExplorerView != DefaultExplorer.this) {
                        importAction = true;
                        JMenuItem item = new JMenuItem(new AbstractAction(dataExplorerView.getUIName()) {

                            @Override
                            public void actionPerformed(ActionEvent e) {

                                final File selectedDir =
                                    ((TreeNode) DefaultExplorer.this.tree.getSelectionPath().getLastPathComponent())
                                        .getFile();
                                if (selectedDir != null) {
                                    dataExplorerView.importFiles(selectedDir.listFiles(), false);
                                }
                            }
                        });
                        scan.add(item);
                        item = new JMenuItem(new AbstractAction(dataExplorerView.getUIName()) {

                            @Override
                            public void actionPerformed(ActionEvent e) {

                                final File selectedDir =
                                    ((TreeNode) DefaultExplorer.this.tree.getSelectionPath().getLastPathComponent())
                                        .getFile();
                                if (selectedDir != null) {
                                    dataExplorerView.importFiles(selectedDir.listFiles(), true);
                                }
                            }
                        });
                        scansub.add(item);
                    }
                }
            }

            if (importAction) {
                popupMenu.add(scan);
                popupMenu.add(scansub);
            }

            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);

            DefaultExplorer.this.clickedPath = path;
            return popupMenu;

        } catch (final Exception exp) {
        } finally {
            e.consume();
        }
        return null;

    }

    /**
     * Expands the tree to the given path.
     */
    public final void expandPaths(final File selectedDir) {

        final TreeNode node = findNodeForDir(selectedDir);
        if (node == null) {
            // log.debug("expandPaths NULL ");
            return;
        }

        final Vector<TreeNode> vec = new Vector<TreeNode>(1);
        vec.add(node);
        getTreeContext().setSelectedDirNodes(vec, 0);

        final TreePath newPath = new TreePath(node.getPath());

        if (!tree.isExpanded(newPath)) {
            tree.expandPath(newPath);
            // log.debug("expandPaths expandPath " + newPath);
        }
        tree.setSelectionPath(newPath);
        tree.scrollPathToVisible(newPath);
    }

    final class JITreeDiskWillExpandAdapter implements javax.swing.event.TreeWillExpandListener {

        @Override
        public void treeWillExpand(final TreeExpansionEvent e) throws ExpandVetoException {
            final TreePath path = e.getPath();

            final TreeNode selectedNode = (TreeNode) path.getLastPathComponent();

            if (!selectedNode.isExplored()) {
                selectedNode.explore();
            }
        }

        @Override
        public void treeWillCollapse(final TreeExpansionEvent e) {
        }
    }

    final class JITreeDiskExpansionAdapter implements javax.swing.event.TreeExpansionListener {

        @Override
        public void treeExpanded(final TreeExpansionEvent e) {
            final Thread runner = new Thread() {

                @Override
                public void run() {
                    Runnable runnable = new Runnable() {

                        @Override
                        public void run() {
                            TreePath path = DefaultExplorer.this.tree.getSelectionPath();
                            if (path != null) {
                                DefaultExplorer.this.tree.setSelectionPath(path);
                            }
                        }
                    };
                    SwingUtilities.invokeLater(runnable);
                }
            };
            runner.start();
        }

        @Override
        public void treeCollapsed(final TreeExpansionEvent e) {
        }
    }

    final class JITreeDiskSelectionAdapter implements javax.swing.event.TreeSelectionListener {

        @Override
        public void valueChanged(final TreeSelectionEvent e) {
            final Thread runner = new Thread() {

                @Override
                public void run() {
                    Runnable runnable = new Runnable() {

                        @Override
                        public void run() {
                            jTreeDiskValueChanged(e);
                        }
                    };
                    SwingUtilities.invokeLater(runnable);
                }
            };
            runner.start();
        }
    }

    public void setSelectedDir(final MediaElement dObj) {
        final Thread runner = new Thread() {

            @Override
            public void run() {
                Runnable runnable = new Runnable() {

                    @Override
                    public void run() {
                        expandPaths(dObj.getFile());
                    }
                };
                SwingUtilities.invokeLater(runnable);
            }
        };
        runner.start();
        runner.setPriority(6);
        return;
    }

    @Override
    public void dispose() {
        super.closeDockable();
    }

    @Override
    public DataExplorerModel getDataExplorerModel() {
        return model;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getDescription() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Icon getIcon() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getUIName() {
        return NAME;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // TODO Auto-generated method stub

    }

    @Override
    public List<Action> getOpenExportDialogAction() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Action> getOpenImportDialogAction() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void importFiles(File[] files, boolean recursive) {

    }

}
