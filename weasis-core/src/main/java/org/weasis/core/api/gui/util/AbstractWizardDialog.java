/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableUtil;

public abstract class AbstractWizardDialog extends JDialog {

  public static final int HORIZONTAL_GAP = 15;
  public static final int VERTICAL_GAP = 10;

  protected String settingTitle;
  protected AbstractItemDialogPage currentPage = null;
  protected DefaultMutableTreeNode pagesRoot = new DefaultMutableTreeNode("root"); // NON-NLS
  private final JPanel jPanelRootPanel = new JPanel();
  protected final JButton jButtonClose =
      new JButton(Messages.getString("AbstractWizardDialog.close"));
  protected final JTree tree = new JTree();
  protected final JPanel jPanelBottom = new JPanel();
  private final JPanel jPanelMain = new JPanel();
  protected JScrollPane jScrollPanePage = new JScrollPane();
  private final JScrollPane jScrollPane1 = new JScrollPane();

  protected AbstractWizardDialog(
      Window window, String title, ModalityType modal, Dimension pageSize) {
    super(window, title, modal);
    this.settingTitle = title;
    jScrollPanePage.setPreferredSize(GuiUtils.getDimension(pageSize.width, pageSize.height));
    jScrollPanePage.setBorder(BorderFactory.createEmptyBorder()); // remove default line
    jbInit();
  }

  private void jbInit() {
    this.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
    jPanelMain.setLayout(new BorderLayout());
    jPanelMain.add(jScrollPanePage, BorderLayout.CENTER);

    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jScrollPane1, jPanelMain);
    jPanelRootPanel.setLayout(new BorderLayout());
    jPanelRootPanel.add(splitPane, BorderLayout.CENTER);

    jPanelBottom.setLayout(new BoxLayout(jPanelBottom, BoxLayout.Y_AXIS));
    jPanelBottom.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING, HORIZONTAL_GAP, VERTICAL_GAP, jButtonClose));
    jButtonClose.addActionListener(e -> cancel());
    jPanelRootPanel.add(jPanelBottom, BorderLayout.SOUTH);

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

  public void showFirstPage() {
    if (pagesRoot.getChildCount() > 0) {
      tree.setSelectionRow(0);
    }
  }

  public void showPage(String title) {
    if (!selectPage(title, pagesRoot)) {
      showFirstPage();
    }
  }

  private boolean selectPage(String title, DefaultMutableTreeNode root) {
    if (title != null) {
      Enumeration<?> children = root.children();
      while (children.hasMoreElements()) {
        Object child = children.nextElement();
        if (child instanceof DefaultMutableTreeNode dtm) {
          Object object = dtm.getUserObject();
          if (object instanceof PageItem page && page.getTitle().equals(title)) {
            TreePath tp = new TreePath(dtm.getPath());
            if (!dtm.isLeaf()) {
              tree.expandPath(tp);
            }
            tree.setSelectionPath(tp);
            return true;
          }
          if (dtm.getChildCount() > 0 && selectPage(title, dtm)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public AbstractItemDialogPage getCurrentPage() {
    Object object = null;
    JViewport viewPort = jScrollPanePage.getViewport();
    if (viewPort != null && viewPort.getComponentCount() > 0) {
      object = viewPort.getComponent(0);
    }

    if (object instanceof AbstractItemDialogPage page) {
      return page;
    }
    return null;
  }

  protected void selectPage(AbstractItemDialogPage page) {
    if (page != null) {
      if (currentPage != null) {
        currentPage.deselectPageAction();
      }
      currentPage = page;
      currentPage.selectPageAction();
      jScrollPanePage.setViewportView(currentPage);
    }
  }

  /** iniTree */
  protected void iniTree() {
    // fill up tree
    Enumeration<?> children = pagesRoot.children();
    while (children.hasMoreElements()) {
      Object child = children.nextElement();
      if (child instanceof DefaultMutableTreeNode node) {
        iniSubpages(node);
      }
    }
    DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
    renderer.setLeafIcon(null);
    renderer.setClosedIcon(null);
    renderer.setOpenIcon(null);
    DefaultTreeModel model = new DefaultTreeModel(pagesRoot, false);
    tree.setModel(model);
    tree.setShowsRootHandles(true);
    tree.setRootVisible(false);
    tree.setExpandsSelectedPaths(true);
    tree.addTreeSelectionListener(
        e -> {
          if (e.getNewLeadSelectionPath() != null) {
            DefaultMutableTreeNode object =
                (DefaultMutableTreeNode) e.getNewLeadSelectionPath().getLastPathComponent();
            if (object.getUserObject() instanceof AbstractItemDialogPage page) {
              selectPage(page);
            }
          }
        });
    expandTree(tree, pagesRoot, 2);
  }

  protected void iniSubpages(DefaultMutableTreeNode node) {
    List<PageItem> subpages = null;

    Object object = node.getUserObject();
    if (object instanceof Insertable) {
      subpages = ((AbstractItemDialogPage) object).getSubPages();
    }

    if (subpages != null) {
      List<Insertable> list = new ArrayList<>();
      for (PageItem pageItem : subpages) {
        if (pageItem instanceof Insertable insertable) {
          list.add(insertable);
        }
      }
      InsertableUtil.sortInsertable(list);
      for (PageItem subpage : subpages) {
        DefaultMutableTreeNode subNode = new DefaultMutableTreeNode(subpage);
        node.add(subNode);
        iniSubpages(subNode);
      }
    }
  }

  public PageItem getSelectedPage() {
    DefaultMutableTreeNode selectedNode =
        (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
    if (selectedNode != null) {
      Object object = selectedNode.getUserObject();
      if (object instanceof PageItem page) {
        return page;
      }
    }
    return null;
  }

  public static void expandTree(JTree tree, DefaultMutableTreeNode start, int maxDeep) {
    if (maxDeep > 1) {
      Enumeration<?> children = start.children();
      while (children.hasMoreElements()) {
        Object child = children.nextElement();
        if (child instanceof DefaultMutableTreeNode dtm && !dtm.isLeaf()) {
          TreePath tp = new TreePath(dtm.getPath());
          tree.expandPath(tp);

          expandTree(tree, dtm, maxDeep - 1);
        }
      }
    }
  }

  public void closeAllPages() {
    Enumeration<?> children = pagesRoot.children();
    while (children.hasMoreElements()) {
      Object child = children.nextElement();
      if (child instanceof DefaultMutableTreeNode treeNode) {
        Object object = treeNode.getUserObject();
        if (object instanceof AbstractItemDialogPage page) {
          page.closeAdditionalWindow();
        }
      }
    }
  }

  protected void resetAllToDefault() {
    Enumeration<?> children = pagesRoot.children();
    while (children.hasMoreElements()) {
      Object child = children.nextElement();
      if (child instanceof DefaultMutableTreeNode node) {
        Object object = node.getUserObject();
        if (object instanceof AbstractItemDialogPage page) {
          for (PageItem subpage : page.getSubPages()) {
            subpage.resetToDefaultValues();
          }
          page.resetToDefaultValues();
        }
      }
    }
  }

  public abstract void cancel();
}
