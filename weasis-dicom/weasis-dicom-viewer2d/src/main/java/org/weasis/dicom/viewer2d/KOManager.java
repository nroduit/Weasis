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

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.swing.JOptionPane;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.macro.HierarchicalSOPInstanceReference;
import org.weasis.dicom.codec.macro.KODocumentModule;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.LoadDicomObjects;

public final class KOManager {

  public static List<Object> getKOElementListWithNone(ViewCanvas<DicomImageElement> currentView) {

    Collection<KOSpecialElement> koElements =
        currentView != null ? DicomModel.getKoSpecialElements(currentView.getSeries()) : null;

    int koElementNb = (koElements == null) ? 0 : koElements.size();

    List<Object> koElementListWithNone = new ArrayList<>(koElementNb + 1);
    koElementListWithNone.add(ActionState.NoneLabel.NONE);

    if (koElementNb > 0) {
      koElementListWithNone.addAll(koElements);
    }
    return koElementListWithNone;
  }

  // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Test if current sopInstanceUID is referenced in the selected KEY_OBJECT of the given
   * currentView. If not, search if there is a more suitable new KEY_OBJECT element. Ask the user if
   * needed.
   */
  public static KOSpecialElement getValidKOSelection(final ViewCanvas<DicomImageElement> view2d) {

    KOSpecialElement currentSelectedKO = getCurrentKOSelection(view2d);
    DicomImageElement currentImage = view2d.getImage();

    KOSpecialElement newKOSelection = null;
    Attributes newDicomKO = null;

    if (currentSelectedKO == null) {

      KOSpecialElement validKOSelection = findValidKOSelection(view2d);

      if (validKOSelection != null) {

        String message = Messages.getString("KOManager.select_KO_msg");
        Object[] options = {
          Messages.getString("KOManager.select_last_ko"), Messages.getString("KOManager.new_ko")
        };

        int response =
            JOptionPane.showOptionDialog(
                view2d.getJComponent(),
                message,
                Messages.getString("KOManager.ko_title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);

        if (response == 0) {
          newKOSelection = validKOSelection;
        } else if (response == 1) {
          newDicomKO = createNewDicomKeyObject(currentImage, view2d.getJComponent());
        } else if (response == JOptionPane.CLOSED_OPTION) {
          return null;
        }
      } else {
        newDicomKO = createNewDicomKeyObject(currentImage, view2d.getJComponent());
      }

    } else {
      if (currentSelectedKO.getMediaReader().isEditableDicom()) {

        String studyInstanceUID =
            TagD.getTagValue(currentImage, Tag.StudyInstanceUID, String.class);

        if (currentSelectedKO.isEmpty()
            || currentSelectedKO.containsStudyInstanceUIDReference(studyInstanceUID)) {

          newKOSelection = currentSelectedKO;
        } else {

          String message = Messages.getString("KOManager.no_ko_msg");
          Object[] options = {
            Messages.getString("KOManager.use_ko"), Messages.getString("KOManager.new_ko")
          };

          int response =
              JOptionPane.showOptionDialog(
                  view2d.getJComponent(),
                  message,
                  Messages.getString("KOManager.ko_title"),
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.WARNING_MESSAGE,
                  null,
                  options,
                  options[0]);

          if (response == 0) {
            newKOSelection = currentSelectedKO;
          } else if (response == 1) {
            newDicomKO = createNewDicomKeyObject(currentImage, view2d.getJComponent());
          } else if (response == JOptionPane.CLOSED_OPTION) {
            return null;
          }
        }

      } else {

        String message = Messages.getString("KOManager.ko_readonly");
        Object[] options = {
          Messages.getString("KOManager.new_ko"), Messages.getString("KOManager.new_ko_from")
        };

        int response =
            JOptionPane.showOptionDialog(
                view2d.getJComponent(),
                message,
                Messages.getString("KOManager.ko_title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);

        if (response == 0) {
          newDicomKO = createNewDicomKeyObject(currentImage, view2d.getJComponent());
        } else if (response == 1) {
          newDicomKO = createNewDicomKeyObject(currentSelectedKO, view2d.getJComponent());
        } else if (response == JOptionPane.CLOSED_OPTION) {
          return null;
        }
      }
    }

    if (newDicomKO != null) {
      // Deactivate filter for new KO
      ActionState koFilterAction = view2d.getEventManager().getAction(ActionW.KO_FILTER);
      if (koFilterAction instanceof ToggleButtonListener buttonListener) {
        buttonListener.setSelected(false);
      }

      newKOSelection = loadDicomKeyObject(view2d.getSeries(), newDicomKO);
    }

    return newKOSelection;
  }

  // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static KOSpecialElement loadDicomKeyObject(
      MediaSeries<DicomImageElement> dicomSeries, Attributes newDicomKO) {

    DicomModel dicomModel = (DicomModel) dicomSeries.getTagValue(TagW.ExplorerModel);
    DicomModel.LOADING_EXECUTOR.execute(
        new LoadDicomObjects(dicomModel, OpeningViewer.NONE, newDicomKO));

    for (KOSpecialElement koElement : DicomModel.getKoSpecialElements(dicomSeries)) {
      if (koElement.getMediaReader().getDicomObject().equals(newDicomKO)) {
        return koElement;
      }
    }

    return null;
  }

  // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static Attributes createNewDicomKeyObject(
      MediaElement dicomMediaElement, Component parentComponent) {

    if (dicomMediaElement != null
        && dicomMediaElement.getMediaReader() instanceof DcmMediaReader reader) {
      Attributes dicomSourceAttribute = reader.getDicomObject();

      String message = Messages.getString("KOManager.ko_desc");
      String defaultDescription = Messages.getString("KOManager.ko_name");

      String description =
          (String)
              JOptionPane.showInputDialog(
                  parentComponent,
                  message,
                  Messages.getString("KOManager.ko_title"),
                  JOptionPane.INFORMATION_MESSAGE,
                  null,
                  null,
                  defaultDescription);

      // description==null means the user canceled the input
      if (StringUtil.hasText(description)) {
        Attributes ko =
            DicomMediaUtils.createDicomKeyObject(dicomSourceAttribute, description, null);

        if (dicomMediaElement instanceof KOSpecialElement) {
          Collection<HierarchicalSOPInstanceReference> referencedStudySequence =
              new KODocumentModule(dicomSourceAttribute).getCurrentRequestedProcedureEvidences();

          new KODocumentModule(ko).setCurrentRequestedProcedureEvidences(referencedStudySequence);
        }
        return ko;
      }
    }
    return null;
  }

  // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Get an editable Dicom KeyObject Selection suitable to handle current Dicom Image. A valid
   * object should either reference the studyInstanceUID of the current Dicom Image or simply be
   * empty ...
   */
  public static KOSpecialElement findValidKOSelection(final ViewCanvas<DicomImageElement> view2d) {

    MediaSeries<DicomImageElement> dicomSeries = view2d.getSeries();
    DicomImageElement currentImage = view2d.getImage();
    if (currentImage != null && dicomSeries != null) {
      String currentStudyInstanceUID =
          TagD.getTagValue(currentImage, Tag.StudyInstanceUID, String.class);
      Collection<KOSpecialElement> koElementsWithReferencedSeriesInstanceUID =
          DicomModel.getKoSpecialElements(dicomSeries);

      if (koElementsWithReferencedSeriesInstanceUID != null) {

        for (KOSpecialElement koElement : koElementsWithReferencedSeriesInstanceUID) {
          if (koElement.getMediaReader().isEditableDicom()) {
            if (koElement.containsStudyInstanceUIDReference(currentStudyInstanceUID)) {
              return koElement;
            }
          }
        }

        for (KOSpecialElement koElement : koElementsWithReferencedSeriesInstanceUID) {
          if (koElement.getMediaReader().isEditableDicom()) {
            if (koElement.isEmpty()) {
              return koElement;
            }
          }
        }
      }
    }
    return null;
  }

  // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static KOSpecialElement getCurrentKOSelection(final ViewCanvas<DicomImageElement> view2d) {

    Object actionValue = view2d.getActionValue(ActionW.KO_SELECTION.cmd());
    if (actionValue instanceof KOSpecialElement koSpecialElement) {
      return koSpecialElement;
    }

    return null;
  }

  public static boolean setKeyObjectReference(
      boolean selectedState, final ViewCanvas<DicomImageElement> view2d) {

    KOSpecialElement validKOSelection = getValidKOSelection(view2d);

    if (validKOSelection == null) {
      return false; // canceled
    }

    KOSpecialElement currentSelectedKO = KOManager.getCurrentKOSelection(view2d);

    if (validKOSelection != currentSelectedKO) {
      ActionState koSelection = view2d.getEventManager().getAction(ActionW.KO_SELECTION);
      if (koSelection instanceof ComboItemListener itemListener) {
        itemListener.setSelectedItem(validKOSelection);
      }
    }

    boolean hasKeyObjectReferenceChanged = false;

    if (validKOSelection == currentSelectedKO || currentSelectedKO == null) {
      // KO Toggle State is changed only if KO Selection remains the same,
      // or if there was no previous KO Selection

      DicomImageElement currentImage = view2d.getImage();
      hasKeyObjectReferenceChanged =
          validKOSelection.setKeyObjectReference(selectedState, currentImage);

      if (hasKeyObjectReferenceChanged) {
        DicomModel dicomModel = (DicomModel) view2d.getSeries().getTagValue(TagW.ExplorerModel);
        // Fire an event since any view in any View2dContainer may have its KO selected state
        // changed
        if (dicomModel != null) {
          dicomModel.firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.UPDATE, view2d, null, validKOSelection));
        }

        boolean filter =
            LangUtil.getNULLtoFalse((Boolean) view2d.getActionValue(ActionW.KO_FILTER.cmd()));
        if (filter && (view2d.getEventManager().getSelectedViewPane() == view2d)) {
          // When unchecking an image, force to call the filter action to resize the views
          ActionState koFilterAction = view2d.getEventManager().getAction(ActionW.KO_FILTER);
          if (koFilterAction instanceof ToggleButtonListener buttonListener) {
            buttonListener.setSelectedWithoutTriggerAction(false);
            buttonListener.setSelected(true);
          }
        }
      }
    }

