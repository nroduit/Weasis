/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.tool;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;

import bibliothek.gui.dock.common.CLocation;
import br.com.animati.texture.mpr3dview.GUIManager;
import br.com.animati.texture.mpr3dview.ViewTexture;
import br.com.animati.texture.mpr3dview.api.ActionWA;
import br.com.animati.texture.mpr3dview.internal.Messages;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 23 jul.
 */
public class ImageTool extends PluginTool {

    public static final String BUTTON_NAME = Messages.getString("ImageTool.title");

    private MenuAccordion menuAccordion = new MenuAccordion();
    private final JScrollPane rootPane = new JScrollPane();

    public ImageTool() {
        super(BUTTON_NAME, BUTTON_NAME, PluginTool.Type.TOOL, 20);
        dockable.setTitleIcon(new ImageIcon(ImageTool.class.getResource("/icon/16x16/mpr3d.png"))); //$NON-NLS-1$
        setDockableWidth(290);
        init();

    }

    private void init() {
        setLayout(new BorderLayout());
        menuAccordion.addItem("wl", Messages.getString("ImageTool.wlPanelTitle"), getWindowLevelPanel(), true);
        menuAccordion.addItem("volumetric", Messages.getString("ImageTool.volumetricPanelTitle"), getVolumetricPanel(),
            true);
        menuAccordion.addItem("mip", "MIP", getMipPanel(), true);
        menuAccordion.addItem("transform", Messages.getString("ImageTool.transformPanelTitle"), getTransformPanel(),
            true);

        add(menuAccordion, BorderLayout.NORTH);

    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        //
    }

    @Override
    public Component getToolComponent() {
        JViewport viewPort = rootPane.getViewport();
        if (viewPort == null) {
            viewPort = new JViewport();
            rootPane.setViewport(viewPort);
        }
        if (viewPort.getView() != this) {
            viewPort.setView(this);
        }
        return rootPane;
    }

    private void setUpPanel(JPanel panel) {
        panel.setAlignmentY(Component.TOP_ALIGNMENT);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Leave a space after title
        panel.add(Box.createRigidArea(new Dimension(5, 10)));
    }

    private JPanel getWindowLevelPanel() {
        final JPanel winLevelPanel = new JPanel();
        setUpPanel(winLevelPanel);

        ActionState winAction = GUIManager.getInstance().getAction(ActionW.WINDOW);
        if (winAction instanceof SliderChangeListener) {
            final JSliderW windowSlider = ((SliderChangeListener) winAction).createSlider(2, true);
            JMVUtils.setPreferredWidth(windowSlider, 100);
            winLevelPanel.add(windowSlider.getParent());
        }

        ActionState levelAction = GUIManager.getInstance().getAction(ActionW.LEVEL);
        if (levelAction instanceof SliderChangeListener) {
            final JSliderW levelSlider = ((SliderChangeListener) levelAction).createSlider(2, true);
            JMVUtils.setPreferredWidth(levelSlider, 100);
            winLevelPanel.add(levelSlider.getParent());
        }

        ActionState presetAction = GUIManager.getInstance().getAction(ActionW.PRESET);
        if (presetAction instanceof ComboItemListener) {
            final JPanel panel_3 = new JPanel();
            panel_3.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel presetsLabel = new JLabel();
            panel_3.add(presetsLabel);
            presetsLabel.setText(Messages.getString("ImageTool.presets") + StringUtil.COLON); //$NON-NLS-1$
            final JComboBox presetComboBox = ((ComboItemListener) presetAction).createCombo(160);
            presetComboBox.setMaximumRowCount(10);
            panel_3.add(presetComboBox);
            winLevelPanel.add(panel_3);
        }

        // ActionState lutShapeAction = GUIManager.getInstance().getAction(ActionW.LUT_SHAPE);
        // if (lutShapeAction instanceof ComboItemListener) {
        // final JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
        // final JLabel label = new JLabel(ActionW.LUT_SHAPE.getTitle() + StringUtil.COLON);
        // pane.add(label);
        // final JComboBox combo = ((ComboItemListener) lutShapeAction).createCombo(140);
        // combo.setMaximumRowCount(10);
        // pane.add(combo);
        // winLevelPanel.add(pane);
        // }

        ActionState lutAction = GUIManager.getInstance().getAction(ActionW.LUT);
        if (lutAction instanceof ComboItemListener) {
            final JPanel panel_4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel lutLabel = new JLabel();
            lutLabel.setText("LUT" + StringUtil.COLON); //$NON-NLS-1$
            panel_4.add(lutLabel);
            final JComboBox lutcomboBox = ((ComboItemListener) lutAction).createCombo(140);
            panel_4.add(lutcomboBox);
            ActionState invlutAction = GUIManager.getInstance().getAction(ActionW.INVERT_LUT);
            if (invlutAction instanceof ToggleButtonListener) {
                panel_4
                    .add(((ToggleButtonListener) invlutAction).createCheckBox(Messages.getString("ImageTool.inverse"))); //$NON-NLS-1$
            }
            winLevelPanel.add(panel_4);
        }

        ActionState filterAction = GUIManager.getInstance().getAction(ActionW.FILTER);
        if (filterAction instanceof ComboItemListener) {
            final JPanel panel_4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel lutLabel = new JLabel();
            lutLabel.setText(Messages.getString("ImageTool.filters") + StringUtil.COLON); //$NON-NLS-1$
            panel_4.add(lutLabel);
            final JComboBox filtercomboBox = ((ComboItemListener) filterAction).createCombo(160);
            panel_4.add(filtercomboBox);
            winLevelPanel.add(panel_4);
        }

        return winLevelPanel;
    }

