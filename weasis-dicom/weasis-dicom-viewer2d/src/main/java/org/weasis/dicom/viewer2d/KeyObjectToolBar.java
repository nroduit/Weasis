/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Collection;
import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import org.dcm4che3.data.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupPopup;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.explorer.DicomModel;

public class KeyObjectToolBar extends WtoolBar {
  private static final Logger LOGGER = LoggerFactory.getLogger(KeyObjectToolBar.class);

  public static final FlatSVGIcon KO_STAR_ICON = ResourceUtil.getToolBarIcon(ActionIcon.STAR);
  public static final ImageIcon KO_STAR_ICON_SELECTED;
  public static final FlatSVGIcon KO_FILTER_ICON =
      ResourceUtil.getToolBarIcon(ActionIcon.SYNCH_STAR);
  public static final ImageIcon KO_FILTER_ICON_SELECTED;

  public static final ImageIcon KO_EDIT_SELECTION_ICON =
      ResourceUtil.getToolBarIcon(ActionIcon.EDIT_KEY_IMAGE);
  public static final ImageIcon KO_STAR_ICON_EXIST;

  static {
    ColorFilter colorFilter = new ColorFilter();
    colorFilter.add(new Color(0x6E6E6E), IconColor.ACTIONS_YELLOW.color);
    KO_STAR_ICON_SELECTED = GuiUtils.getDerivedIcon(KO_STAR_ICON, colorFilter);
    KO_FILTER_ICON_SELECTED = GuiUtils.getDerivedIcon(KO_FILTER_ICON, colorFilter);

    colorFilter = new ColorFilter();
    colorFilter.add(new Color(0x6E6E6E), IconColor.ACTIONS_BLUE.color);
    KO_STAR_ICON_EXIST = GuiUtils.getDerivedIcon(KO_STAR_ICON, colorFilter);
  }

  public KeyObjectToolBar(int index) {
    super(Messages.getString("KeyObjectToolBar.title"), index);

    final EventManager evtMgr = EventManager.getInstance();
    final ToggleButtonListener koToggleAction =
        (ToggleButtonListener) evtMgr.getAction(ActionW.KO_TOGGLE_STATE);
    final JToggleButton toggleKOSelectionBtn = new JToggleButton();

    toggleKOSelectionBtn.setToolTipText(ActionW.KO_TOGGLE_STATE.getTitle());
    toggleKOSelectionBtn.setIcon(KO_STAR_ICON);
    toggleKOSelectionBtn.setSelectedIcon(KO_STAR_ICON_SELECTED);

    koToggleAction.registerActionState(toggleKOSelectionBtn);
    add(toggleKOSelectionBtn);

    final ToggleButtonListener koFilterAction =
        (ToggleButtonListener) evtMgr.getAction(ActionW.KO_FILTER);
    final JToggleButton koFilterBtn = new JToggleButton();

    koFilterBtn.setToolTipText(ActionW.KO_FILTER.getTitle());
    koFilterBtn.setIcon(KO_FILTER_ICON);
    koFilterBtn.setSelectedIcon(KO_FILTER_ICON_SELECTED);

    koFilterAction.registerActionState(koFilterBtn);
    add(koFilterBtn);

    final ComboItemListener koSelectionAction =
        (ComboItemListener) evtMgr.getAction(ActionW.KO_SELECTION);
    GroupPopup koSelectionMenu = koSelectionAction.createGroupRadioMenu();

    final DropDownButton koSelectionButton =
        new DropDownButton(ActionW.KO_SELECTION.cmd(), buildKoSelectionIcon(), koSelectionMenu) {
          @Override
          protected JPopupMenu getPopupMenu() {
            JPopupMenu popupMenu =
                (getMenuModel() == null) ? new JPopupMenu() : getMenuModel().createJPopupMenu();
            popupMenu.setInvoker(this);
            return popupMenu;
          }
        };

    koSelectionButton.setToolTipText(ActionW.KO_SELECTION.getTitle());
    koSelectionAction.registerActionState(koSelectionButton);
    add(koSelectionButton);

    JButton koEditSelectionBtn = new JButton(KO_EDIT_SELECTION_ICON);
    koSelectionAction.registerActionState(koEditSelectionBtn);
    koEditSelectionBtn.addActionListener(e -> editKo(evtMgr, koSelectionAction, koFilterAction));

    add(koEditSelectionBtn);
  }

