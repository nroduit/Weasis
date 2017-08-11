package org.weasis.dicom.rendering;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.mf.Patient;
import org.weasis.dicom.mf.ViewerMessage;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.KOManager;

public class RenderingToolbar extends WtoolBar {
    private static final long serialVersionUID = 1094212122971026258L;
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingToolbar.class);

    public static final ActionW PUBLISH_MF =
        new ActionW(Messages.getString("RenderingToolbar.exportKO"), "pub_mf", 0, 0, //$NON-NLS-1$ //$NON-NLS-2$
            null);
    public static final ActionW SET_TILE_MULTI_VIEW =
        new ActionW(Messages.getString("RenderingToolbar.multilayout"), //$NON-NLS-1$
            "set_tile_multi_view", 0, 0, null); //$NON-NLS-1$

    public RenderingToolbar() {
        super(PUBLISH_MF.getTitle(), 0);

        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.toolbar.export.clipboard.layout", true)) { //$NON-NLS-1$
            final JButton setTileMultiViewBtn = new JButton();

            setTileMultiViewBtn.setIcon(new ImageIcon(getClass().getResource("/icon/32x32/multiView.png"))); //$NON-NLS-1$
            setTileMultiViewBtn.setToolTipText(SET_TILE_MULTI_VIEW.getTitle());
            setTileMultiViewBtn.setActionCommand(SET_TILE_MULTI_VIEW.cmd());
            setTileMultiViewBtn.addActionListener(newSetTileMultiViewAction());

            add(setTileMultiViewBtn);
        }

        final JButton exportClipBtn = new JButton();

        exportClipBtn.setIcon(new ImageIcon(getClass().getResource("/icon/32x32/clipboard_ko.png"))); //$NON-NLS-1$
        exportClipBtn.setToolTipText(PUBLISH_MF.getTitle());
        exportClipBtn.setActionCommand(PUBLISH_MF.cmd());
        exportClipBtn.addActionListener(publishManifestAction());

        add(exportClipBtn);
    }

    private ActionListener publishManifestAction() {
        return e -> {
            ImageViewerPlugin<DicomImageElement> view2dContainer =
                EventManager.getInstance().getSelectedView2dContainer();
            if (view2dContainer == null) {
                return;
            }

            DicomModel model = getDicomModel();
            if (model != null) {
                buildManifest(model, view2dContainer.getGroupID());
            }
        };
    }

    private void buildManifest(DicomModel model, MediaSeriesGroup patient) {

        Collection<KOSpecialElement> koEditable = DicomModel.getEditableKoSpecialElements(patient);
        
        if (koEditable.isEmpty() || koEditable.stream().allMatch(k -> k.getReferencedSOPInstanceUIDSet().isEmpty())) {
            JOptionPane.showMessageDialog(RenderingToolbar.this,
                Messages.getString("RenderingToolbar.msg-no_image"), //$NON-NLS-1$
                Messages.getString("RenderingToolbar.warning"), //$NON-NLS-1$
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        
        final int maxNumber = BundleTools.SYSTEM_PREFERENCES.getIntProperty("weasis.toolbar.export.clipboard.img.limit", //$NON-NLS-1$
            Integer.MAX_VALUE);
        if ( koEditable.stream().anyMatch(k -> k.getReferencedSOPInstanceUIDSet().size() > maxNumber)) {
            JOptionPane.showMessageDialog(RenderingToolbar.this,
                String.format(Messages.getString("RenderingToolbar.msg_nb_images"), maxNumber), //$NON-NLS-1$
                Messages.getString("RenderingToolbar.warning"), JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$
            return;
        }


        WadoParameters wado = null;
        ViewerMessage message = null;
        int imgNotAdd = 0;
        final List<Patient> patientList = new ArrayList<>();
//        try {
//            Patient p = DicomModelQueryResult.getPatient(patient, patientList);
//            for (String studyUID : selectedKO.getReferencedStudyInstanceUIDSet()) {
//                MediaSeriesGroup study = model.getHierarchyNode(patient, studyUID);
//                Study st = DicomModelQueryResult.getStudy(study, p);
//
//                for (String seriesUID : selectedKO.getReferencedSeriesInstanceUIDSet(studyUID)) {
//                    MediaSeriesGroup series = model.getHierarchyNode(study, seriesUID);
//                    Series s = DicomModelQueryResult.getSeries(series, st);
//                    Set<String> instancesSet = selectedKO.getReferencedSOPInstanceUIDSet(seriesUID);
//
//                    if (instancesSet != null && series instanceof MediaSeries) {
//                        WadoParameters wadoParams = (WadoParameters) series.getTagValue(TagW.WadoParameters);
//                        if (wadoParams == null) {
//                            imgNotAdd += instancesSet.size();
//                            continue;
//                        }
//                        if (wado == null) {
//                            wado = wadoParams;
//                        }
//                        for (DicomImageElement media : ((MediaSeries<DicomImageElement>) series)
//                            .getSortedMedias(null)) {
//                            String sopUID = (String) media.getTagValue(TagD.get(Tag.SOPInstanceUID));
//
//                            if (instancesSet.contains(sopUID)) {
//                                SOPInstance sop = new SOPInstance(sopUID);
//                                sop.setInstanceNumber(((Integer) media.getTagValue(TagD.get(Tag.InstanceNumber)))
//                                    .toString().toUpperCase());
//                                s.addSOPInstance(sop);
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (Exception e11) {
//            String title = "Building manifest error"; //$NON-NLS-1$
//            AuditLog.logError(LOGGER, e11, title);
//            message = new ViewerMessage(title, e11.getMessage(), ViewerMessage.eLevel.ERROR);
//        }

//        if (wado != null) {
//            ArcQuery arquery;
//            try {
//                DefaultQueryResult result = new DefaultQueryResult(patientList, wado);
//                result.setViewerMessage(message);
//                arquery = new ArcQuery(Arrays.asList(result));
//                // Force manifest 1 for this plugin
//                Toolkit.getDefaultToolkit().getSystemClipboard()
//                    .setContents(new StringSelection(arquery.xmlManifest("1")), RenderingToolbar.this);
//            } catch (Exception e12) {
//                AuditLog.logError(LOGGER, e12, "Building wado query error"); //$NON-NLS-1$
//            }
//        }
//        if (imgNotAdd > 0) {
//            JOptionPane.showMessageDialog(RenderingToolbar.this,
//                imgNotAdd + " images cannot exported because they have be loaded locally!", //$NON-NLS-1$
//                Messages.getString("RenderingToolbar.warning"), //$NON-NLS-1$
//                JOptionPane.WARNING_MESSAGE);
//            return;
//        }
    }

    private ActionListener newSetTileMultiViewAction() {
        return e -> {

            ImageViewerPlugin<DicomImageElement> view2dContainer =
                EventManager.getInstance().getSelectedView2dContainer();

            if (view2dContainer == null) {
                return;
            }

            try {
                ComboItemListener<?> layoutAction =
                    (ComboItemListener<?>) EventManager.getInstance().getAction(ActionW.LAYOUT);

                int layout = BundleTools.SYSTEM_PREFERENCES.getIntProperty("weasis.toolbar.export.display.layout", //$NON-NLS-1$
                    6);
                GridBagLayoutModel layoutModel = view2dContainer.getBestDefaultViewLayout(layout);
                layoutAction.setSelectedItem(layoutModel);

                ComboItemListener<?> synchAction =
                    (ComboItemListener<?>) EventManager.getInstance().getAction(ActionW.SYNCH);
                synchAction.setSelectedItem(
                    BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.toolbar.export.clipboard.tile", true)
                        ? SynchView.DEFAULT_TILE : SynchView.DEFAULT_STACK);

                KOManager.setKeyObjectReferenceAllSeries(true, EventManager.getInstance().getSelectedViewPane());

            } catch (Exception ex) {
                LOGGER.warn(ex.toString());
            }
        };
    }

    private DicomModel getDicomModel() {
        DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
        if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
            return (DicomModel) dicomView.getDataExplorerModel();
        }
        return null;
    }
}
