package org.weasis.base.explorer;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;

public class DefaultExplorer extends PluginTool implements DataExplorerView {

    private static final JIExplorerContext treeContext = new JIExplorerContext();
    /**
	 *
	 */
    private static final long serialVersionUID = 2844546991944685813L;

    public static final String BUTTON_NAME = "Explorer";
    public static final String NAME = "Media Explorer";
    public static final String P_LAST_DIR = "last.dir";
    private static final String PREFERENCE_NODE = "view";

    protected FileTreeModel model;

    protected TreePath clickedPath;

    protected JPopupMenu popup;
    protected JMenuItem jMenuItemExpand;
    protected JMenuItem jMenuItemRefresh;

    protected final JMenu scan;

    protected DiskFileList jilist;

    protected boolean changed;
    private final JTree tree;

    public DefaultExplorer() {
        this(JIUtility.createTreeModel());
    }

    public DefaultExplorer(final FileTreeModel model) {
        super(BUTTON_NAME, NAME, ToolWindowAnchor.LEFT, PluginTool.TYPE.explorer);
        setDockableWidth(300);
        scan = new JMenu("Import to");
        this.tree = new JTree(model);

        this.changed = false;

        this.model = model;
        tree.putClientProperty("JTree.lineStyle", "Angled");

        final TreeRenderer renderer = new TreeRenderer();
        tree.setCellRenderer(renderer);

        tree.addTreeSelectionListener(new JITreeDiskSelectionAdapter());
        tree.addTreeExpansionListener(new JITreeDiskExpansionAdapter());
        tree.addTreeWillExpandListener(new JITreeDiskWillExpandAdapter());

        initPopMenu();

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setAlignmentX((float) 0.5);
        tree.setShowsRootHandles(false);
        // setTransferHandler(new NodeTransferHandler());
        tree.setDragEnabled(false);

        // gotoLastDirectory();
    }

    @Override
    public Component getToolComponent() {
        return new JScrollPane(tree);
    }

    protected void activate(ComponentContext context) {
        File prefDir = null;
        Preferences prefs = BundlePreferences.getDefaultPreferences(context.getBundleContext());
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

    protected void deactivate(ComponentContext context) {
        File dir = getCurrentDir();
        if (dir != null) {
            Preferences prefs = BundlePreferences.getDefaultPreferences(context.getBundleContext());
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

    public void initPopMenu() {
        this.popup = new JPopupMenu();

        this.jMenuItemExpand = new JMenuItem();
        this.jMenuItemExpand.setText("Expand");
        this.jMenuItemExpand.setAction(new AbstractAction() {

            /**
			 *
			 */
            private static final long serialVersionUID = 2975977946216576290L;

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
        this.popup.add(this.jMenuItemExpand);

        this.jMenuItemRefresh = new JMenuItem();
        this.jMenuItemRefresh.setText("Refresh");
        this.jMenuItemRefresh.setAction(new AbstractAction("Refresh", null) {

            /**
			 *
			 */
            private static final long serialVersionUID = 1118192487617852891L;

            @Override
            public void actionPerformed(final ActionEvent e) {
                repaint();
                refresh();
            }
        });
        tree.addMouseListener(new PopupTrigger());
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

    public void setJIList(final DiskFileList jilist) {
        this.jilist = jilist;
    }

    public DiskFileList getJIList() {
        if (jilist == null) {
            jilist = new JIThumbnailListPane(model);
        }
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

    public TreePath getClickedPath() {
        return this.clickedPath;
    }

    public void setClickedPath(final TreePath clickedPath) {
        this.clickedPath = clickedPath;
    }

    public DefaultTreeModel getTreeModel() {
        return this.model;
    }

    public void setTreeModel(final FileTreeModel model) {
        this.model = model;
    }

    public JPopupMenu getPopup() {
        return this.popup;
    }

    public void setPopup(final JPopupMenu popup) {
        this.popup = popup;
    }

    class PopupTrigger extends MouseAdapter {

        @Override
        public void mouseReleased(final MouseEvent e) {
            if (e.isPopupTrigger() || (e.getButton() == MouseEvent.BUTTON3)) {
                final int x = e.getX();
                final int y = e.getY();
                final TreePath path = tree.getPathForLocation(x, y);
                if (path == null) {
                    return;
                }

                DefaultExplorer.this.popup.removeAll();
                if (tree.isExpanded(path)) {
                    DefaultExplorer.this.jMenuItemExpand.setText("Collapse");
                } else {
                    DefaultExplorer.this.jMenuItemExpand.setText("Expand");
                }
                DefaultExplorer.this.popup.add(DefaultExplorer.this.jMenuItemExpand);
                DefaultExplorer.this.popup.add(DefaultExplorer.this.jMenuItemRefresh);
                DefaultExplorer.this.popup.addSeparator();

                // if (!getTreeContext().isStatusBarProgressTaskRunning()) {
                synchronized (UIManager.EXPLORER_PLUGINS) {
                    List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
                    for (final DataExplorerView dataExplorerView : explorers) {
                        if (dataExplorerView != DefaultExplorer.this) {
                            scan.removeAll();
                            JMenuItem item = new JMenuItem(dataExplorerView.getUIName());
                            scan.add(item);
                            item.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    // TODO improve that
                                    DataExplorerModel m = dataExplorerView.getDataExplorerModel();
                                    // if (m instanceof Command) {
                                    // final File selectedDir = ((JITreeNode)
                                    // DefaultExplorer.this.tree.getSelectionPath()
                                    // .getLastPathComponent()).getFile();
                                    // if (selectedDir != null) {
                                    // ((Command) m).execute(" -l \"" + selectedDir + "\"", null, null);
                                    // }
                                    // }
                                }
                            });
                        }
                    }
                }

                DefaultExplorer.this.popup.add(DefaultExplorer.this.scan);

                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                DefaultExplorer.this.popup.show(DefaultExplorer.this.tree, x, y);
                DefaultExplorer.this.clickedPath = path;
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
            // TODO improve, if null
            getJIList();
            if (jilist instanceof ViewerPlugin) {
                ((ViewerPlugin) jilist).setPluginName(selectedDir.toString());
                // if the view has been closed
                openThumbnailsListView();
                this.jilist.loadDirectory(selectedDir);
            }
        }
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
        // TODO Auto-generated method stub

    }

    public void openThumbnailsListView() {

        if (jilist == null) {
            jilist = new JIThumbnailListPane(model);

            File selectedDir = getCurrentDir();
            if (selectedDir != null) {
                expandPaths(selectedDir);
                ((ViewerPlugin) jilist).setPluginName(selectedDir.toString());
                // if the view has been closed
                openThumbnailsListView();
                this.jilist.loadDirectory(selectedDir);
            }
        }
        DataExplorerModel m = getDataExplorerModel();
        m.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Register, m, null, jilist));

        // TabbedDockableContainer tabContainer = DockingUtilities.findTabbedDockableContainer(pluginPane);
        // if (tabContainer != null) {
        // tabContainer.setSelectedDockable(pluginPane);
        // }

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
    protected void changeToolWindowAnchor(ToolWindowAnchor anchor) {
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

}
