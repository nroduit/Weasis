/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.PageItem;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.explorer.internal.Activator;

public class DicomImport extends AbstractWizardDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomImport.class);

  private static final String LAST_PAGE = "last.dicom.import.page";

  private boolean cancelVeto = false;
  private final DicomModel dicomModel;

  public DicomImport(Window parent, final DicomModel dicomModel) {
    super(
        parent,
        Messages.getString("DicomImport.imp_dicom"),
        ModalityType.APPLICATION_MODAL,
        new Dimension(650, 500));
    this.dicomModel = dicomModel;

    JButton importAndClose = new JButton(Messages.getString("DicomImport.impAndClose0"));
    importAndClose.addActionListener(
        e -> {
          importSelection();
          cancel();
        });

    JButton importButton = new JButton(Messages.getString("DicomImport.imp"));
    importButton.addActionListener(e -> importSelection());

    JButton jButtonHelp = new JButton();
    jButtonHelp.putClientProperty("JButton.buttonType", "help");
    jButtonHelp.addActionListener(
        e -> {
          try {
            GuiUtils.openInDefaultBrowser(
                jButtonHelp,
                new URL(
                    BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.online")
                        + "dicom-import"));
          } catch (MalformedURLException e1) {
            LOGGER.error("Cannot open online help", e1);
          }
        });

    jPanelBottom.removeAll();
    jPanelBottom.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING,
            HORIZONTAL_GAP,
            VERTICAL_GAP,
            jButtonHelp,
            importButton,
            importAndClose,
            jButtonClose));

    initializePages();
    pack();
    showPage(Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_PAGE));
  }

  @Override
  protected void initializePages() {
    ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
    list.add(new LocalImport());
    list.add(new DicomZipImport());
    list.add(new DicomDirImport());

    BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    try {
      for (ServiceReference<DicomImportFactory> service :
          context.getServiceReferences(DicomImportFactory.class, null)) {
        DicomImportFactory factory = context.getService(service);
        if (factory != null) {
          ImportDicom page = factory.createDicomImportPage(null);
          if (page instanceof AbstractItemDialogPage dialogPage) {
            list.add(dialogPage);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("init import pages", e);
    }

    InsertableUtil.sortInsertable(list);
    for (AbstractItemDialogPage page : list) {
      pagesRoot.add(new DefaultMutableTreeNode(page));
    }
    iniTree();
  }

  private void importSelection() {
    Object object = null;
    try {
      object = jScrollPanePage.getViewport().getComponent(0);
    } catch (Exception ex) {
    }
    if (object instanceof ImportDicom selectedPage) {
      selectedPage.importDICOM(dicomModel, null);
    }
  }

  public void setCancelVeto(boolean cancelVeto) {
    this.cancelVeto = cancelVeto;
  }

  @Override
  public void cancel() {
    if (cancelVeto) {
      cancelVeto = false;
    } else {
      dispose();
    }
  }

  @Override
  public void dispose() {
    PageItem page = getSelectedPage();
    if (page != null) {
      Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_PAGE, page.getTitle());
    }
    closeAllPages();
    super.dispose();
  }
}
