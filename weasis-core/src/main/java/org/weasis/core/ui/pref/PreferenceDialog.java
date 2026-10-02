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

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Hashtable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.core.util.StringUtil;

public class PreferenceDialog extends AbstractWizardDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(PreferenceDialog.class);

  public static final String KEY_SHOW_APPLY = "show.apply";
  public static final String KEY_SHOW_RESTORE = "show.restore";
  public static final String KEY_HELP = "help.item";

  protected final JButton jButtonHelp = new JButton();
  protected final JButton restoreButton = new JButton(Messages.getString("restore.values"));
  protected final JButton applyButton = new JButton(Messages.getString("LabelPrefView.apply"));
  protected final JButton reloadSiteButton =
      new JButton(Messages.getString("PreferenceDialog.reload.site"));
  protected final JPanel bottomPrefPanel =
      GuiUtils.getFlowLayoutPanel(
          FlowLayout.TRAILING, 10, 7, reloadSiteButton, jButtonHelp, restoreButton, applyButton);
  protected final JLabel scopeLabel = new JLabel();
  protected final JPanel scopePanel =
      GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, 10, 0, scopeLabel);

  public PreferenceDialog(Window parentWin) {
    super(
        parentWin,
        Messages.getString("OpenPreferencesAction.title"),
        ModalityType.APPLICATION_MODAL,
        new Dimension(620, 460));

    jPanelBottom.add(bottomPrefPanel, 0);
    jPanelBottom.add(scopePanel, 0);
    scopeLabel.setEnabled(false);

    jButtonHelp.putClientProperty("JButton.buttonType", "help");
    jButtonHelp.setToolTipText(Messages.getString("online.documentation"));
    reloadSiteButton.setToolTipText(Messages.getString("PreferenceDialog.reload.site.tip"));
    reloadSiteButton.addActionListener(e -> SiteDocuments.reloadAll());
    applyButton.addActionListener(
        e -> {
          if (currentPage != null) currentPage.closeAdditionalWindow();
        });
    restoreButton.addActionListener(
        e -> {
          if (currentPage != null) {
            currentPage.resetToDefaultValues();
          }
        });

    initializePages();
    pack();
    showFirstPage();
  }

  @Override
  protected void initializePages() {
    Hashtable<String, Object> properties = new Hashtable<>();
    properties.put("weasis.user.prefs", System.getProperty("weasis.user.prefs", "user")); // NON-NLS

    ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
    GeneralSetting generalSetting = new GeneralSetting(this);
    list.add(generalSetting);
    ViewerPrefView viewerSetting = new ViewerPrefView();
    list.add(viewerSetting);
    DicomPrefView dicomPrefView = new DicomPrefView();
    list.add(dicomPrefView);
    DrawPrefView drawPrefView = new DrawPrefView(this);
    list.add(drawPrefView);

    BundleContext context = AppProperties.getBundleContext(this.getClass());
    try {
      for (ServiceReference<PreferencesPageFactory> service :
          context.getServiceReferences(PreferencesPageFactory.class, null)) {
        PreferencesPageFactory factory = context.getService(service);
        if (factory != null) {
          String className =
              GuiUtils.getUICore().getSystemPreferences().getProperty(factory.getClass().getName());
          if (!StringUtil.hasText(className) || Boolean.parseBoolean(className)) {
            AbstractItemDialogPage page = factory.createInstance(properties);
            if (page != null) {
              int position = page.getComponentPosition();
              if (position < 1000) {
                AbstractItemDialogPage mainPage;
                if (position > 500 && position < 600) {
                  mainPage = viewerSetting;
                } else if (position > 600 && position < 700) {
                  mainPage = dicomPrefView;
                } else if (position > 700 && position < 800) {
                  mainPage = drawPrefView;
                } else {
                  mainPage = generalSetting;
                }
                JComponent menuPanel = mainPage.getMenuPanel();
                mainPage.addSubPage(page, a -> showPage(page.getTitle()), menuPanel);
                if (menuPanel != null) {
                  menuPanel.revalidate();
                  menuPanel.repaint();
                }
              } else {
                list.add(page);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Get Preference pages from service", e);
    }

    InsertableUtil.sortInsertable(list);
    for (AbstractItemDialogPage page : list) {
      page.sortSubPages();
      pagesRoot.add(new DefaultMutableTreeNode(page));
    }
    iniTree();
  }

  @Override
  protected void selectPage(AbstractItemDialogPage page) {
    if (page != null) {
      super.selectPage(page);
      applyButton.setVisible(Boolean.TRUE.toString().equals(page.getProperty(KEY_SHOW_APPLY)));
      restoreButton.setVisible(Boolean.TRUE.toString().equals(page.getProperty(KEY_SHOW_RESTORE)));

      String helpKey = page.getProperty(KEY_HELP);
      for (ActionListener al : jButtonHelp.getActionListeners()) {
        jButtonHelp.removeActionListener(al);
      }
      jButtonHelp.setVisible(StringUtil.hasText(helpKey));
      String scope = page.getProperty(AbstractItemDialogPage.KEY_SCOPE);
      scopePanel.setVisible(StringUtil.hasText(scope));
      if (StringUtil.hasText(scope)) {
        scopeLabel.setText(scopeText(scope, false));
        scopeLabel.setToolTipText(scopeText(scope, true));
      }
      if (jButtonHelp.isVisible()) {
        jButtonHelp.addActionListener(GuiUtils.createHelpActionListener(jButtonHelp, helpKey));
      }
    }
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

  @Override
  public void cancel() {
    dispose();
  }

  @Override
  public void dispose() {
    closeAllPages();
    super.dispose();
  }
}
