/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.util.Enumeration;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;

import org.weasis.core.api.Messages;

public abstract class AbstractWizardDialog extends JDialog {

    protected String settingTitle;
    protected AbstractItemDialogPage currentPage = null;
    protected DefaultMutableTreeNode pagesRoot = new DefaultMutableTreeNode("root"); //$NON-NLS-1$
    private final JPanel jPanelRootPanel = new JPanel();
    private final BorderLayout borderLayout3 = new BorderLayout();
    protected final JButton jButtonClose = new JButton();
    private final BorderLayout borderLayout2 = new BorderLayout();
    protected final JTree tree = new JTree();
    protected JPanel jPanelButtom = new JPanel();
    private final JPanel jPanelMain = new JPanel();
    protected JScrollPane jScrollPanePage = new JScrollPane();
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();
    private final JScrollPane jScrollPane1 = new JScrollPane();

    public AbstractWizardDialog(Window window, String title, ModalityType modal, Dimension pageSize) {
        super(window, title, modal);
        this.settingTitle = title;
        try {
            jScrollPanePage.setPreferredSize(pageSize);
            jbInit();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        this.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        jPanelMain.setLayout(borderLayout2);

        jButtonClose.addActionListener(new java.awt.event.ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        });
        jButtonClose.setText(Messages.getString("AbstractWizardDialog.close")); //$NON-NLS-1$

        jPanelRootPanel.setLayout(borderLayout3);
        jPanelButtom.setLayout(gridBagLayout1);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jScrollPane1, jPanelMain);
        jPanelRootPanel.add(splitPane, BorderLayout.CENTER);
        jPanelMain.add(jScrollPanePage, BorderLayout.CENTER);
        jPanelRootPanel.add(jPanelButtom, BorderLayout.SOUTH);
        jPanelButtom.add(jButtonClose, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(10, 10, 10, 15), 0, 0));
        jScrollPane1.setViewportView(tree);

        this.getContentPane().add(jPanelRootPanel, null);
    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            cancel();
        }
        super.processWindowEvent(e);
    }

    protected abstract void initializePages();

    public void showPageFirstPage() {
        if (pagesRoot.getChildCount() > 0) {
            tree.setSelectionRow(0);
        }
    }

    public AbstractItemDialogPage getCurrentPage() {
        Object object = null;
        try {
            object = jScrollPanePage.getViewport().getComponent(0);
        } catch (Exception ex) {
        }
        if (object instanceof AbstractItemDialogPage) {
            return (AbstractItemDialogPage) object;
        }
        return null;
    }

    private void rowslection(AbstractItemDialogPage page) {
        if (page != null) {
            if (currentPage != null) {
                currentPage.deselectPageAction();
            }
            currentPage = page;
            currentPage.selectPageAction();
            jScrollPanePage.setViewportView(currentPage);
        }
    }

    /**
     * iniTree
     */
    protected void iniTree() {

        // fill up tree
        Enumeration children = pagesRoot.children();
        while (children.hasMoreElements()) {
            PageProps[] subpages = null;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) children.nextElement();
            Object object = node.getUserObject();
            if (object instanceof AbstractItemDialogPage) {
                subpages = ((AbstractItemDialogPage) object).getSubPages();
            }

            if (subpages != null) {
                for (int j = 0; j < subpages.length; j++) {
                    node.add(new DefaultMutableTreeNode(subpages[j]));
                }
            }
        }
        DefaultTreeModel model = new DefaultTreeModel(pagesRoot, false);
        tree.setModel(model);
        tree.setShowsRootHandles(true);
        tree.setRootVisible(false);
        tree.setExpandsSelectedPaths(true);
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        renderer.setLeafIcon(null);
        tree.setCellRenderer(renderer);
        tree.addTreeSelectionListener(new TreeSelectionListener() {

            @Override
            public void valueChanged(TreeSelectionEvent e) {
                if (e.getNewLeadSelectionPath() != null) {
                    DefaultMutableTreeNode object =
                        (DefaultMutableTreeNode) e.getNewLeadSelectionPath().getLastPathComponent();
                    if (object.getUserObject() instanceof AbstractItemDialogPage) {
                        rowslection((AbstractItemDialogPage) object.getUserObject());
                    }
                }
            }
        });
        // Dimension dim = tree.getPreferredSize().getSize();
        // dim.width += 5;
        // jScrollPane1.setPreferredSize(dim);

        expandTree(tree, (MutableTreeNode) model.getRoot());
    }

    private static void expandTree(JTree tree, MutableTreeNode start) {
        for (Enumeration children = start.children(); children.hasMoreElements();) {
            DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
            if (!dtm.isLeaf()) {
                //
                TreePath tp = new TreePath(dtm.getPath());
                tree.expandPath(tp);
                //
                expandTree(tree, dtm);
            }
        }
        return;
    }

    public void closeAllPages() {
        Enumeration children = pagesRoot.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode page = (DefaultMutableTreeNode) children.nextElement();
            Object object = page.getUserObject();
            if (object instanceof AbstractItemDialogPage) {
                try {
                    ((AbstractItemDialogPage) object).closeAdditionalWindow();
                } catch (Exception ex) {
                    continue;
                }
            }
        }
    }

    protected void resetAlltoDefault() {
        Enumeration children = pagesRoot.children();
        while (children.hasMoreElements()) {
            PageProps[] subpages = null;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) children.nextElement();
            Object object = node.getUserObject();
            if (object instanceof AbstractItemDialogPage) {
                try {
                    AbstractItemDialogPage page = ((AbstractItemDialogPage) object);
                    subpages = page.getSubPages();
                    if (subpages != null) {
                        for (int j = 0; j < subpages.length; j++) {
                            subpages[j].resetoDefaultValues();
                        }
                    }
                    page.resetoDefaultValues();
                } catch (Exception ex) {
                    continue;
                }
            }
        }
    }

    public abstract void cancel();

}
