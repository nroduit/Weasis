package org.weasis.acquire.explorer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.core.bean.DefaultTagable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableColumnAdjuster;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.tool.ModalityWorklist;

public class WorklistDialog extends JDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorklistDialog.class);

    private JButton okButton;
    private JButton cancelButton;
    private JPanel footPanel;

    private DicomNode calling;
    private DicomNode called;

    private JPanel tableContainer;

    private JTable jtable;

    private Attributes selectedItem;

    public WorklistDialog(Window parent, String title, DicomNode calling, DicomNode called) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        initComponents();
        this.calling = Objects.requireNonNull(calling);
        this.called = Objects.requireNonNull(called);
        fillTable();
        pack();
    }

    private void initComponents() {
        final JPanel rootPane = new JPanel();
        rootPane.setBorder(new EmptyBorder(10, 15, 10, 15));
        this.setContentPane(rootPane);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        rootPane.setLayout(new BorderLayout(0, 0));

        jtable = new JTable();
        jtable.setFont(FontTools.getFont10());
        jtable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jtable.setRowSelectionAllowed(true);

        jtable.getTableHeader().setReorderingAllowed(false);
        tableContainer = new JPanel();
        tableContainer.setBorder(BorderFactory.createEtchedBorder());
        tableContainer.setPreferredSize(new Dimension(920, 300));
        tableContainer.setLayout(new BorderLayout());

        this.getContentPane().add(tableContainer, BorderLayout.CENTER);

        footPanel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) footPanel.getLayout();
        flowLayout.setVgap(15);
        flowLayout.setAlignment(FlowLayout.RIGHT);
        flowLayout.setHgap(20);
        getContentPane().add(footPanel, BorderLayout.SOUTH);

        okButton = new JButton();
        footPanel.add(okButton);

        okButton.setText("Select");
        okButton.addActionListener(e -> okButtonActionPerformed());
        cancelButton = new JButton();
        footPanel.add(cancelButton);

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(e -> dispose());
    }

    public void fillTable() {
        tableContainer.removeAll();

        DicomState state = queryWorklist(calling, called);

        List<Attributes> items = state.getDicomRSP();
        if (items != null) {
            DicomParam[] cols = { CFind.PatientName, CFind.PatientID, CFind.PatientBirthDate, CFind.PatientSex,
                CFind.AccessionNumber, ModalityWorklist.ScheduledProcedureStepDescription, CFind.Modality,
                ModalityWorklist.ScheduledStationName };

            TagW[] tags = TagD.getTagFromIDs(Arrays.stream(cols).mapToInt(DicomParam::getTag).toArray());
            int sps = tags.length - 3;

            Object[][] labels = new Object[items.size()][];
            for (int i = 0; i < labels.length; i++) {
                Attributes m = items.get(i);
                Object[] row = new Object[tags.length];

                for (int j = 0; j <= sps; j++) {
                    row[j] = tags[j].getFormattedTagValue(tags[j].getValue(m), null);
                }

                m = m.getNestedDataset(Tag.ScheduledProcedureStepSequence);
                for (int j = sps; j < tags.length; j++) {
                    row[j] = tags[j].getFormattedTagValue(tags[j].getValue(m), null);
                }
                labels[i] = row;
            }
            jtable.setModel(
                new SimpleTableModel(Arrays.stream(tags).map(TagW::getDisplayedName).toArray(String[]::new), labels));
            int height = (jtable.getRowHeight() + jtable.getRowMargin()) * jtable.getRowCount()
                + jtable.getTableHeader().getHeight() + 5;
            tableContainer.setPreferredSize(new Dimension(jtable.getColumnModel().getTotalColumnWidth(), height));
            tableContainer.add(jtable.getTableHeader(), BorderLayout.PAGE_START);
            tableContainer.add(jtable, BorderLayout.CENTER);
            TableColumnAdjuster.pack(jtable);

            jtable.getSelectionModel().addListSelectionListener(event -> {
                int row = jtable.getSelectedRow();
                selectedItem = items.get(row);

            });
        } else {
            tableContainer.setPreferredSize(new Dimension(50, 50));
        }
        tableContainer.revalidate();
        tableContainer.repaint();
    }

    private void applySelection() {
        if (selectedItem != null) {
            DefaultTagable tagable = new DefaultTagable();

            TagW[] addTags = TagD.getTagFromIDs(Tag.StudyInstanceUID, Tag.AdmissionID, Tag.ReferringPhysicianName);
            for (TagW t : addTags) {
                tagable.setTagNoNull(t, t.getValue(selectedItem));
            }

            Attributes seq = selectedItem.getNestedDataset(Tag.ScheduledProcedureStepSequence);
            tagable.setTagNoNull(TagD.get(Tag.StudyDescription),
                TagD.get(Tag.ScheduledProcedureStepDescription).getValue(seq));
            TagW tModality = TagD.get(Tag.Modality);
            tagable.setTagNoNull(tModality, tModality.getValue(seq));
            tagable.setTagNoNull(TagD.get(Tag.StationName), TagD.get(Tag.ScheduledStationName).getValue(seq));

            AcquireManager.getInstance().applyToGlobal(tagable);
        }
    }

    private void okButtonActionPerformed() {
        applySelection();
        dispose();
    }

    private static DicomState queryWorklist(DicomNode calling, DicomNode called) {
        DicomParam stationAet = new DicomParam(Tag.ScheduledStationAETitle, calling.getAet());

        DicomParam[] SPS_RETURN_KEYS = { CFind.Modality, ModalityWorklist.RequestedContrastAgent, stationAet,
            ModalityWorklist.ScheduledProcedureStepStartDate, ModalityWorklist.ScheduledProcedureStepStartTime,
            ModalityWorklist.ScheduledPerformingPhysicianName, ModalityWorklist.ScheduledProcedureStepDescription,
            ModalityWorklist.ScheduledProcedureStepID, ModalityWorklist.ScheduledStationName,
            ModalityWorklist.ScheduledProcedureStepLocation, ModalityWorklist.PreMedication,
            ModalityWorklist.ScheduledProcedureStepStatus };

        DicomParam[] RETURN_KEYS = { CFind.AccessionNumber, CFind.ReferringPhysicianName, CFind.PatientName,
            CFind.PatientID, CFind.PatientBirthDate, CFind.PatientSex, ModalityWorklist.PatientWeight,
            ModalityWorklist.MedicalAlerts, ModalityWorklist.Allergies, ModalityWorklist.PregnancyStatus,
            CFind.StudyInstanceUID, ModalityWorklist.RequestingPhysician, ModalityWorklist.RequestingService,
            ModalityWorklist.RequestedProcedureDescription, ModalityWorklist.AdmissionID, ModalityWorklist.SpecialNeeds,
            ModalityWorklist.CurrentPatientLocation, ModalityWorklist.PatientState,
            ModalityWorklist.RequestedProcedureID, ModalityWorklist.RequestedProcedurePriority,
            ModalityWorklist.PatientTransportArrangements, ModalityWorklist.PlacerOrderNumberImagingServiceRequest,
            ModalityWorklist.FillerOrderNumberImagingServiceRequest,
            ModalityWorklist.ConfidentialityConstraintOnPatientDataDescription };

        AdvancedParams params = new AdvancedParams();
        ConnectOptions connectOptions = new ConnectOptions();
        connectOptions.setConnectTimeout(3000);
        connectOptions.setAcceptTimeout(5000);
        params.setConnectOptions(connectOptions);

        return ModalityWorklist.process(params, calling, called, 0, SPS_RETURN_KEYS, RETURN_KEYS);
    }
}
