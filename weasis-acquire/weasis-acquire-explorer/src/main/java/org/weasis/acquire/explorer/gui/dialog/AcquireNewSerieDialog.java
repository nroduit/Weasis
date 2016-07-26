package org.weasis.acquire.explorer.gui.dialog;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.acquire.explorer.gui.central.AcquireTabPanel;
import org.weasis.core.api.media.data.ImageElement;

@SuppressWarnings("serial")
public class AcquireNewSerieDialog extends JDialog implements PropertyChangeListener {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AcquireNewSerieDialog.class);

    private static final Object[] OPTIONS = { "Validate", "Cancel" };
    private static final String REVALIDATE = "ReValidate";

    private final JTextField serieName = new JTextField();
    private JOptionPane optionPane;

    private AcquireTabPanel acquireTabPanel;
    private List<ImageElement> medias;

    public AcquireNewSerieDialog(AcquireTabPanel acquireTabPanel, final List<ImageElement> medias) {
        this.acquireTabPanel = acquireTabPanel;
        this.medias = medias;
        optionPane = new JOptionPane(initPanel(), JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null,
            OPTIONS, OPTIONS[0]);
        optionPane.addPropertyChangeListener(this);

        setContentPane(optionPane);
        setModal(true);
        pack();
    }

    private JPanel initPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JLabel question = new JLabel("Quel nom souhaitez-vous donner à la série ?");
        panel.add(question, BorderLayout.NORTH);

        panel.add(serieName, BorderLayout.CENTER);

        return panel;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Object action = evt.getNewValue();
        boolean close = true;
        if (action != null) {
            if (OPTIONS[0].equals(action)) {
                if (serieName.getText() != null && !serieName.getText().isEmpty()) {
                    acquireTabPanel.moveElements(new Serie(serieName.getText()), medias);
                } else {
                    JOptionPane.showMessageDialog(this, "PLease provide a name for the Serie",
                        "The Serie name cannot be empty", JOptionPane.ERROR_MESSAGE);
                    optionPane.setValue(REVALIDATE);
                    close = false;
                }
            } else if (action.equals(REVALIDATE)) {
                close = false;
            }
            if (close) {
                clearAndHide();
            }
        }
    }

    public void clearAndHide() {
        serieName.setText(null);
        setVisible(false);
    }

}
