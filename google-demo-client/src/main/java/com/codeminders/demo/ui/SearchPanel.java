package com.codeminders.demo.ui;

import com.codeminders.demo.GoogleAPIClient;
import com.codeminders.demo.model.StudyQuery;
import com.codeminders.demo.ui.dicomstore.DicomStoreSelector;
import com.codeminders.demo.ui.dicomstore.LoadStudiesTask;
import org.jdatepicker.DateModel;
import org.jdatepicker.impl.JDatePanelImpl;
import org.jdatepicker.impl.JDatePickerImpl;
import org.jdatepicker.impl.UtilDateModel;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Properties;

import static javax.swing.BoxLayout.LINE_AXIS;
import static javax.swing.BoxLayout.PAGE_AXIS;

public class SearchPanel extends JPanel {

    private final JTextField patientName = textField();
    private final JTextField patientId = textField();
    private final JDatePickerImpl startDate = createDatePicker();
    private final JDatePickerImpl endDate = createDatePicker();
    private final JTextField accessionNumber = textField();
    private final JTextField referringPhd = textField();

    private final GoogleAPIClient googleAPIClient;
    private final DicomStoreSelector storeSelector;

    public SearchPanel(GoogleAPIClient googleAPIClient, DicomStoreSelector storeSelector) {
        this.googleAPIClient = googleAPIClient;
        this.storeSelector = storeSelector;
        initSearchPanel();
    }

    private void initSearchPanel() {
        BoxLayout layout = new BoxLayout(this, PAGE_AXIS);
        setLayout(layout);
        Border border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 2, Color.GRAY),
                BorderFactory.createEmptyBorder(5, 5, 5, 15));
        setBorder(border);
        setPreferredSize(new Dimension(300, 300));

        JLabel searchLabel = label("Search");
        searchLabel.setFont(new Font("Sans-serif", Font.BOLD, 18));
        add(searchLabel);

        add(Box.createVerticalStrut(10));

        JLabel studyDateLabel = label("Study date");
        add(studyDateLabel);
        JPanel datePanel = new JPanel();
        BoxLayout datePanelLayout = new BoxLayout(datePanel, LINE_AXIS);
        datePanel.setLayout(datePanelLayout);
        datePanel.add(startDate);
        datePanel.add(endDate);
        add(datePanel);

        add(Box.createVerticalStrut(20));

        JLabel patientNameLabel = label("Patient Name");
        add(patientNameLabel);
        add(patientName);

        add(Box.createVerticalStrut(20));

        JLabel patientIdLabel = label("Patient ID");
        add(patientIdLabel);
        add(patientId);

        add(Box.createVerticalStrut(20));

        JLabel accessionNumberLabel = label("Accession number");
        add(accessionNumberLabel);
        add(accessionNumber);

        add(Box.createVerticalStrut(20));

        JLabel referringPhdLabel = label("Referring Physician");
        add(referringPhdLabel);
        add(referringPhd);

        add(Box.createVerticalStrut(20));

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener((action) -> reloadTable());
        JButton reset = new JButton("Reset");
        reset.addActionListener((action) -> {
            clearSearchForm();
            reloadTable();
        });
        JPanel buttonPanel = new JPanel();
        BoxLayout buttonPanelLayout = new BoxLayout(buttonPanel, LINE_AXIS);
        buttonPanel.setLayout(buttonPanelLayout);
        buttonPanel.add(searchButton);
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(reset);
        add(buttonPanel);

        add(Box.createVerticalGlue());
    }

    private void reloadTable() {
        storeSelector.getCurrentStore().ifPresent((store) -> {
            new LoadStudiesTask(store, googleAPIClient, storeSelector, buildQuery()).execute();
        });
    }

    private void clearSearchForm() {
        patientName.setText("");
        patientId.setText("");
        accessionNumber.setText("");
        referringPhd.setText("");
        startDate.getModel().setSelected(false);
        endDate.getModel().setSelected(false);
    }

    private StudyQuery buildQuery() {
        StudyQuery query = new StudyQuery();
        query.setPatientName(patientName.getText());
        query.setPatientId(patientId.getText());
        query.setAccessionNumber(accessionNumber.getText());
        query.setPhysicianName(referringPhd.getText());
        DateModel<?> startDateModel = startDate.getModel();
        if (startDateModel.isSelected()) {
            query.setStartDate(LocalDate.of(startDateModel.getYear(), startDateModel.getMonth() + 1, startDateModel.getDay()));
        }

        DateModel<?> endDateModel = endDate.getModel();
        if (endDateModel.isSelected()) {
            query.setEndDate(LocalDate.of(endDateModel.getYear(), endDateModel.getMonth() + 1, endDateModel.getDay()));
        }

        return query;
    }

    private JDatePickerImpl createDatePicker() {
        UtilDateModel model = new UtilDateModel();
        Properties p = new Properties();
        p.put("text.today", "Today");
        p.put("text.month", "Month");
        p.put("text.year", "Year");
        JDatePanelImpl datePanel = new JDatePanelImpl(model, p);
        JDatePickerImpl datePicker = new JDatePickerImpl(datePanel, new DateLabelFormatter());
        return datePicker;
    }

    private JTextField textField() {
        JTextField result = new JTextField();
        result.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, result.getPreferredSize().height) );

        return result;
    }

    private JLabel label(String text) {
        JLabel result = new JLabel(text);

        result.setAlignmentX(CENTER_ALIGNMENT);
        result.setHorizontalTextPosition(SwingConstants.CENTER);

        return result;
    }

    public class DateLabelFormatter extends JFormattedTextField.AbstractFormatter {

        private String datePattern = "yyyy-MM-dd";
        private SimpleDateFormat dateFormatter = new SimpleDateFormat(datePattern);

        @Override
        public Object stringToValue(String text) throws ParseException {
            return dateFormatter.parseObject(text);
        }

        @Override
        public String valueToString(Object value) throws ParseException {
            if (value != null) {
                Calendar cal = (Calendar) value;
                return dateFormatter.format(cal.getTime());
            }

            return "";
        }

    }
}