  private void editKo(
      EventManager evtMgr,
      ComboItemListener koSelectionAction,
      ToggleButtonListener koFilterAction) {

    ImageViewerPlugin<DicomImageElement> selectedView2dContainer =
        evtMgr.getSelectedView2dContainer();
    ViewCanvas<DicomImageElement> selectedView2d = evtMgr.getSelectedViewPane();

    if (selectedView2dContainer == null) {
      return;
    }
    if (selectedView2d == null) {
      return;
    }
    MediaSeries<DicomImageElement> selectedDicomSeries = selectedView2d.getSeries();
    if (selectedDicomSeries == null) {
      return;
    }

    Collection<KOSpecialElement> koElementCollection =
        DicomModel.getKoSpecialElements(selectedDicomSeries);

    final JList<KOSpecialElement> list = new JList<>();
    list.setBorder(BorderFactory.createLoweredSoftBevelBorder());
    list.setSelectionModel(new ToggleSelectionModel());

    list.setListData(koElementCollection.toArray(KOSpecialElement[]::new));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    JScrollPane scrollList = new JScrollPane(list);
    final JButton deleteBtn = new JButton(Messages.getString("KeyObjectToolBar.delete"));
    final JButton createBtn = new JButton(Messages.getString("KeyObjectToolBar.new"));
    final JButton copyBtn = new JButton(Messages.getString("KeyObjectToolBar.copy"));

    int maxBtnWidth = 0;
    maxBtnWidth = Math.max(maxBtnWidth, deleteBtn.getPreferredSize().width);
    maxBtnWidth = Math.max(maxBtnWidth, createBtn.getPreferredSize().width);
    maxBtnWidth = Math.max(maxBtnWidth, copyBtn.getPreferredSize().width);

    deleteBtn.setPreferredSize(new Dimension(maxBtnWidth, deleteBtn.getPreferredSize().height));
    createBtn.setPreferredSize(new Dimension(maxBtnWidth, createBtn.getPreferredSize().height));
    copyBtn.setPreferredSize(new Dimension(maxBtnWidth, copyBtn.getPreferredSize().height));

    Object[] message = {Messages.getString("KeyObjectToolBar.k0_list"), scrollList};

    final JOptionPane pane =
        new JOptionPane(
            message,
            JOptionPane.QUESTION_MESSAGE,
            JOptionPane.YES_NO_OPTION,
            null,
            new JButton[] {deleteBtn, createBtn},
            createBtn);

    deleteBtn.addActionListener(e1 -> pane.setValue(deleteBtn));
    createBtn.addActionListener(e1 -> pane.setValue(createBtn));
    copyBtn.addActionListener(e1 -> pane.setValue(copyBtn));

    list.addListSelectionListener(
        e1 -> {
          if (e1.getValueIsAdjusting()) {
            return;
          }

          if (list.isSelectionEmpty()) {
            pane.setOptions(new JButton[] {deleteBtn, createBtn});
            pane.setInitialValue(createBtn);
          } else {
            pane.setOptions(new JButton[] {deleteBtn, copyBtn});
            pane.setInitialValue(copyBtn);
          }

          deleteBtn.setEnabled(!list.isSelectionEmpty());
        });

    deleteBtn.setEnabled(!list.isSelectionEmpty());

    pane.setComponentOrientation(selectedView2dContainer.getComponentOrientation());
    JDialog dialog =
        pane.createDialog(selectedView2dContainer, Messages.getString("KeyObjectToolBar.edit"));

    pane.selectInitialValue();

    dialog.setVisible(true);
    dialog.dispose();

    Object selectedValue = pane.getValue();

    if (selectedValue != null) {
      if (selectedValue.equals(deleteBtn)) {
        LOGGER.info("Delete KeyObject {}", list.getSelectedValue());

        DicomModel dicomModel = (DicomModel) selectedDicomSeries.getTagValue(TagW.ExplorerModel);
        if (dicomModel != null) {
          dicomModel.removeSpecialElement(list.getSelectedValue());
          if (selectedView2d instanceof View2d view2d) {
            boolean needToRepaint = view2d.updateKOSelectedState(selectedView2d.getImage());
            if (needToRepaint) {
              evtMgr.updateKeyObjectComponentsListener(selectedView2d);
              repaint();
            }
          }
        }

      } else {
        Attributes newDicomKO = null;

        if (selectedValue.equals(createBtn)) {
          newDicomKO =
              KOManager.createNewDicomKeyObject(selectedView2d.getImage(), selectedView2dContainer);
          LOGGER.info("Create new KeyObject");

        } else if (selectedValue.equals(copyBtn)) {
          LOGGER.info("Copy selected KeyObject: {}", list.getSelectedValue());
          newDicomKO =
              KOManager.createNewDicomKeyObject(list.getSelectedValue(), selectedView2dContainer);
        }

        if (newDicomKO != null) {
          // Deactivate filter for new KO
          koFilterAction.setSelected(false);
          KOSpecialElement newKOSelection =
              KOManager.loadDicomKeyObject(selectedDicomSeries, newDicomKO);
          koSelectionAction.setSelectedItem(newKOSelection);
        }
      }
    }
  }

  private Icon buildKoSelectionIcon() {
    return DropButtonIcon.createDropButtonIcon(ResourceUtil.getToolBarIcon(OtherIcon.KEY_IMAGE));
  }

  static class ToggleSelectionModel extends DefaultListSelectionModel {
    private boolean gestureStarted = false;

    @Override
    public void setSelectionInterval(int index0, int index1) {
      // Toggle only one element while the user is dragging the mouse
      if (!gestureStarted) {
        if (isSelectedIndex(index0)) {
          super.removeSelectionInterval(index0, index1);
        } else {
          if (getSelectionMode() == SINGLE_SELECTION) {
            super.setSelectionInterval(index0, index1);
          } else {
            super.addSelectionInterval(index0, index1);
          }
        }
      }

      // Disable toggling till the adjusting is over, or keep it
      // enabled in case setSelectionInterval was called directly.
      gestureStarted = getValueIsAdjusting();
    }

    @Override
    public void setValueIsAdjusting(boolean isAdjusting) {
      super.setValueIsAdjusting(isAdjusting);

      if (!isAdjusting) {
        // Enable toggling
        gestureStarted = false;
      }
    }
  }
}
