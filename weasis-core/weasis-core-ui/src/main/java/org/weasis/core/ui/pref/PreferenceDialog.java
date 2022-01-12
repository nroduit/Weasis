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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.Messages;
import org.weasis.core.util.StringUtil;

public class PreferenceDialog extends AbstractWizardDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(PreferenceDialog.class);

  public static final String KEY_SHOW_APPLY = "show.apply";
  public static final String KEY_SHOW_RESTORE = "show.restore";
  public static final String KEY_HELP = "help.item";

  protected final JButton jButtonHelp = new JButton();
  protected final JButton restoreButton = new JButton(Messages.getString("restore.values"));
  protected final JButton applyButton = new JButton(Messages.getString("LabelPrefView.apply"));
  protected final JPanel bottomPrefPanel =
      GuiUtils.getComponentsInJPanel(
          FlowLayout.TRAILING, 10, 7, jButtonHelp, restoreButton, applyButton);

  public PreferenceDialog(Window parentWin) {
    super(
        parentWin,
        Messages.getString("OpenPreferencesAction.title"),
        ModalityType.APPLICATION_MODAL,
        new Dimension(700, 520));

    jPanelBottom.add(bottomPrefPanel, 0);

    jButtonHelp.putClientProperty("JButton.buttonType", "help");
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
    list.add(new GeneralSetting(this));
    ViewerPrefView viewerSetting = new ViewerPrefView();
    list.add(viewerSetting);
    DicomPrefView dicomPrefView = new DicomPrefView(this);
    list.add(dicomPrefView);

    BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    try {
      for (ServiceReference<PreferencesPageFactory> service :
          context.getServiceReferences(PreferencesPageFactory.class, null)) {
        PreferencesPageFactory factory = context.getService(service);
        if (factory != null) {
          AbstractItemDialogPage page = factory.createInstance(properties);
          if (page != null) {
            int position = page.getComponentPosition();
            if (position > 500) {
              if (position < 550) {
                viewerSetting.addSubPage(page);
              } else {
                dicomPrefView.addSubPage(page);
              }
            } else {
              list.add(page);
            }
          }
        }
      }
    } catch (InvalidSyntaxException e) {
      LOGGER.error("Get Preference pages from service", e);
    }

    InsertableUtil.sortInsertable(list);
    for (AbstractItemDialogPage page : list) {
      pagesRoot.add(new DefaultMutableTreeNode(page));
    }
    iniTree();
  }

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
      if (jButtonHelp.isVisible()) {
        jButtonHelp.addActionListener(
            e -> {
              try {
                GuiUtils.openInDefaultBrowser(
                    jButtonHelp,
                    new URL(
                        BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.online")
                            + helpKey));
              } catch (MalformedURLException e1) {
                LOGGER.error("Cannot open online help", e1);
              }
            });
      }
    }
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
