package org.weasis.dicom.viewer2d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JOptionPane;

import org.dcm4che3.data.Attributes;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LoadDicomObjects;

public final class KOManager {

    // private final MediaSeries<DicomImageElement> currentDicomSeries;
    //
    // public KOManager(MediaSeries<DicomImageElement> currentDicomSeries) {
    // this.currentDicomSeries = currentDicomSeries;
    // }

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

            newKOSelection = findValidKOSelection(view2d);

            if (newKOSelection != null) {

                String message = "No KeyObject is selected but at least one is available.\n";
                Object[] options = { "Switch to a valid KeyObject Selection", "Create a new one" };

                int response =
                    JOptionPane.showOptionDialog(null, message, "Key Object Selection", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                if (response == 1) {
                    newDicomKO = createNewDicomKeyObject(currentImage);
                } else if (response == JOptionPane.CLOSED_OPTION) {
                    return null;
                }
            }
        } else {
            if (currentSelectedKO.getMediaReader().isEditableDicom()) {

                newKOSelection = currentSelectedKO;

                String studyInstanceUID = (String) currentImage.getTagValue(TagW.StudyInstanceUID);

                if (currentSelectedKO.containsStudyInstanceUIDReference(studyInstanceUID) == false
                    && currentSelectedKO.isEmpty() == false) {

                    String message = "Be aware that selected KO doesn't have any reference on the current study.\n";
                    Object[] options = { "Use it anyway", "Create a new KeyObject" };

                    int response =
                        JOptionPane.showOptionDialog(null, message, "Key Object Selection", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                    if (response == 1) {
                        newDicomKO = createNewDicomKeyObject(currentImage);
                    } else if (response == JOptionPane.CLOSED_OPTION) {
                        return null;
                    }
                }

            } else {

                String message = "Be aware that selected KO is Read Only.\n";
                Object[] options = { "Create a new KeyObject from a copy", "Create a new KeyObject" };

                int response =
                    JOptionPane.showOptionDialog(null, message, "Key Object Selection", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                if (response == 0) {
                    newDicomKO = createNewDicomKeyObject(currentSelectedKO);
                } else if (response == 1) {
                    newDicomKO = createNewDicomKeyObject(currentImage);
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

    private static Attributes createNewDicomKeyObject(MediaElement<?> dicomMediaElement) {

        if (dicomMediaElement == null || (dicomMediaElement.getMediaReader() instanceof DicomMediaIO) == false) {
            return null;
        }

        Attributes dicomSourceAttribute = ((DicomMediaIO) dicomMediaElement.getMediaReader()).getDicomObject();

        String message = "Set a description for the new KeyObject Selection";
        String defautDescription = "new KO selection";

        String description =
            (String) JOptionPane.showInputDialog(null, message, "Key Object Selection",
                JOptionPane.INFORMATION_MESSAGE, null, null, defautDescription);

        if (!StringUtil.hasText(description)) {
            return null; // no input is given meaning operation is canceled
        }

        return DicomMediaUtils.createDicomKeyObject(dicomSourceAttribute, description, null);
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

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean setKeyObjectReference(boolean selectedState, final DefaultView2d<DicomImageElement> view2d) {

        KOSpecialElement validKOSelection = KOManager.getValidKOSelection(view2d);

        if (validKOSelection == null) {
            return false; // canceled
        }

        DicomImageElement currentImage = view2d.getImage();
        boolean hasChanged = validKOSelection.setKeyObjectReference(selectedState, currentImage);

        if (hasChanged) {
            DicomModel dicomModel = (DicomModel) view2d.getSeries().getTagValue(TagW.ExplorerModel);
            // Fire an event since any view in any View2dContainner may have its KO selected state changed
            dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, view2d, null,
                validKOSelection));
        }

        // !! Notify KO_SELECTION change only after KO_TOOGLE_STATE has been updated into the data model
        KOSpecialElement currentSelectedKO = KOManager.getCurrentKOSelection(view2d);

        if (validKOSelection != currentSelectedKO) {
            ActionState koSelectionAction = view2d.getEventManager().getAction(ActionW.KO_SELECTION);
            if (koSelectionAction instanceof ComboItemListener) {
                ((ComboItemListener) koSelectionAction).setSelectedItem(validKOSelection);
            }
        }

        return hasChanged;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Deprecated
    public static void toogleKoState(final DefaultView2d<DicomImageElement> defaultView2d) {

        MediaSeries<DicomImageElement> currentDicomSeries = defaultView2d.getSeries();
        DicomImageElement currentImage = defaultView2d.getImage();

        String currentStudyInstanceUID = (String) currentImage.getTagValue(TagW.StudyInstanceUID);
        String currentSeriesInstanceUID = (String) currentImage.getTagValue(TagW.SeriesInstanceUID);
        String currentSOPInstanceUID = (String) currentImage.getTagValue(TagW.SOPInstanceUID);
        String currentSOPClassUID = (String) currentImage.getTagValue(TagW.SOPClassUID);

        DicomModel dicomModel = (DicomModel) currentDicomSeries.getTagValue(TagW.ExplorerModel);

        KOSpecialElement selectedKO =
            (defaultView2d.getActionValue(ActionW.KO_SELECTION.cmd()) instanceof KOSpecialElement)
                ? (KOSpecialElement) defaultView2d.getActionValue(ActionW.KO_SELECTION.cmd()) : null;

        // null is Default, it involves creating a new dicom KO and use it as the new selection
        KOSpecialElement dicomKO = null;

        // Is there a KO selected ?
        if (selectedKO != null) {

            // Is it a new created dicom KO ?
            if (selectedKO.getMediaReader().isEditableDicom()) {

                // Does this selected KO refers to this studyUID or is it empty?
                if (selectedKO.getReferencedStudyInstanceUIDSet().contains(currentStudyInstanceUID)
                    || selectedKO.getReferencedSOPInstanceUIDSet().size() == 0) {

                    // continue with the selected KO
                    dicomKO = selectedKO;

                } else {
                    // Ask the user whether it's better to use this selected KO with a new
                    // study reference or to create a new KO for this study

                    String message = "Be aware that selected KO doesn't have any reference on this study.\n";
                    Object[] options = { "Use it anyway", "Create a new KeyObject" };

                    int response =
                        JOptionPane.showOptionDialog(null, message, "Key Object Selection", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                    if (response == 0) {
                        dicomKO = selectedKO;
                    } else if (response != 1) { // no option is chosen meaning operation is cancelled
                        return;
                    }
                }

            } else {
                // Ask the user whether it's better to create a new KO from a copy of the selected
                // one or to create a new KO for this study

                String message = "Be aware that selected KO is Read Only.\n";
                Object[] options = { "Create a new KeyObject from a copy", "Create an empty new KeyObject" };

                int response =
                    JOptionPane.showOptionDialog(null, message, "Key Object Selection", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                if (response == 0) {
                    dicomKO = selectedKO;
                } else if (response != 1) { // no option is chosen meaning operation is cancelled
                    return;
                }
            }
        } else {

            // Is there any new created dicom KO for this study or any empty one?
            Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(currentDicomSeries);

            if (koElements != null) {
                for (KOSpecialElement koElement : koElements) {
                    if (koElement.getMediaReader().isEditableDicom()) {
                        if (koElement.getReferencedStudyInstanceUIDSet().contains(currentStudyInstanceUID)
                            || koElement.getReferencedSOPInstanceUIDSet().size() == 0) {
                            dicomKO = koElement;
                            break;
                        }
                    }
                }
            }

            if (dicomKO != null) {
                // Ask the user whether it's better to switch to the most recent new created KO with a
                // reference to this study or to create a new KO for this study

                String message = "No KeyObject is selected but at least one is available.\n";
                Object[] options = { "Switch to the most recent valid KeyObject", "Create an empty new KeyObject" };

                int response =
                    JOptionPane.showOptionDialog(null, message, "Key Object Selection", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                if (response == 1) {
                    dicomKO = null;
                } else if (response != 0) { // no option is chosen meaning operation is cancelled
                    return;
                }
            }
        }

        if (dicomKO == null || !dicomKO.getMediaReader().isEditableDicom()) {

            Attributes newDicomKO = null;

            String message = "Set a description for the new KeyObject Selection";
            String description =
                (String) JOptionPane.showInputDialog(null, message, "Key Object Selection",
                    JOptionPane.INFORMATION_MESSAGE, null, null, "new KO selection");

            if (!StringUtil.hasText(description)) {
                return; // no input is given meaning operation is cancelled
            }

            if (dicomKO == null) {
                // create a new empty dicom KO
                // String patientID = (String) currentImage.getTagValue(TagW.PatientID);
                // String patientName = (String) currentImage.getTagValue(TagW.PatientName);
                // Date patientBirthdate = (Date) currentImage.getTagValue(TagW.PatientBirthDate);

                if (currentImage.getMediaReader() instanceof DicomMediaIO) {
                    newDicomKO =
                        DicomMediaUtils.createDicomKeyObject(
                            ((DicomMediaIO) currentImage.getMediaReader()).getDicomObject(), description, null);
                }

            } else {
                // create a new dicom KO from the selected one by copying its content selection

                // TODO should remove from the current procedure evidence any SOPInstanceUID references outside
                // the scope of the current seriesInstanceUID or at least give the choice to the user

                newDicomKO =
                    DicomMediaUtils.createDicomKeyObject(dicomKO.getMediaReader().getDicomObject(), description, null);
            }

            new LoadDicomObjects(dicomModel, newDicomKO).addSelectionAndnotify(); // must be executed in the EDT

            // Find the new KOSpecialElement just loaded and set it as default for this view
            for (KOSpecialElement ko : DicomModel.getKoSpecialElements(currentDicomSeries)) {
                if (ko.getMediaReader().getDicomObject().equals(newDicomKO)) {
                    dicomKO = ko;
                    break;
                }
            }

            // TODO if dicomKOCandidate is a copy just remove any SopInstanceUID outside currentSeriesUID
        }

        // dicomKO.toggleKeyObjectReference(currentStudyInstanceUID, currentSeriesInstanceUID, currentSOPInstanceUID,
        // currentSOPClassUID);

        if (selectedKO != dicomKO) {
            ((View2d) defaultView2d).setKeyObjectSelection(dicomKO);
            selectedKO = dicomKO;
        }

        ((View2d) defaultView2d).updateKOSelectionChange();

        // Fire an event since any view in the View2dContainner may have its KO selected state changed
        // dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Update, currentView, null,
        // selectedKO));
    }

}
