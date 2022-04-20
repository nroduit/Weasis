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

import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.PageItem;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.internal.Activator;

public class DicomExport extends AbstractWizardDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomExport.class);

  private static final String LAST_PAGE = "last.dicom.export.page";

  private final DicomModel dicomModel;
  private final CheckTreeModel treeModel;

  public DicomExport(Window parent, final DicomModel dicomModel) {
    super(
        parent,
        Messages.getString("DicomExport.exp_dicom"),
        ModalityType.APPLICATION_MODAL,
        new Dimension(650, 500));
    this.dicomModel = dicomModel;
    this.treeModel = new CheckTreeModel(dicomModel);

    JButton exportAndClose = new JButton(Messages.getString("DicomExport.exp_close"));
    exportAndClose.addActionListener(
        e -> {
          exportSelection();
          cancel();
        });
    JButton exportButton = new JButton();
    exportButton.addActionListener(e -> exportSelection());
    exportButton.setText(Messages.getString("DicomExport.exp"));

    JButton jButtonHelp = new JButton();
    jButtonHelp.putClientProperty("JButton.buttonType", "help");
    jButtonHelp.addActionListener(
        e -> {
          try {
            GuiUtils.openInDefaultBrowser(
                jButtonHelp,
                new URL(
                    BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.online")
                        + "dicom-export"));
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
            exportButton,
            exportAndClose,
            jButtonClose));

    initializePages();
    pack();
    showPage(Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_PAGE));
  }

  @Override
  protected void initializePages() {
    Hashtable<String, Object> properties = new Hashtable<>();
    properties.put(dicomModel.getClass().getName(), dicomModel);
    properties.put(treeModel.getClass().getName(), treeModel);

    initTreeCheckingModel();

    ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
    list.add(new LocalExport(dicomModel, treeModel));

    BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    try {
      for (ServiceReference<DicomExportFactory> service :
          context.getServiceReferences(DicomExportFactory.class, null)) {
        DicomExportFactory factory = context.getService(service);
        if (factory != null) {
          ExportDicom page = factory.createDicomExportPage(properties);
          if (page instanceof AbstractItemDialogPage dialogPage) {
            list.add(dialogPage);
          }
        }
      }
    } catch (InvalidSyntaxException e) {
      LOGGER.error("Insert DICOM export plugins", e);
    }

    InsertableUtil.sortInsertable(list);
    for (AbstractItemDialogPage page : list) {
      pagesRoot.add(new DefaultMutableTreeNode(page));
    }

    iniTree();
  }

  /**
   * Set the checking Paths for the CheckTreeModel to the open Series for the current selected
   * Patient only <br>
   */
  private void initTreeCheckingModel() {
    TreeCheckingModel checkingModel = treeModel.getCheckingModel();
    checkingModel.setCheckingMode(CheckingMode.PROPAGATE_PRESERVING_UNCHECK);

    if (UIManager.getExplorerplugin(DicomExplorer.NAME) instanceof DicomExplorer explorer) {

      Set<Series<?>> openSeriesSet = explorer.getSelectedPatientOpenSeries();
      Object rootNode = treeModel.getModel().getRoot();

      if (!openSeriesSet.isEmpty() && rootNode instanceof DefaultMutableTreeNode mutableTreeNode) {
        List<TreePath> selectedSeriesPathsList = new ArrayList<>();

        Enumeration<?> enumTreeNode = mutableTreeNode.breadthFirstEnumeration();
        while (enumTreeNode.hasMoreElements()) {
          Object child = enumTreeNode.nextElement();
          if (child instanceof DefaultMutableTreeNode treeNode) {
            if (treeNode.getLevel() != 3) { // 3 stands for Series Level
              continue;
            }

            Object userObject = treeNode.getUserObject();
            if (userObject instanceof DicomSeries && openSeriesSet.contains(userObject)) {
              selectedSeriesPathsList.add(new TreePath(treeNode.getPath()));
            }
          }
        }

        if (!selectedSeriesPathsList.isEmpty()) {
          TreePath[] seriesCheckingPaths = selectedSeriesPathsList.toArray(new TreePath[0]);
          checkingModel.setCheckingPaths(seriesCheckingPaths);
          treeModel.setDefaultSelectionPaths(selectedSeriesPathsList);
        }
      }
    }
  }

  private void exportSelection() {
    Object object = null;
    try {
      object = jScrollPanePage.getViewport().getComponent(0);
    } catch (Exception e) {
      LOGGER.debug("Failed to extract DICOM export", e);
    }
    if (object instanceof final ExportDicom selectedPage) {
      try {
        selectedPage.exportDICOM(treeModel, null);
      } catch (IOException e) {
        LOGGER.error("DICOM export failed", e);
      }
    }
  }

  @Override
  public void cancel() {
    dispose();
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

  public static Properties getImportExportProperties() {
    return Activator.IMPORT_EXPORT_PERSISTENCE;
  }
}