    private JPanel getVolumetricPanel() {
        final JPanel volumePanel = new JPanel();
        setUpPanel(volumePanel);
        final JButton logInfoButton = new JButton();
        logInfoButton.setIcon(new ImageIcon(ImageTool.class.getResource("/icon/24x24/info.png")));
        logInfoButton.setToolTipText(Messages.getString("ImageTool.log"));
        logInfoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ViewCanvas<DicomImageElement> view = GUIManager.getInstance().getSelectedViewPane();
                if (view instanceof ViewTexture) {
                    if (((ViewTexture) view).getSeriesObject() != null) {
                        (((ViewTexture) view).getSeriesObject()).textureLogInfo.showAsDialog(logInfoButton);
                    }
                }
            }

        });

        ActionState slicingAction = GUIManager.getInstance().getAction(ActionWA.VOLUM_CENTER_SLICING);
        if (slicingAction instanceof ToggleButtonListener) {
            JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 3));
            pane.add(((ToggleButtonListener) slicingAction).createCheckBox(ActionWA.VOLUM_CENTER_SLICING.getTitle()));
            pane.add(Box.createRigidArea(new Dimension(20, 10)));
            pane.add(logInfoButton);
            volumePanel.add(pane);
        }

        ActionState lightAction = GUIManager.getInstance().getAction(ActionWA.VOLUM_LIGHT);
        if (lightAction instanceof ToggleButtonListener) {
            JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 3));
            pane.add(((ToggleButtonListener) lightAction).createCheckBox(ActionWA.VOLUM_LIGHT.getTitle()));
            volumePanel.add(pane);
        }

        ActionState qualityAction = GUIManager.getInstance().getAction(ActionWA.VOLUM_QUALITY);
        if (qualityAction instanceof SliderChangeListener) {
            final JSliderW frameSlider = ((SliderChangeListener) qualityAction).createSlider(0, true);
            JMVUtils.setPreferredWidth(frameSlider, 100);
            volumePanel.add(frameSlider.getParent());
        }
        return volumePanel;
    }

    private JPanel getTransformPanel() {
        final JPanel transPanel = new JPanel();
        setUpPanel(transPanel);

        ActionState sequence = GUIManager.getInstance().getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderChangeListener) {
            final JSliderW frameSlider = ((SliderChangeListener) sequence).createSlider(0, true);
            JMVUtils.setPreferredWidth(frameSlider, 100);
            transPanel.add(frameSlider.getParent());
        }

        ActionState zoomAction = GUIManager.getInstance().getAction(ActionW.ZOOM);
        if (zoomAction instanceof SliderChangeListener) {
            final JSliderW zoomSlider = ((SliderChangeListener) zoomAction).createSlider(0, true);
            JMVUtils.setPreferredWidth(zoomSlider, 100);
            transPanel.add(zoomSlider.getParent());
        }

        ActionState rotateAction = GUIManager.getInstance().getAction(ActionW.ROTATION);
        if (rotateAction instanceof SliderChangeListener) {
            final JSliderW rotationSlider = ((SliderChangeListener) rotateAction).createSlider(5, true);
            JMVUtils.setPreferredWidth(rotationSlider, 100);
            transPanel.add(rotationSlider.getParent());
        }
        ActionState flipAction = GUIManager.getInstance().getAction(ActionW.FLIP);
        if (flipAction instanceof ToggleButtonListener) {
            JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 3));
            pane.add(((ToggleButtonListener) flipAction).createCheckBox("Flip Horizontally (after rotation)"));
            transPanel.add(pane);
        }

        ActionState smoothAction = GUIManager.getInstance().getAction(ActionWA.SMOOTHING);
        if (smoothAction instanceof ToggleButtonListener) {
            JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 3));
            pane.add(((ToggleButtonListener) smoothAction).createCheckBox(Messages.getString("ImageTool.smoothing"))); //$NON-NLS-1$
            transPanel.add(pane);
        }

        return transPanel;
    }

    private JPanel getMipPanel() {
        JPanel panel = new JPanel();
        setUpPanel(panel);

        ActionState lutShapeAction = GUIManager.getInstance().getAction(ActionWA.MIP_OPTION);
        if (lutShapeAction instanceof ComboItemListener) {
            final JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel label = new JLabel(ActionWA.MIP_OPTION.getTitle() + StringUtil.COLON);
            pane.add(label);
            final JComboBox combo = ((ComboItemListener) lutShapeAction).createCombo(140);
            combo.setMaximumRowCount(10);
            pane.add(combo);
            panel.add(pane);
        }

        ActionState mipDepth = GUIManager.getInstance().getAction(ActionWA.MIP_DEPTH);
        if (mipDepth instanceof SliderChangeListener) {
            final JSliderW frameSlider = ((SliderChangeListener) mipDepth).createSlider(0, true);
            JMVUtils.setPreferredWidth(frameSlider, 100);
            panel.add(frameSlider.getParent());
        }
        return panel;
    }
}
