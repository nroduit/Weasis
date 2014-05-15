package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JOptionPane;

import org.dcm4che3.data.Attributes;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LoadDicomObjects;

public final class KOManager {

    public static List<Object> getKOElementListWithNone(DefaultView2d<DicomImageElement> currentView) {

        Collection<KOSpecialElement> koElements =
            currentView != null ? DicomModel.getKoSpecialElements(currentView.getSeries()) : null;

        int koElementNb = (koElements == null) ? 0 : koElements.size();

        List<Object> koElementListWithNone = new ArrayList<Object>(koElementNb + 1);
        koElementListWithNone.add(ActionState.NONE);

        if (koElementNb > 0) {
            koElementListWithNone.addAll(koElements);
        }
        return koElementListWithNone;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Test if current sopInstanceUID is referenced in the selected KEY_OBJECT of the given currentView. If not, search
     * if there is a more suitable new KEY_OBJECT element. Ask the user if needed.
     */

    private static KOSpecialElement getValidKOSelection(final DefaultView2d<DicomImageElement> view2d) {

        KOSpecialElement currentSelectedKO = getCurrentKOSelection(view2d);
        DicomImageElement currentImage = view2d.getImage();

        KOSpecialElement newKOSelection = null;
        Attributes newDicomKO = null;

        if (currentSelectedKO == null) {

            KOSpecialElement validKOSelection = findValidKOSelection(view2d);

            if (validKOSelection != null) {

                String message = "No KeyObject is selected but at least one is available.\n";
                Object[] options = { "Switch to a valid KeyObject Selection", "Create a new one" };

                int response =
                    JOptionPane.showOptionDialog(view2d, message, "Key Object Selection", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                if (response == 0) {
                    newKOSelection = validKOSelection;
                } else if (response == 1) {
                    newDicomKO = createNewDicomKeyObject(currentImage, view2d);
                } else if (response == JOptionPane.CLOSED_OPTION) {
                    return null;
                }
            } else {
                newDicomKO = createNewDicomKeyObject(currentImage, view2d);
            }

        } else {
            if (currentSelectedKO.getMediaReader().isEditableDicom()) {

                String studyInstanceUID = (String) currentImage.getTagValue(TagW.StudyInstanceUID);

                if (currentSelectedKO.isEmpty()
                    || currentSelectedKO.containsStudyInstanceUIDReference(studyInstanceUID)) {

                    newKOSelection = currentSelectedKO;
                } else {

                    String message = "Be aware that selected KO doesn't have any reference on the current study.\n";
                    Object[] options = { "Use it anyway", "Create a new KeyObject" };

                    int response =
                        JOptionPane.showOptionDialog(view2d, message, "Key Object Selection",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                    if (response == 0) {
                        newKOSelection = currentSelectedKO;
                    } else if (response == 1) {
                        newDicomKO = createNewDicomKeyObject(currentImage, view2d);
                    } else if (response == JOptionPane.CLOSED_OPTION) {
                        return null;
                    }
                }

            } else {

                String message = "Be aware that selected KO is Read Only.\n";
                Object[] options = { "Create a new KeyObject from a copy", "Create a new KeyObject" };

                int response =
                    JOptionPane.showOptionDialog(view2d, message, "Key Object Selection", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                if (response == 0) {
                    newDicomKO = createNewDicomKeyObject(currentSelectedKO, view2d);
                } else if (response == 1) {
                    newDicomKO = createNewDicomKeyObject(currentImage, view2d);
                } else if (response == JOptionPane.CLOSED_OPTION) {
                    return null;
                }
            }
        }

        if (newDicomKO != null) {
            newKOSelection = loadDicomObject(view2d.getSeries(), newDicomKO);
        }

        return newKOSelection;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static KOSpecialElement loadDicomObject(MediaSeries<DicomImageElement> dicomSeries, Attributes newDicomKO) {

        DicomModel dicomModel = (DicomModel) dicomSeries.getTagValue(TagW.ExplorerModel);

        new LoadDicomObjects(dicomModel, newDicomKO).addSelectionAndnotify(); // must be executed in the EDT

        for (KOSpecialElement koElement : DicomModel.getKoSpecialElements(dicomSeries)) {
            if (koElement.getMediaReader().getDicomObject().equals(newDicomKO)) {
                return koElement;
            }
        }

        return null;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static Attributes createNewDicomKeyObject(MediaElement<?> dicomMediaElement, Component parentComponent) {

        if (dicomMediaElement == null || (dicomMediaElement.getMediaReader() instanceof DicomMediaIO) == false) {
            return null;
        }

        Attributes dicomSourceAttribute = ((DicomMediaIO) dicomMediaElement.getMediaReader()).getDicomObject();

        String message = "Set a description for the new KeyObject Selection";
        String defautDescription = "new KO selection";

        String description =
            (String) JOptionPane.showInputDialog(parentComponent, message, "Key Object Selection",
                JOptionPane.INFORMATION_MESSAGE, null, null, defautDescription);

        // description==null means the user canceled the input
        if (StringUtil.hasText(description)) {
            return DicomMediaUtils.createDicomKeyObject(dicomSourceAttribute, description, null);
        }

        return null;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Get an editable Dicom KeyObject Selection suitable to handle current Dicom Image. A valid object should either
     * reference the studyInstanceUID of the current Dicom Image or simply be empty ...
     */

    public static KOSpecialElement findValidKOSelection(final DefaultView2d<DicomImageElement> view2d) {

        MediaSeries<DicomImageElement> dicomSeries = view2d.getSeries();
        DicomImageElement currentImage = view2d.getImage();

        String currentStudyInstanceUID = (String) currentImage.getTagValue(TagW.StudyInstanceUID);
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
        return null;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static KOSpecialElement getCurrentKOSelection(final DefaultView2d<DicomImageElement> view2d) {

        Object actionValue = view2d.getActionValue(ActionW.KO_SELECTION.cmd());
        if (actionValue instanceof KOSpecialElement) {
            return (KOSpecialElement) actionValue;
        }

        return null;
    }

    public static Boolean getCurrentKOToogleState(final DefaultView2d<DicomImageElement> view2d) {

        Object actionValue = view2d.getActionValue(ActionW.KO_TOOGLE_STATE.cmd());
        if (actionValue instanceof Boolean) {
            return (Boolean) actionValue;
        }

        return null;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean setKeyObjectReference(boolean selectedState, final DefaultView2d<DicomImageElement> view2d) {

        KOSpecialElement validKOSelection = getValidKOSelection(view2d);

        if (validKOSelection == null) {
            return false; // canceled
        }

        DicomImageElement currentImage = view2d.getImage();
        boolean hasKeyObjectReferenceChanged = validKOSelection.setKeyObjectReference(selectedState, currentImage);

        KOSpecialElement currentSelectedKO = KOManager.getCurrentKOSelection(view2d);
        boolean hasKeyObjectSelectionChanged = validKOSelection != currentSelectedKO;

        if (hasKeyObjectSelectionChanged) {
            if (view2d instanceof View2d) {
                ((View2d) view2d).setKeyObjectSelection(validKOSelection);
            }
        }

        if (hasKeyObjectReferenceChanged) {
            DicomModel dicomModel = (DicomModel) view2d.getSeries().getTagValue(TagW.ExplorerModel);
            // Fire an event since any view in any View2dContainner may have its KO selected state changed
            dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, view2d, null,
                validKOSelection));
        }

        return hasKeyObjectSelectionChanged;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void updateKOSelectionChange(DefaultView2d<DicomImageElement> view2D) {

        DicomSeries dicomSeries = (DicomSeries) view2D.getSeries();
        DicomImageElement currentImg = view2D.getImage();

        Object selectedKO = view2D.getActionValue(ActionW.KO_SELECTION.cmd());

        if (currentImg != null && dicomSeries != null && selectedKO instanceof KOSpecialElement) {

            if ((Boolean) view2D.getActionValue(ActionW.KO_FILTER.cmd())) {

                int newImageIndex = view2D.getFrameIndex();
                // The getFrameIndex() return a valid index for the current image displayed according to the current
                // FILTERED_SERIES and the current SortComparator

                // If the current image is not part anymore of the KO FILTERED_SERIES then it has been removed from the
                // selection. Hence, the nearest image should be selected.

                Filter<DicomImageElement> dicomFilter =
                    (Filter<DicomImageElement>) view2D.getActionValue(ActionW.FILTERED_SERIES.cmd());
                if (newImageIndex < 0) {

                    double[] val = (double[]) currentImg.getTagValue(TagW.SlicePosition);
                    double location = val[0] + val[1] + val[2];
                    Double offset = (Double) view2D.getActionValue(ActionW.STACK_OFFSET.cmd());
                    if (offset != null) {
                        location += offset;
                    }

                    if (dicomSeries.size(dicomFilter) > 0) {
                        newImageIndex =
                            dicomSeries.getNearestImageIndex(location, view2D.getTileOffset(), dicomFilter,
                                view2D.getCurrentSortComparator());
                    } else {
                        // If there is no more image in KO series filtered then disable the KO_FILTER
                        dicomFilter = null;
                        view2D.setActionsInView(ActionW.KO_FILTER.cmd(), false);
                        view2D.setActionsInView(ActionW.FILTERED_SERIES.cmd(), dicomFilter);
                        newImageIndex = view2D.getFrameIndex();
                    }
                }

                // Update the sliceAction component for the current selected View which fire a SCROLL_SERIES changeEvent
                // (see EventManager -> stateChanged()). This change will be handled by any DefaultView2d
                // object that listen this event change if the synchview Action is Enable

                // In case a new KO selection has been added the FILTERED_SERIES size will be updated in consequence
                // This avoids to call eventManager.updateComponentsListener since only moveTroughSliceAction should be
                // updated

                ActionState seqAction = view2D.getEventManager().getAction(ActionW.SCROLL_SERIES);
                SliderCineListener sliceAction = null;
                if (seqAction instanceof SliderCineListener) {
                    sliceAction = (SliderCineListener) seqAction;
                } else {
                    return;
                }

                sliceAction.setMinMaxValue(1, dicomSeries.size(dicomFilter), newImageIndex + 1);
            }
        }
    }

}
