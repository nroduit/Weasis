/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import com.formdev.flatlaf.icons.FlatSearchIcon;
import com.formdev.flatlaf.util.UIScale;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import net.miginfocom.swing.MigLayout;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.PreferencesPageFactory.PageDescriptor;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.util.StringUtil;

/**
 * The preferences of the application: a tree of pages with a search field on one side, the selected
 * page on the other.
 *
 * <p>A page is built when it is first shown, so the dialog opens without building the thirty of
 * them; the first search builds the remaining ones, to look into their labels. What belongs to the
 * page shown (its title, scope, help, <i>Restore default values</i> and <i>Apply</i>) sits around
 * the page; what belongs to all the settings (the settings packages, the configuration files,
 * <i>Close</i>) sits in the bar under the dialog. Closing saves every page that was built.
 */
public class PreferenceDialog extends JDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(PreferenceDialog.class);

  public static final String KEY_SHOW_APPLY = "show.apply";
  public static final String KEY_SHOW_RESTORE = "show.restore";
  public static final String KEY_HELP = "help.item";

  private static final Dimension PAGE_SIZE = new Dimension(720, 570);
  private static final int TREE_WIDTH = 210;
  private static final String BREADCRUMB = "  ›  ";

  // The page and the size of the last dialog of this session
  private static String lastPage;
  private static Dimension lastSize;

  /** A page of the tree, known by its title and place; the page itself is built on demand. */
  static final class PageNode {
    private final String title;
    private final int position;
    private final Supplier<AbstractItemDialogPage> builder;
    private final List<PageNode> children = new ArrayList<>();
    private PageNode parent;
    private AbstractItemDialogPage page;
    private boolean failed;
    private String index;

    PageNode(String title, int position, Supplier<AbstractItemDialogPage> builder) {
      this.title = title == null ? "" : title;
      this.position = position;
      this.builder = builder;
    }

    PageNode add(PageNode child) {
      child.parent = this;
      children.add(child);
      return this;
    }

    String title() {
      return title;
    }

    List<PageNode> children() {
      return children;
    }

    boolean isBuilt() {
      return page != null;
    }

    /** The page, built by the first call; null when it cannot be built. */
    AbstractItemDialogPage page() {
      if (page == null && !failed) {
        try {
          page = builder.get();
        } catch (RuntimeException | LinkageError e) {
          LOGGER.error("Cannot build the preference page {}", title, e);
        }
        failed = page == null;
        if (page != null && !title.equals(page.getTitle())) {
          // showPage(title) of the callers must find the node of the page
          LOGGER.warn("Preference page described as {} is titled {}", title, page.getTitle());
        }
      }
      return page;
    }

    /** The title of the page with those of its parents: "General › Monitors". */
    String path() {
      return parent == null ? title : parent.path() + BREADCRUMB + title;
    }

    // What a search looks into: the title and, once the page is built, the texts it shows
    String index() {
      if (index == null) {
        StringBuilder text = new StringBuilder(title);
        if (page == null) {
          return text.toString().toLowerCase(Locale.ROOT);
        }
        collectTexts(page, text);
        index = text.toString().toLowerCase(Locale.ROOT);
      }
      return index;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  /**
   * What the scroll pane shows: the page as wide as the dialog, so that its texts wrap and its
   * fields stretch, and scrolled sideways only when the dialog is narrower than the page can be.
   */
  private static final class PageHolder extends JPanel implements Scrollable {
    PageHolder() {
      super(new BorderLayout());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
      return UIScale.scale(16);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
      return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return getParent() == null || getParent().getWidth() >= getMinimumSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }
  }

  private final List<PageNode> roots = new ArrayList<>();
  private PageNode currentNode;
  private boolean filtering;

  private final JTextField searchField = new JTextField();
  private final JTree tree = new JTree();
  private final JLabel titleLabel = new JLabel();
  private final JLabel scopeLabel = new JLabel();
  private final JButton helpButton = new JButton();
  private final JPanel linksPanel = new JPanel(new MigLayout("insets 0, wrap 2", "[]unrel[]"));
  private final PageHolder pageHolder = new PageHolder();
  private final JScrollPane pageScroll = new JScrollPane(pageHolder);
  private final JButton restoreButton = new JButton(Messages.getString("restore.values"));
  private final JButton applyButton = new JButton(Messages.common("apply"));
  private final JPanel pageBar =
      new JPanel(new MigLayout("insets 5lp 15lp 5lp 15lp, fillx", "push[]rel[]"));

  public PreferenceDialog(Window parentWin) {
    super(
        parentWin,
        Messages.getString("OpenPreferencesAction.title"),
        ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
    buildPages();
    buildInterface();
    fillTree("");
    pack();
    if (lastSize != null) {
      setSize(lastSize);
    }
    if (lastPage == null || !selectPage(lastPage)) {
      showFirstPage();
    }
  }

  // ---- The tree of pages --------------------------------------------------------------------

  private void buildPages() {
    PageNode general = new PageNode(GeneralSetting.PAGE_NAME, 100, GeneralSetting::new);
    general
        .add(new PageNode(LanguageSetting.PAGE_NAME, 101, LanguageSetting::new))
        .add(new PageNode(ThemeSetting.PAGE_NAME, 102, ThemeSetting::new))
        .add(new PageNode(Messages.getString("shortcuts"), 105, ShortcutPrefView::new))
        .add(new PageNode(Messages.getString("ScreenPrefView.monitors"), 108, ScreenPrefView::new))
        .add(new PageNode(Messages.getString("ProxyPrefView.proxy"), 110, ProxyPrefView::new))
        .add(new PageNode(Messages.getString("MaskingPrefView.title"), 112, MaskingPrefView::new))
        .add(new PageNode(LoggingPrefView.PAGE_NAME, 115, LoggingPrefView::new))
        .add(new PageNode(LauncherPrefView.PAGE_NAME, 120, LauncherPrefView::new))
        .add(new PageNode(CommandsPrefView.PAGE_NAME, 125, CommandsPrefView::new));
    PageNode viewer = new PageNode(Messages.getString("viewer"), 500, ViewerPrefView::new);
    PageNode dicom = new PageNode("DICOM", 600, DicomPrefView::new); // NON-NLS
    PageNode draw = new PageNode(MeasureTool.BUTTON_NAME, 700, DrawPrefView::new);
    draw.add(new PageNode(GraphicPrefView.PAGE_NAME, 702, GraphicPrefView::new))
        .add(new PageNode(Messages.getString("ProfilePrefView.title"), 704, ProfilePrefView::new))
        .add(new PageNode(MeasureTool.LABEL_PREF_NAME, 710, LabelsPrefView::new));
    roots.add(general);
    roots.add(new PageNode(PluginsPrefView.PAGE_NAME, 130, PluginsPrefView::new));
    roots.add(viewer);
    roots.add(dicom);
    roots.add(draw);

    addPluginPages(general, viewer, dicom, draw);

    Comparator<PageNode> byPosition = Comparator.comparingInt(n -> n.position);
    roots.sort(byPosition);
    roots.forEach(root -> root.children.sort(byPosition));
  }

  // A plugin page goes under the category its position names; a factory that describes its page
  // lets it be built when shown, another one builds it now
  private void addPluginPages(PageNode general, PageNode viewer, PageNode dicom, PageNode draw) {
    PluginManager pluginManager = GuiUtils.getUICore().getPluginManager();
    if (pluginManager == null) {
      return;
    }
    Map<String, Object> properties = new HashMap<>();
    properties.put("weasis.user.prefs", System.getProperty("weasis.user.prefs", "user")); // NON-NLS
    try {
      for (PreferencesPageFactory factory :
          pluginManager.getExtensions(PreferencesPageFactory.class)) {
        if (factory == null || !isEnabled(factory)) {
          continue;
        }
        PageNode node;
        PageDescriptor descriptor = factory.describe();
        if (descriptor != null) {
          node =
              new PageNode(
                  descriptor.title(),
                  descriptor.position(),
                  () -> factory.createInstance(properties));
        } else {
          AbstractItemDialogPage page = factory.createInstance(properties);
          if (page == null) {
            continue;
          }
          node = new PageNode(page.getTitle(), page.getComponentPosition(), () -> page);
        }
        int position = node.position;
        if (position >= 1000) {
          roots.add(node);
        } else if (position > 500 && position < 600) {
          viewer.add(node);
        } else if (position > 600 && position < 700) {
          dicom.add(node);
        } else if (position > 700 && position < 800) {
          draw.add(node);
        } else {
          general.add(node);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Get Preference pages from plugin extensions", e);
    }
  }

  private static boolean isEnabled(PreferencesPageFactory factory) {
    String value =
        GuiUtils.getUICore().getSystemPreferences().getProperty(factory.getClass().getName());
    return !StringUtil.hasText(value) || Boolean.parseBoolean(value);
  }

  // ---- The interface ------------------------------------------------------------------------

  private void buildInterface() {
    searchField.putClientProperty(
        "JTextField.placeholderText", Messages.getString("PreferenceDialog.search")); // NON-NLS
    searchField.putClientProperty("JTextField.showClearButton", true); // NON-NLS
    searchField.putClientProperty("JTextField.leadingIcon", new FlatSearchIcon()); // NON-NLS
    searchField.getDocument().addDocumentListener(new SearchListener());

    DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
    renderer.setLeafIcon(null);
    renderer.setClosedIcon(null);
    renderer.setOpenIcon(null);
    tree.setCellRenderer(renderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setExpandsSelectedPaths(true);
    tree.setRowHeight(UIScale.scale(22));
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.addTreeSelectionListener(
        e -> {
          TreePath path = e.getNewLeadSelectionPath();
          if (!filtering
              && path != null
              && path.getLastPathComponent() instanceof DefaultMutableTreeNode treeNode
              && treeNode.getUserObject() instanceof PageNode node) {
            showNode(node);
          }
        });
    JScrollPane treeScroll = new JScrollPane(tree);
    treeScroll.setBorder(BorderFactory.createEmptyBorder());
    JPanel side = new JPanel(new MigLayout("insets 10lp 10lp 0 0, fill, wrap 1", "[grow,fill]"));
    side.add(searchField);
    side.add(treeScroll, "grow, push, width " + TREE_WIDTH + "lp::"); // NON-NLS

    // What belongs to the page shown: its title, scope and help above, its buttons under
    titleLabel.putClientProperty("FlatLaf.styleClass", "h3"); // NON-NLS
    scopeLabel.putClientProperty("FlatLaf.styleClass", "small"); // NON-NLS
    scopeLabel.setEnabled(false);
    helpButton.putClientProperty("JButton.buttonType", "help"); // NON-NLS
    helpButton.setToolTipText(Messages.getString("online.documentation"));
    JPanel header =
        new JPanel(new MigLayout("insets 10lp 15lp 5lp 15lp, fillx", "[grow]unrel[]unrel[]"));
    header.add(titleLabel, "growx, wmin 50lp"); // NON-NLS
    header.add(scopeLabel);
    header.add(helpButton);

    linksPanel.setBorder(GuiUtils.getEmptyBorder(10, 15, 0, 15));
    pageScroll.setBorder(BorderFactory.createEmptyBorder());
    pageScroll.setPreferredSize(GuiUtils.getDimension(PAGE_SIZE.width, PAGE_SIZE.height));

    applyButton.addActionListener(
        _ -> {
          AbstractItemDialogPage page = getCurrentPage();
          if (page != null) {
            page.closeAdditionalWindow();
          }
        });
    restoreButton.addActionListener(
        _ -> {
          AbstractItemDialogPage page = getCurrentPage();
          if (page != null) {
            page.resetToDefaultValues();
          }
        });
    pageBar.add(restoreButton);
    pageBar.add(applyButton);

    JPanel main = new JPanel(new BorderLayout());
    main.add(header, BorderLayout.NORTH);
    main.add(pageScroll, BorderLayout.CENTER);
    main.add(pageBar, BorderLayout.SOUTH);

    JSplitPane split = GuiUtils.getHorizontalSplit(side, main, -1);

    JPanel content = new JPanel(new BorderLayout());
    content.add(split, BorderLayout.CENTER);
    content.add(buildGlobalBar(), BorderLayout.SOUTH);
    setContentPane(content);

    // Ctrl+F goes to the search field
    getRootPane()
        .registerKeyboardAction(
            _ -> searchField.requestFocusInWindow(),
            KeyStroke.getKeyStroke(
                KeyEvent.VK_F, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  // What belongs to all the settings: the packages and the configuration files, and Close
  private JPanel buildGlobalBar() {
    JPopupMenu menu = new JPopupMenu();
    menu.add(
        menuItem(
            Messages.getString("PreferenceDialog.export.settings"),
            Messages.getString("PreferenceDialog.export.settings.tip"),
            _ -> SettingsPackageActions.exportSettings(this)));
    menu.add(
        menuItem(
            Messages.getString("PreferenceDialog.import.settings"),
            Messages.getString("PreferenceDialog.import.settings.tip"),
            _ -> SettingsPackageActions.importSettings(this)));
    menu.add(
        menuItem(
            Messages.getString("PreferenceDialog.import.legacy"),
            Messages.getString("PreferenceDialog.import.legacy.tip"),
            _ -> SettingsPackageActions.importLegacy(this)));
    menu.addSeparator();
    menu.add(
        menuItem(
            Messages.getString("PreferenceDialog.reload.site"),
            Messages.getString("PreferenceDialog.reload.site.tip"),
            _ -> SiteDocuments.reloadAll()));

    JButton manageButton = new JButton(Messages.getString("PreferenceDialog.manage") + " ▾");
    manageButton.setToolTipText(Messages.getString("PreferenceDialog.manage.tip"));
    manageButton.addActionListener(_ -> menu.show(manageButton, 0, manageButton.getHeight()));

    JButton closeButton = new JButton(Messages.common("close"));
    closeButton.addActionListener(_ -> cancel());

    JPanel bar = new JPanel(new MigLayout("insets 8lp 10lp 10lp 15lp, fillx", "[]push[]"));
    bar.add(manageButton);
    bar.add(closeButton);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JSeparator(), BorderLayout.NORTH);
    panel.add(bar, BorderLayout.CENTER);
    return panel;
  }

  private static JMenuItem menuItem(String text, String tip, ActionListener action) {
    JMenuItem item = new JMenuItem(text);
    item.setToolTipText(tip);
    item.addActionListener(action);
    return item;
  }

  // ---- Showing a page -----------------------------------------------------------------------

  private void showNode(PageNode node) {
    AbstractItemDialogPage page = node.page();
    AbstractItemDialogPage previous = getCurrentPage();
    if (previous != null && previous != page) {
      previous.deselectPageAction();
    }
    currentNode = node;
    titleLabel.setText(node.path());

    pageHolder.removeAll();
    linksPanel.removeAll();
    if (!node.children().isEmpty()) {
      for (PageNode child : node.children()) {
        linksPanel.add(linkButton(child));
      }
      pageHolder.add(linksPanel, BorderLayout.NORTH);
    }
    if (page == null) {
      JLabel error = new JLabel(Messages.getString("PreferenceDialog.page.error"));
      error.setBorder(GuiUtils.getEmptyBorder(15));
      pageHolder.add(error, BorderLayout.CENTER);
    } else {
      page.selectPageAction();
      pageHolder.add(page, BorderLayout.CENTER);
    }

    boolean apply = page != null && isTrue(page.getProperty(KEY_SHOW_APPLY));
    boolean restore = page != null && isTrue(page.getProperty(KEY_SHOW_RESTORE));
    applyButton.setVisible(apply);
    restoreButton.setVisible(restore);
    pageBar.setVisible(apply || restore);

    for (ActionListener listener : helpButton.getActionListeners()) {
      helpButton.removeActionListener(listener);
    }
    String helpKey = page == null ? null : page.getProperty(KEY_HELP);
    helpButton.setVisible(StringUtil.hasText(helpKey));
    if (helpButton.isVisible()) {
      helpButton.addActionListener(GuiUtils.createHelpActionListener(helpButton, helpKey));
    }
    String scope = page == null ? null : page.getProperty(AbstractItemDialogPage.KEY_SCOPE);
    scopeLabel.setVisible(StringUtil.hasText(scope));
    if (StringUtil.hasText(scope)) {
      scopeLabel.setText(scopeText(scope, false));
      scopeLabel.setToolTipText(scopeText(scope, true));
    }

    pageHolder.revalidate();
    pageHolder.repaint();
    pageScroll.getVerticalScrollBar().setValue(0);
    pageScroll.getHorizontalScrollBar().setValue(0);
  }

  // A sub-page offered on the page of its category
  private JButton linkButton(PageNode child) {
    JButton button = new JButton(child.title() + BREADCRUMB.stripTrailing());
    button.putClientProperty("JButton.buttonType", "toolBarButton"); // NON-NLS
    button.setHorizontalAlignment(SwingConstants.LEADING);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(_ -> showPage(child.title()));
    return button;
  }

  private static boolean isTrue(String value) {
    return Boolean.TRUE.toString().equals(value);
  }

  /** The label, or its tooltip, of a page scope; an unknown value reads as the user scope. */
  private static String scopeText(String scope, boolean tip) {
    return switch (scope) {
      case AbstractItemDialogPage.SCOPE_MACHINE ->
          Messages.getString(
              tip ? "PreferenceDialog.scope.machine.tip" : "PreferenceDialog.scope.machine");
      case AbstractItemDialogPage.SCOPE_SITE ->
          Messages.getString(
              tip ? "PreferenceDialog.scope.site.tip" : "PreferenceDialog.scope.site");
      default ->
          Messages.getString(
              tip ? "PreferenceDialog.scope.user.tip" : "PreferenceDialog.scope.user");
    };
  }

  /** The page shown, or null. */
  public AbstractItemDialogPage getCurrentPage() {
    return currentNode == null ? null : currentNode.page;
  }

  public void showFirstPage() {
    if (tree.getRowCount() > 0) {
      tree.setSelectionRow(0);
    }
  }

  /** Shows the page with this title, the first page when there is none. */
  public void showPage(String title) {
    if (!searchField.getText().isEmpty() && find(title, roots) != null) {
      searchField.setText(""); // the page may be filtered out
    }
    if (!selectPage(title)) {
      showFirstPage();
    }
  }

  private boolean selectPage(String title) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    var nodes = root.depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      if (nodes.nextElement() instanceof DefaultMutableTreeNode treeNode
          && treeNode.getUserObject() instanceof PageNode node
          && node.title().equals(title)) {
        TreePath path = new TreePath(treeNode.getPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
        return true;
      }
    }
    return false;
  }

  private static PageNode find(String title, List<PageNode> nodes) {
    for (PageNode node : nodes) {
      if (node.title().equals(title)) {
        return node;
      }
      PageNode child = find(title, node.children());
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  // ---- Search -------------------------------------------------------------------------------

  private final class SearchListener implements DocumentListener {
    @Override
    public void insertUpdate(DocumentEvent e) {
      search();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      search();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      search();
    }
  }

  private void search() {
    String query = searchField.getText().strip().toLowerCase(Locale.ROOT);
    if (!query.isEmpty()) {
      buildAllPages(roots); // a search looks into the labels of every page
    }
    PageNode shown = currentNode;
    filtering = true;
    try {
      fillTree(query);
      PageNode target = query.isEmpty() ? shown : bestMatch(query, shown);
      if (target == shown && shown != null) {
        selectPage(shown.title()); // the page shown stays as it is
        return;
      }
      filtering = false;
      if (target == null || !selectPage(target.title())) {
        tree.clearSelection();
      }
    } finally {
      filtering = false;
    }
  }

  private static void buildAllPages(List<PageNode> nodes) {
    for (PageNode node : nodes) {
      node.page();
      buildAllPages(node.children());
    }
  }

  // The page to show for a search: the one shown when it matches, else a page whose title
  // matches, else the first page that holds the words
  private PageNode bestMatch(String query, PageNode shown) {
    List<PageNode> matches = new ArrayList<>();
    collectMatches(roots, query, matches);
    if (matches.isEmpty()) {
      return null;
    }
    if (shown != null && matches.contains(shown)) {
      return shown;
    }
    return matches.stream()
        .filter(n -> matches(n.title().toLowerCase(Locale.ROOT), query))
        .findFirst()
        .orElse(matches.getFirst());
  }

  private static void collectMatches(List<PageNode> nodes, String query, List<PageNode> result) {
    for (PageNode node : nodes) {
      if (matches(node.index(), query)) {
        result.add(node);
      }
      collectMatches(node.children(), query, result);
    }
  }

  /** Whether the text holds every word of the query. */
  static boolean matches(String text, String query) {
    for (String word : query.split("\\s+")) { // NON-NLS
      if (!word.isEmpty() && !text.contains(word)) {
        return false;
      }
    }
    return true;
  }

  // The pages that match the query, with the categories that hold one; every page for no query
  private void fillTree(String query) {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root"); // NON-NLS
    for (PageNode node : roots) {
      DefaultMutableTreeNode treeNode = treeNode(node, query);
      if (treeNode != null) {
        root.add(treeNode);
      }
    }
    tree.setModel(new DefaultTreeModel(root, false));
    for (int row = 0; row < tree.getRowCount(); row++) {
      tree.expandRow(row);
    }
  }

  private static DefaultMutableTreeNode treeNode(PageNode node, String query) {
    DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
    for (PageNode child : node.children()) {
      DefaultMutableTreeNode childNode = treeNode(child, query);
      if (childNode != null) {
        treeNode.add(childNode);
      }
    }
    boolean keep = query.isEmpty() || treeNode.getChildCount() > 0 || matches(node.index(), query);
    return keep ? treeNode : null;
  }

  // The texts a page shows: labels, buttons, check boxes, group titles, tabs and tooltips
  private static void collectTexts(Component component, StringBuilder text) {
    if (component instanceof JLabel label) {
      append(text, label.getText());
    } else if (component instanceof AbstractButton button) {
      append(text, button.getText());
    } else if (component instanceof JTabbedPane tabs) {
      for (int i = 0; i < tabs.getTabCount(); i++) {
        append(text, tabs.getTitleAt(i));
      }
    }
    if (component instanceof JComponent c) {
      append(text, c.getToolTipText());
      appendBorder(text, c.getBorder());
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        collectTexts(child, text);
      }
    }
  }

  private static void appendBorder(StringBuilder text, Border border) {
    if (border instanceof TitledBorder titled) {
      append(text, titled.getTitle());
    } else if (border instanceof CompoundBorder compound) {
      appendBorder(text, compound.getOutsideBorder());
      appendBorder(text, compound.getInsideBorder());
    }
  }

  private static void append(StringBuilder text, String value) {
    if (StringUtil.hasText(value)) {
      text.append('\n').append(value.replaceAll("<[^>]*>", " ")); // NON-NLS
    }
  }

  // ---- Closing ------------------------------------------------------------------------------

  // Overridden so that the pages are saved when the window is closed
  @Override
  protected void processWindowEvent(WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
      cancel();
    }
    super.processWindowEvent(e);
  }

  public void cancel() {
    dispose();
  }

  /** Saves and closes every page that was built. */
  public void closeAllPages() {
    closePages(roots);
  }

  private static void closePages(List<PageNode> nodes) {
    for (PageNode node : nodes) {
      closePages(node.children());
      if (node.isBuilt()) {
        try {
          node.page.closeAdditionalWindow();
        } catch (RuntimeException e) {
          LOGGER.error("Cannot save the preference page {}", node.title(), e);
        }
      }
    }
  }

  @Override
  public void dispose() {
    if (currentNode != null) {
      lastPage = currentNode.title();
    }
    if (isDisplayable()) {
      lastSize = getSize();
    }
    closeAllPages();
    super.dispose();
  }
}
