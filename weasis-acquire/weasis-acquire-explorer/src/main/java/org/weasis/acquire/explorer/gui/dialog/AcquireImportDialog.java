package org.weasis.acquire.explorer.gui.dialog;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.acquire.explorer.gui.list.AcquireThumbnailListPane;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;

public class AcquireImportDialog extends JDialog implements PropertyChangeListener {
    private static final long serialVersionUID = -8736946182228791444L;

    private final AcquireThumbnailListPane<? extends MediaElement<?>> mainPanel;

    private final Object[] options = { "Validate", "Cancel" };
    private final static String REVALIDATE = "ReValidate";

    private final JTextField serieName = new JTextField();
    private final ButtonGroup btnGrp = new ButtonGroup();

    private final JRadioButton btn1 = new JRadioButton("Ne pas regrouper");
    private final JRadioButton btn2 = new JRadioButton("Regrouper par dates");
    private final JRadioButton btn3 = new JRadioButton("Regrouper la série sous le nom");

    private Serie serieType = Serie.DEFAULT_SERIE;
    private JOptionPane optionPane;

    private List<ImageElement> mediaList;

    public AcquireImportDialog(AcquireThumbnailListPane<? extends MediaElement<?>> mainPanel,
        List<ImageElement> mediaList) {
        super();
        this.mainPanel = mainPanel;
        this.mediaList = mediaList;
        optionPane = new JOptionPane(initPanel(), JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null,
            options, options[0]);
        optionPane.addPropertyChangeListener(this);

        setContentPane(optionPane);
        setModal(true);
        setLocationRelativeTo(null);
        pack();
    }

    private JPanel initPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        JLabel question = new JLabel("Comment souhaitez vous regrouper cet ensemble d'images ?");
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        panel.add(question, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        panel.add(btn1, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        panel.add(btn2, c);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.anchor = GridBagConstraints.WEST;
        panel.add(btn3, c);

        JMVUtils.setPreferredWidth(serieName, 150);
        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        panel.add(serieName, c);

        btnGrp.add(btn1);
        btnGrp.add(btn2);
        btnGrp.add(btn3);
        btnGrp.setSelected(btn1.getModel(), true);

        return panel;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Object action = evt.getNewValue();
        if (action != null) {
            boolean close = true;
            if (action.equals(options[0])) {
                if (btnGrp.getSelection().equals(btn1.getModel())) {
                    serieType = Serie.DEFAULT_SERIE;
                } else if (btnGrp.getSelection().equals(btn2.getModel())) {
                    serieType = Serie.DATE_SERIE;
                } else {
                    if (serieName.getText() != null && !serieName.getText().isEmpty()) {
                        serieType = new Serie(serieName.getText());
                    } else {
                        JOptionPane.showMessageDialog(this, "PLease provide a name for the Serie",
                            "The Serie name cannot be empty", JOptionPane.ERROR_MESSAGE);
                        optionPane.setValue(REVALIDATE);
                        close = false;
                    }
                }
                if (close) {
                    AcquireManager.importImages(serieType, mediaList);
                    mainPanel.getCentralPane().setSelectedAndGetFocus();
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