    return hasKeyObjectReferenceChanged;
  }

  public static boolean setKeyObjectReferenceAllSeries(
      boolean selectedState, final ViewCanvas<DicomImageElement> view2d) {

    KOSpecialElement validKOSelection = getValidKOSelection(view2d);

    if (validKOSelection == null) {
      return false; // canceled
    }

    KOSpecialElement currentSelectedKO = KOManager.getCurrentKOSelection(view2d);

    if (validKOSelection != currentSelectedKO) {
      ActionState koSelection = view2d.getEventManager().getAction(ActionW.KO_SELECTION);
      if (koSelection instanceof ComboItemListener itemListener) {
        itemListener.setSelectedItem(validKOSelection);
      }
    }

    boolean hasKeyObjectReferenceChanged = false;

    if (validKOSelection == currentSelectedKO || currentSelectedKO == null) {
      // KO Toggle State is changed only if KO Selection remains the same,
      // or if there was no previous KO Selection
      hasKeyObjectReferenceChanged =
          validKOSelection.setKeyObjectReference(selectedState, view2d.getSeries());

      if (hasKeyObjectReferenceChanged) {
        DicomModel dicomModel = (DicomModel) view2d.getSeries().getTagValue(TagW.ExplorerModel);
        // Fire an event since any view in any View2dContainer may have its KO selected state
        // changed
        if (dicomModel != null) {
          dicomModel.firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.UPDATE,
                  view2d,
                  null,
                  new SeriesEvent(SeriesEvent.Action.UPDATE, validKOSelection, "updateAll")));
        }
      }
    }

    return hasKeyObjectReferenceChanged;
  }

  // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static void updateKOFilter(
      ViewCanvas<DicomImageElement> view2D,
      Object newSelectedKO,
      Boolean enableFilter,
      int imgSelectionIndex) {
    updateKOFilter(view2D, newSelectedKO, enableFilter, imgSelectionIndex, true);
  }

  public static void updateKOFilter(
      ViewCanvas<DicomImageElement> view2D,
      Object newSelectedKO,
      Boolean enableFilter,
      int imgSelectionIndex,
      boolean updateImage) {

    if (view2D instanceof View2d view2d) {
      boolean tiledMode = imgSelectionIndex >= 0;
      boolean koFilter;
      KOSpecialElement selectedKO = null;
      if (newSelectedKO == null) {
        Object actionValue = view2D.getActionValue(ActionW.KO_SELECTION.cmd());
        if (actionValue instanceof KOSpecialElement koSpecialElement) {
          selectedKO = koSpecialElement;

          // test if current ko_selection action in view do still exist
          Collection<KOSpecialElement> koElements =
              view2D.getSeries() != null
                  ? DicomModel.getKoSpecialElements(view2D.getSeries())
                  : null;
          if (koElements != null && !koElements.contains(selectedKO)) {
            selectedKO = null;
            newSelectedKO = ActionState.NoneLabel.NONE;
            view2D.setActionsInView(ActionW.KO_SELECTION.cmd(), newSelectedKO);
          }
        }
      } else {
        if (newSelectedKO instanceof KOSpecialElement koSpecialElement) {
          selectedKO = koSpecialElement;
        }
        view2D.setActionsInView(ActionW.KO_SELECTION.cmd(), newSelectedKO);
      }

      koFilter =
          Objects.requireNonNullElseGet(
              enableFilter,
              () ->
                  LangUtil.getNULLtoFalse(
                      (Boolean) view2D.getActionValue(ActionW.KO_FILTER.cmd())));

      if (tiledMode && selectedKO == null) {
        // Unselect the filter with the None KO selection
        koFilter = false;
      }

      view2D.setActionsInView(ActionW.KO_FILTER.cmd(), koFilter);
      view2D.setActionsInView(ActionW.FILTERED_SERIES.cmd(), null);

      if (selectedKO == null
          || view2D.getSeries() == null
          || (view2D.getImage() == null && !tiledMode)) {
        if (newSelectedKO != null) {
          if (updateImage) {
            updateImage(view2D, null, view2D.getFrameIndex());
          }
          // Update the None KO selection
          view2d.updateKOButtonVisibleState();
        }
        return;
      }

      DicomSeries dicomSeries = (DicomSeries) view2D.getSeries();
      String seriesInstanceUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
      Filter<DicomImageElement> sopInstanceUIDFilter = null;

      if (koFilter && selectedKO.containsSeriesInstanceUIDReference(seriesInstanceUID)) {
        sopInstanceUIDFilter = selectedKO.getSOPInstanceUIDFilter();
      }
      view2D.setActionsInView(ActionW.FILTERED_SERIES.cmd(), sopInstanceUIDFilter);

      if (updateImage) {

        /*
         * The getFrameIndex() returns a valid index for the current image displayed according to the current
         * FILTERED_SERIES and the current SortComparator
         */
        int newImageIndex = view2D.getFrameIndex();
        if (tiledMode) {
          newImageIndex = view2D.getTileOffset() + imgSelectionIndex;
        }

        if (koFilter && newImageIndex < 0) {
          if (dicomSeries.size(sopInstanceUIDFilter) > 0 && view2D.getImage() != null) {

            double[] val = (double[]) view2D.getImage().getTagValue(TagW.SlicePosition);
            if (val != null) {
              double location = val[0] + val[1] + val[2];
              // Double offset = (Double) view2D.getActionValue(ActionW.STACK_OFFSET.cmd());
              // if (offset != null) {
              // location += offset;
              // }
              newImageIndex =
                  dicomSeries.getNearestImageIndex(
                      location,
                      view2D.getTileOffset(),
                      sopInstanceUIDFilter,
                      view2D.getCurrentSortComparator());
            }
          } else {
            // If there is no more image in KO series filtered then disable the KO_FILTER
            sopInstanceUIDFilter = null;
            view2D.setActionsInView(ActionW.KO_FILTER.cmd(), false);
            view2D.setActionsInView(ActionW.FILTERED_SERIES.cmd(), null);
            newImageIndex = view2D.getFrameIndex();
          }
        }

        updateImage(view2D, sopInstanceUIDFilter, newImageIndex);
      }
      view2d.updateKOButtonVisibleState();
    }
  }

  private static void updateImage(
      ViewCanvas<DicomImageElement> view2D,
      Filter<DicomImageElement> sopInstanceUIDFilter,
      int newImageIndex) {
    int imgIndex = Math.max(newImageIndex, 0);
    if (view2D == view2D.getEventManager().getSelectedViewPane()) {
      /*
       * Update the sliceAction action according to the nearest image when the filter hides the image of the previous
       * state. And update the action min and max.
       */
      ActionState seqAction = view2D.getEventManager().getAction(ActionW.SCROLL_SERIES);
      if (seqAction instanceof SliderCineListener) {
        SliderChangeListener moveTroughSliceAction = (SliderChangeListener) seqAction;
        moveTroughSliceAction.setSliderMinMaxValue(
            1, view2D.getSeries().size(sopInstanceUIDFilter), imgIndex + 1);
      }
    }

    DicomImageElement newImage = null;
    if (view2D.getSeries() != null) {
      newImage =
          view2D
              .getSeries()
              .getMedia(imgIndex, sopInstanceUIDFilter, view2D.getCurrentSortComparator());
    }
    if (newImage != null && !newImage.isImageAvailable()) {
      newImage.getImage();
    }
    ((View2d) view2D).setImage(newImage);
  }
}
