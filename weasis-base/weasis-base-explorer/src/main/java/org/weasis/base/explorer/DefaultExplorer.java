/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.base.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.explorer.list.DiskFileList;
import org.weasis.base.explorer.list.impl.JIThumbnailListPane;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.TitleMenuItem;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;

@SuppressWarnings("serial")
public class DefaultExplorer extends PluginTool implements DataExplorerView {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExplorer.class);

    private static final JIExplorerContext treeContext = new JIExplorerContext();

    public static final String BUTTON_NAME = "Explorer"; //$NON-NLS-1$
    public static final String NAME = Messages.getString("DefaultExplorer.name"); //$NON-NLS-1$
    public static final String P_LAST_DIR = "default.explorer.last.dir"; //$NON-NLS-1$
    private static final String PREFERENCE_NODE = "view"; //$NON-NLS-1$

    protected FileTreeModel model;
    protected TreePath clickedPath;
    protected final JIThumbnailListPane<?> jilist;

    protected boolean changed;
    private final JTree tree;
    private JPanel jRootPanel = new JPanel();

    public DefaultExplorer(final FileTreeModel model, JIThumbnailCache thumbCache) {
        super(BUTTON_NAME, NAME, POSITION.WEST, ExtendedMode.NORMALIZED, PluginTool.Type.EXPLORER, 10);
        if (model == null) {
            throw new IllegalArgumentException();
        }
        setDockableWidth(400);

        this.tree = new JTree(model);
        this.jilist = new JIThumbnailListPane<>(thumbCache);
        this.changed = false;

        this.model = model;
        tree.putClientProperty("JTree.lineStyle", "Angled"); //$NON-NLS-1$ //$NON-NLS-2$

        final TreeRenderer renderer = new TreeRenderer();
        tree.setCellRenderer(renderer);

        tree.addTreeSelectionListener(new JITreeDiskSelectionAdapter());
        tree.addTreeExpansionListener(new JITreeDiskExpansionAdapter());
        tree.addTreeWillExpandListener(new JITreeDiskWillExpandAdapter());

        tree.addMouseListener(new PopupTrigger());

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setAlignmentX((float) 0.5);
        tree.setShowsRootHandles(false);
        tree.setDragEnabled(false);

        JScrollPane treePane = new JScrollPane(tree);
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treePane, jilist);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(200);
        // Provide minimum sizes for the two components in the split pane
        Dimension minimumSize = new Dimension(150, 150);
        treePane.setMinimumSize(minimumSize);
        treePane.setMinimumSize(minimumSize);

        jRootPanel.setLayout(new BorderLayout());
        jRootPanel.add(splitPane, BorderLayout.CENTER);
    }

    @Override
    public Component getToolComponent() {
        return jRootPanel;
    }

    protected void iniLastPath() {
        Path prefDir = null;
        try {
            prefDir = Paths.get(BundleTools.LOCAL_UI_PERSISTENCE.getProperty(P_LAST_DIR));
        } catch (InvalidPathException e) {
            LOGGER.error("Get last dir path", e); //$NON-NLS-1$
        }

        if (prefDir == null) {
            prefDir = Paths.get(System.getProperty("user.home")); //$NON-NLS-1$
        }

        if (Files.isReadable(prefDir) && prefDir.toFile().isDirectory()) {
            final TreeNode selectedTreeNode = findNode(prefDir);

            if (selectedTreeNode != null) {
                expandPaths(prefDir);
            }
        }
    }

    protected void saveLastPath() {
        Path dir = getCurrentDir();
        if (dir != null && Files.isReadable(dir)) {
            BundleTools.LOCAL_UI_PERSISTENCE.setProperty(P_LAST_DIR, dir.toString());
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

    public Path getCurrentDir() {
        final TreePath path = tree.getSelectionPath();
        if (path != null) {
            return ((TreeNode) path.getLastPathComponent()).getNodePath();
        }
        return null;
    }

    public DiskFileList getJIList() {
        return this.jilist;
    }

    public void updateSelectionPath(Path path) {
        final TreePath treePath = tree.getSelectionPath();
        final TreeNode node = (TreeNode) treePath.getLastPathComponent();
        if (node != null) {
            node.setNodePath(path);
            getTreeModel().nodeStructureChanged(node);
        }
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

    public final TreeNode findNode(Path dir) {
        TreeNode parentNode = (TreeNode) tree.getModel().getRoot();

        if (!parentNode.isExplored()) {
            parentNode.explore();
        }

        final Path parentFile = parentNode.getNodePath();

        if (parentFile.equals(Paths.get(JIUtility.ROOT_FOLDER))) {
            final int count = tree.getModel().getChildCount(parentNode);

            for (int i = 0; i < count; i++) {
                final Object oneChild = tree.getModel().getChild(parentNode, i);
                final Path onePath = ((TreeNode) oneChild).getNodePath();

                if (dir.startsWith(onePath)) {
                    parentNode = (TreeNode) oneChild;
                    break;
                }
            }
        } else if (!dir.startsWith(parentFile)) {
            return null;
        }

        final Iterator<Path> iter = dir.iterator();
        while (iter.hasNext()) {
            if (!parentNode.isExplored()) {
                parentNode.explore();
            }

            iter.next();
            final int count = tree.getModel().getChildCount(parentNode);

            for (int i = 0; i < count; i++) {
                final Object oneChild = tree.getModel().getChild(parentNode, i);
                final Path onePath = ((TreeNode) oneChild).getNodePath();

                if (dir.startsWith(onePath)) {
                    if (dir.equals(onePath)) {
                        return (TreeNode) oneChild;
                    }
                    parentNode = (TreeNode) oneChild;
                    break;
                }
            }
        }
        return null;
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

        // Refresh
        if (selectedTreeNode != null) {
            final ArrayList<TreeNode> vec = new ArrayList<>(1);
            vec.add(selectedTreeNode);

            getTreeContext().setSelectedDirNodes(vec, selectedTreeNode);

            final Path selectedDir = ((TreeNode) tree.getSelectionPath().getLastPathComponent()).getNodePath();
            if (selectedDir != null) {
                this.jilist.loadDirectory(selectedDir);
            }

        }
    }

    public JPopupMenu buidContexMenu(final MouseEvent e) {

        try {
            JPopupMenu popupMenu = new JPopupMenu();
            TitleMenuItem itemTitle =
                new TitleMenuItem(Messages.getString("DefaultExplorer.sel_path"), popupMenu.getInsets()); //$NON-NLS-1$
            popupMenu.add(itemTitle);
            popupMenu.addSeparator();

            final int x = e.getX();
            final int y = e.getY();
            final TreePath path = tree.getPathForLocation(x, y);
            if (path == null) {
                return null;
            }

            JMenuItem menuItem =
                new JMenuItem(new DefaultAction(tree.isExpanded(path) ? Messages.getString("DefaultExplorer.collapse") //$NON-NLS-1$
                    : Messages.getString("DefaultExplorer.expand"), event -> { //$NON-NLS-1$
                        if (DefaultExplorer.this.clickedPath == null) {
                            return;
                        }
                        if (tree.isExpanded(DefaultExplorer.this.clickedPath)) {
                            tree.collapsePath(DefaultExplorer.this.clickedPath);
                        } else {
                            tree.expandPath(DefaultExplorer.this.clickedPath);
                        }
                    }));
            popupMenu.add(menuItem);

            menuItem = new JMenuItem(new DefaultAction(Messages.getString("DefaultExplorer.refresh"), event -> { //$NON-NLS-1$
                repaint();
                refresh();
            }));
            popupMenu.add(menuItem);
            popupMenu.addSeparator();

            boolean importAction = false;
            JMenu scan = new JMenu(Messages.getString("DefaultExplorer.import_to")); //$NON-NLS-1$
            JMenu scansub = new JMenu(Messages.getString("DefaultExplorer.import_sub")); //$NON-NLS-1$

            synchronized (UIManager.EXPLORER_PLUGINS) {
                for (final DataExplorerView dataExplorerView : UIManager.EXPLORER_PLUGINS) {
                    if (dataExplorerView != DefaultExplorer.this) {
                        importAction = true;
                        JMenuItem item = new JMenuItem(new DefaultAction(dataExplorerView.getUIName(), event -> {
                            final Path selectedDir =
                                ((TreeNode) DefaultExplorer.this.tree.getSelectionPath().getLastPathComponent())
                                    .getNodePath();
                            if (selectedDir != null) {
                                dataExplorerView.importFiles(selectedDir.toFile().listFiles(), false);
                            }
                        }));
                        scan.add(item);
                        item = new JMenuItem(new DefaultAction(dataExplorerView.getUIName(), event -> {
                            final Path selectedDir =
                                ((TreeNode) DefaultExplorer.this.tree.getSelectionPath().getLastPathComponent())
                                    .getNodePath();
                            if (selectedDir != null) {
                                dataExplorerView.importFiles(selectedDir.toFile().listFiles(), true);
                            }
                        }));
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
    public final void expandPaths(Path selectedDir) {

        final TreeNode node = findNode(selectedDir);
        if (node == null) {
            return;
        }

        final ArrayList<TreeNode> list = new ArrayList<>(1);
        list.add(node);
        getTreeContext().setSelectedDirNodes(list, 0);

        final TreePath newPath = new TreePath(node.getPath());

        if (!tree.isExpanded(newPath)) {
            tree.expandPath(newPath);
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
            // Do nothing
        }
    }

    final class JITreeDiskExpansionAdapter implements javax.swing.event.TreeExpansionListener {

        @Override
        public void treeExpanded(final TreeExpansionEvent e) {
            final Thread runner = new Thread() {

                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> Optional.ofNullable(DefaultExplorer.this.tree.getSelectionPath())
                        .ifPresent(DefaultExplorer.this.tree::setSelectionPath));
                }
            };
            runner.start();
        }

        @Override
        public void treeCollapsed(final TreeExpansionEvent e) {
            // Do nothing
        }
    }

    final class JITreeDiskSelectionAdapter implements javax.swing.event.TreeSelectionListener {

        @Override
        public void valueChanged(final TreeSelectionEvent e) {
            final Thread runner = new Thread() {

                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> jTreeDiskValueChanged(e));
                }
            };
            runner.start();
        }
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
        // Do nothing
    }

    @Override
    public String getDescription() {
        return NAME;
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getUIName() {
        return NAME;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
    }

    @Override
    public List<Action> getOpenExportDialogAction() {
        return Collections.emptyList();
    }

    @Override
    public List<Action> getOpenImportDialogAction() {
        return Collections.emptyList();
    }

    @Override
    public void importFiles(File[] files, boolean recursive) {
        // Do no import external files
    }

    @Override
    public boolean canImportFiles() {
        return false;
    }
}
