/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.tool;

import bibliothek.gui.dock.common.CLocation;
import br.com.animati.texture.codec.TextureDicomSeries;
import br.com.animati.texture.mpr3dview.EventPublisher;
import br.com.animati.texture.mpr3dview.GUIManager;
import br.com.animati.texture.mpr3dview.ViewTexture;
import br.com.animati.texture.mpr3dview.api.AbstractViewsContainer;
import br.com.animati.texture.mpr3dview.api.ActionWA;
import br.com.animati.texture.mpr3dview.api.GridElement;
import br.com.animati.texture.mpr3dview.internal.Messages;
import br.com.animati.texturedicom.TextureImageCanvas;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.BoundedRangeModel;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ViewerPlugin;

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
        super(BUTTON_NAME, BUTTON_NAME, PluginTool.Type.TOOL, 6);
        
        setDockableWidth(290);
        init();

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

    private void init() {
        setLayout(new BorderLayout());
        menuAccordion.addItem("wl", Messages.getString("ImageTool.wlPanelTitle"),
                getWindowLevelPanel(), true);
        menuAccordion.addItem("volumetric", Messages.getString("ImageTool.volumetricPanelTitle"),
                getVolumetricPanel(), true);
        menuAccordion.addItem("mip", "MIP", getMipPanel(), true);
        menuAccordion.addItem("transform", Messages.getString("ImageTool.transformPanelTitle"), 
                getTransformPanel(), true);
        
        add(menuAccordion, BorderLayout.NORTH);
        
    }

    private JPanel getWindowLevelPanel() {
        
        final String[] comboCmds = new String[] {
            ActionW.PRESET.cmd(), ActionW.LUT_SHAPE.cmd(), ActionW.LUT.cmd(),
            ActionW.FILTER.cmd()};
        final JPanel winLevelPanel = new JPanel();
        setUpPanel(winLevelPanel);
        
        final JSliderW winSlider = createSlider(ActionW.WINDOW.getTitle(), true);
        final JSliderW levSlider = createSlider(ActionW.LEVEL.getTitle(), true);
        final JComboBox presetComboBox = new JComboBox();
        presetComboBox.setName(ActionW.PRESET.cmd());
        final JComboBox lsComboBox = new JComboBox();
        lsComboBox.setName(ActionW.LUT_SHAPE.cmd());
        final JComboBox lutComboBox = new JComboBox();
        lutComboBox.setName(ActionW.LUT.cmd());
        final JComboBox filterComboBox = new JComboBox();
        filterComboBox.setName(ActionW.FILTER.cmd());
        final JComboBox[] combos = new JComboBox[] {presetComboBox, lsComboBox,
            lutComboBox, filterComboBox};
        final JCheckBox invertCh = new JCheckBox(
                Messages.getString("ImageTool.inverse"));
        
        final ActionListener comboListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ("comboBoxChanged".equals(e.getActionCommand()) &&
                        e.getSource() instanceof JComboBox) {
                    JComboBox box = (JComboBox) e.getSource();
                    Object selected = box.getSelectedItem();
                    EventPublisher.getInstance().publish(new PropertyChangeEvent(
                            box, EventPublisher.VIEWER_DO_ACTION
                            + box.getName(), null, selected));
                } else if (e.getSource() instanceof JCheckBox) {
                    JCheckBox check = (JCheckBox) e.getSource();
                    EventPublisher.getInstance().publish(new PropertyChangeEvent(
                            check, EventPublisher.VIEWER_DO_ACTION
                            + ActionW.INVERT_LUT.cmd(), null, check.isSelected()));
                }
            }
        };
        
        EventPublisher.getInstance().addPropertyChangeListener(
                "(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionW.WINDOW.cmd()
                + ")|(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionW.LEVEL.cmd()
                + ")|(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionW.PRESET.cmd()
                + ")|(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionW.LUT_SHAPE.cmd()
                + ")|(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionW.LUT.cmd()
                + ")|(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionW.FILTER.cmd()
                + ")|(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionW.INVERT_LUT.cmd()
                + ")|(" + EventPublisher.VIEWER_SELECTED + ")",
                new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String cmd = evt.getPropertyName();
                if (EventPublisher.VIEWER_SELECTED.equals(cmd)
                        && evt.getNewValue() instanceof GridElement) {
                    GridElement viewer = (GridElement) evt.getNewValue();
                    
                    //All Combos
                    for (int i = 0; i < combos.length; i++) {
                        JComboBox jComboBox = combos[i];
                        String act = comboCmds[i];
                        jComboBox.removeActionListener(comboListener);
                        Object actData = viewer.getActionData(act);
                        Object selection = viewer.getActionValue(act);
                        jComboBox.removeAllItems();
                        if (actData instanceof List) {
                           for (Object object : (List) actData) {
                               jComboBox.addItem(object);
                               jComboBox.setEnabled(true);
                           }
                        } else {
                            jComboBox.setEnabled(false);
                        }
                        jComboBox.setSelectedItem(selection);
                        jComboBox.addActionListener(comboListener);
                    }

                    boolean invValue = JMVUtils.getNULLtoFalse((Boolean)
                            viewer.getActionValue(ActionW.INVERT_LUT.cmd()));
                    invertCh.removeActionListener(comboListener);
                    invertCh.setSelected(invValue);
                    invertCh.addActionListener(comboListener);
                    
                    //WinLevel
                    Object wdata = viewer.getActionData(ActionW.WINDOW.cmd());
                    if (wdata instanceof BoundedRangeModel) {
                        BoundedRangeModel model = (BoundedRangeModel) wdata;
                        winSlider.setModel(model);
                    }
                    Object ldata = viewer.getActionData(ActionW.LEVEL.cmd());
                    if (ldata instanceof BoundedRangeModel) {
                        BoundedRangeModel model = (BoundedRangeModel) ldata;
                        levSlider.setModel(model);
                    }
                    
                } else if (cmd != null && cmd.startsWith(EventPublisher.VIEWER_ACTION_CHANGED)
                        && isViewSelected(evt.getSource())) {
                    if (cmd != null && cmd.endsWith(ActionW.PRESET.cmd())) {
                        presetComboBox.removeActionListener(comboListener);
                        if (evt.getNewValue() instanceof List) {
                            presetComboBox.removeAllItems();
                            for (Object object : (List) evt.getNewValue()) {
                                presetComboBox.addItem(object);
                            }
                        } else {
                            presetComboBox.setSelectedItem(evt.getNewValue());
                        }
                        presetComboBox.addActionListener(comboListener);
                    } else if (cmd != null && cmd.endsWith(ActionW.INVERT_LUT.cmd())) {
                        if (evt.getNewValue() instanceof Boolean) {
                            invertCh.setSelected((Boolean) evt.getNewValue());
                        }
                    } else if (cmd != null && cmd.endsWith(ActionW.LUT.cmd())) {
                        lutComboBox.removeActionListener(comboListener);
                        if (evt.getNewValue() instanceof List) {
                            lutComboBox.removeAllItems();
                            for (Object object : (List) evt.getNewValue()) {
                                lutComboBox.addItem(object);
                            }
                        } else {
                            lutComboBox.setSelectedItem(evt.getNewValue());
                        }
                        lutComboBox.addActionListener(comboListener);
                    } else if (cmd != null && cmd.endsWith(ActionW.LUT_SHAPE.cmd())) {
                        lsComboBox.removeActionListener(comboListener);
                        if (evt.getNewValue() instanceof List) {
                            lsComboBox.removeAllItems();
                            for (Object object : (List) evt.getNewValue()) {
                                lsComboBox.addItem(object);
                            }
                        } else {
                            lsComboBox.setSelectedItem(evt.getNewValue());
                        }
                        lsComboBox.addActionListener(comboListener);
                    } else if (cmd != null && cmd.endsWith(ActionW.FILTER.cmd())) {
                        filterComboBox.removeActionListener(comboListener);
                        if (evt.getNewValue() instanceof List) {
                            filterComboBox.removeAllItems();
                            for (Object object : (List) evt.getNewValue()) {
                                filterComboBox.addItem(object);
                            }
                        } else {
                            filterComboBox.setSelectedItem(evt.getNewValue());
                        }
                        filterComboBox.addActionListener(comboListener);
                    }
                }
            }
        });
        
        JMVUtils.setPreferredWidth(winSlider, 100);
        winLevelPanel.add(winSlider.getParent());
        
        JMVUtils.setPreferredWidth(levSlider, 100);
        winLevelPanel.add(levSlider.getParent());
        
        //Presets Pannel:
        final JPanel presPanel = new JPanel();
        presPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 3));
        presPanel.add(new JLabel(Messages.getString("ImageTool.presets")));
        presetComboBox.setMaximumRowCount(10);
        presPanel.add(presetComboBox);
        winLevelPanel.add(presPanel);
        
        //LutShape panel:
        final JPanel lsPanel = new JPanel();
        lsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 3));
        lsPanel.add(new JLabel("Lut Shape"));
        lsComboBox.setMaximumRowCount(10);
        lsPanel.add(lsComboBox);
        winLevelPanel.add(lsPanel);
        
        //LUT Pannel:
        final JPanel lutPanel = new JPanel();
        lutPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 3));
        lutPanel.add(new JLabel("LUT"));
        lutComboBox.setMaximumRowCount(10);
        lutPanel.add(lutComboBox);

        lutPanel.add(invertCh);
        winLevelPanel.add(lutPanel);
        
        //Presets Pannel:
        final JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 3));
        filterPanel.add(new JLabel(Messages.getString("ImageTool.filters")));
        filterComboBox.setMaximumRowCount(10);
        filterPanel.add(filterComboBox);
        winLevelPanel.add(filterPanel);
        
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
                ViewerPlugin plugin =
                        GUIManager.getInstance().getSelectedViewerPlugin();
                if (plugin instanceof AbstractViewsContainer) {
                    Component component = ((AbstractViewsContainer) plugin)
                            .getSelectedPane().getComponent();
                    if (component instanceof ViewTexture) {
                        TextureDicomSeries imSeries =
                            ((ViewTexture) component).getSeriesObject();
                        imSeries.textureLogInfo.showAsDialog(plugin);
                    }
                }
            }
            
        });        
        //Center Slicing
        final JPanel scPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        final JCheckBox scCheckBox = new JCheckBox(
                ActionWA.VOLUM_CENTER_SLICING.getTitle());
        scCheckBox.setName(ActionWA.VOLUM_CENTER_SLICING.cmd());
        scPanel.add(scCheckBox);
        scPanel.add(Box.createRigidArea(new Dimension(20, 10)));
        scPanel.add(logInfoButton);
        volumePanel.add(scPanel);
        
        //volumetricLighting
        final JPanel vlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
        final JCheckBox vlCheckBox = new JCheckBox(
                ActionWA.VOLUM_LIGHT.getTitle());
        vlCheckBox.setName(ActionWA.VOLUM_LIGHT.cmd());
        vlPanel.add(vlCheckBox);
        volumePanel.add(vlPanel);
        
        final ActionListener cbListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() instanceof JCheckBox) {
                    JCheckBox check = (JCheckBox) e.getSource();
                    EventPublisher.getInstance().publish(new PropertyChangeEvent(
                            check, EventPublisher.VIEWER_DO_ACTION
                            + check.getName(), null, check.isSelected()));
                }
            }
        };
        
        scCheckBox.addActionListener(cbListener);
        vlCheckBox.addActionListener(cbListener);
        
        final JPanel qualPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
        final JSliderW qualSlider = createSlider(ActionWA.VOLUM_QUALITY.getTitle(), true);
        qualSlider.setModel(new DefaultBoundedRangeModel(300, 1, 75, 2000));
        
        final ChangeListener slListener = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        qualSlider, EventPublisher.VIEWER_DO_ACTION
                        + ActionWA.VOLUM_QUALITY.cmd(), null,
                        qualSlider.getValue()));
            }
        };
        qualSlider.addChangeListener(slListener);
        qualPanel.add(qualSlider.getParent());
        volumePanel.add(qualPanel);
        
        EventPublisher.getInstance().addPropertyChangeListener(
                EventPublisher.VIEWER_SELECTED, new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getNewValue() instanceof GridElement) {
                    GridElement view = (GridElement) evt.getNewValue();
                    
                    logInfoButton.setEnabled(view instanceof GridElement
                            && ((GridElement) view).getComponent()
                            instanceof ViewTexture);

                    Boolean volumetric = (Boolean) view.getActionValue(
                            ActionWA.VOLUM_RENDERING.cmd());
                    boolean isVolumetric = JMVUtils.getNULLtoFalse(volumetric);
                    
                    if (isVolumetric) {
                        scCheckBox.removeActionListener(cbListener);
                        vlCheckBox.removeActionListener(cbListener);
                        qualSlider.removeChangeListener(slListener);
                        
                        scCheckBox.setSelected(JMVUtils.getNULLtoFalse(
                                (Boolean) view.getActionValue(
                            ActionWA.VOLUM_CENTER_SLICING.cmd())));
                        vlCheckBox.setSelected(JMVUtils.getNULLtoFalse(
                                (Boolean) view.getActionValue(
                            ActionWA.VOLUM_LIGHT.cmd())));
                        Integer quality = (Integer) view.getActionValue(
                            ActionWA.VOLUM_QUALITY.cmd());
                        qualSlider.setValue(quality);
                        
                        scCheckBox.addActionListener(cbListener);
                        vlCheckBox.addActionListener(cbListener);
                        qualSlider.addChangeListener(slListener);
                    }
                    
                    scCheckBox.setEnabled(isVolumetric);
                    vlCheckBox.setEnabled(isVolumetric);
                    qualSlider.setEnabled(isVolumetric);
                    
                }
            }
        });
               
        return volumePanel;
    }

    private JPanel getTransformPanel() {
        final JPanel transPanel = new JPanel();
        setUpPanel(transPanel);
        
        final JSliderW zoomSlider = createSlider(ActionW.ZOOM.getTitle(), false);
        transPanel.add(zoomSlider.getParent());
        
        // Rotation
        final JSliderW rotationSlider = createSlider(ActionW.ROTATION.getTitle(), false);
        transPanel.add(rotationSlider.getParent());
          
        //Smoothing
        final JPanel panelSmoo = 
                new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
        final JCheckBox smooCheckBox = new JCheckBox(
                Messages.getString("ImageTool.smoothing"));
        panelSmoo.add(smooCheckBox);
        transPanel.add(panelSmoo);
        final ActionListener smooListener = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        smooCheckBox, EventPublisher.VIEWER_DO_ACTION 
                        + ActionWA.SMOOTHING.cmd(), null,
                        smooCheckBox.isSelected()));
            }
            
        };
        smooCheckBox.addActionListener(smooListener);
        
        // Flip Horizontal
        final JPanel panelFlip = 
                new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        final JCheckBox flipCheckBox = new JCheckBox(
                Messages.getString("ImageTool.Flip"));
        panelFlip.add(flipCheckBox);
        transPanel.add(panelFlip);
        final ActionListener flipListener = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        flipCheckBox, EventPublisher.VIEWER_DO_ACTION 
                        + ActionW.FLIP.cmd(), null,
                        flipCheckBox.isSelected()));
            }
            
        };
        flipCheckBox.addActionListener(flipListener);
        
        EventPublisher.getInstance().addPropertyChangeListener(
                "(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionW.FLIP.cmd()
                + ")|(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionWA.SMOOTHING.cmd()
                + ")|(" + EventPublisher.VIEWER_SELECTED + ")", new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String cmd = evt.getPropertyName();
                if (EventPublisher.VIEWER_SELECTED.equals(cmd)
                        && evt.getNewValue() instanceof GridElement) {
                    GridElement viewer = (GridElement) evt.getNewValue();
                    Object zoomData = viewer.getActionData(ActionW.ZOOM.cmd());
                    if (zoomData instanceof BoundedRangeModel) {
                        zoomSlider.setModel((BoundedRangeModel) zoomData);
                        zoomSlider.repaint();
                    }
                    
                    Object rotationData = viewer.getActionData(ActionW.ROTATION.cmd());
                    if (rotationData instanceof BoundedRangeModel) {
                        rotationSlider.setModel((BoundedRangeModel) rotationData);
                        rotationSlider.repaint();
                    }
                    
                    Object smooData = viewer.getActionValue(
                            ActionWA.SMOOTHING.cmd());
                    if (smooData instanceof Boolean) {
                        smooCheckBox.setSelected((Boolean) smooData);
                        smooCheckBox.setEnabled(true);
                    } else {
                        smooCheckBox.setEnabled(false);
                    }
                    
                    Object flipData = viewer.getActionValue(
                            ActionW.FLIP.cmd());
                    if (flipData instanceof Boolean) {
                        flipCheckBox.setSelected((Boolean) flipData);
                        flipCheckBox.setEnabled(true);
                    } else {
                        flipCheckBox.setEnabled(false);
                    }
                } else if (cmd != null && cmd.endsWith(ActionW.FLIP.cmd())) {
                    if (evt.getNewValue() instanceof Boolean) {
                        Boolean flag = (Boolean) evt.getNewValue();
                        flipCheckBox.setSelected(flag);
                    }
                } else if (cmd != null && cmd.endsWith(ActionWA.SMOOTHING.cmd())) {
                    if (evt.getNewValue() instanceof Boolean) {
                        Boolean flag = (Boolean) evt.getNewValue();
                        smooCheckBox.setSelected(flag);
                    }
                }                
            }
        });
        
        return transPanel;
    }
    
    private void setUpPanel(JPanel panel) {
        panel.setAlignmentY(Component.TOP_ALIGNMENT);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        
        //Leave a space after title
        panel.add(Box.createRigidArea(new Dimension(5,10)));
    }
    
    private JSliderW createSlider(final String title, boolean showTitleValue) {
        final JPanel slPanel = new JPanel();
        slPanel.setLayout(new BoxLayout(slPanel, BoxLayout.Y_AXIS));
        final TitledBorder border = new TitledBorder(title);
        border.setTitleFont(FontTools.getFont11());
        slPanel.setBorder(border);
        final JSliderW slider = new JSliderW(JSliderW.HORIZONTAL) {
            public void setModel(BoundedRangeModel newModel) {
                super.setModel(newModel);
                fireStateChanged();
            }
        };
        slPanel.add(slider);
        if (showTitleValue) {
            slider.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    border.setTitle(title + ": " + slider.getValue() + 
                            " ["+ slider.getMinimum() +" / "+ slider.getMaximum() +"]");
                    slPanel.repaint();
                }
            });
        }
        return slider;
    }

    private JPanel getMipPanel() {
        JPanel panel = new JPanel();
        setUpPanel(panel);
        
        final JComboBox box = new JComboBox();
        final JPanel boxPanel = new JPanel();
        boxPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 3));
        boxPanel.add(new JLabel("MIP Type:"));
        box.setMaximumRowCount(10);
        boxPanel.add(box);
        panel.add(boxPanel);

        final JSliderW mipSl = createSlider("Mip", true);
        final ChangeListener slListener = new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        mipSl, EventPublisher.VIEWER_DO_ACTION
                        + ActionWA.MIP_DEPTH.cmd(), null,
                        (double) mipSl.getValue() / mipSl.getMaximum()));
            }
        };
        
        final ActionListener boxListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        mipSl, EventPublisher.VIEWER_DO_ACTION
                        + ActionWA.MIP_OPTION.cmd(), null,
                        box.getSelectedItem()));
                mipSl.setEnabled(!(box.getSelectedItem().equals(
                        TextureImageCanvas.MipOption.None)));
            }
        };
        
        mipSl.setValue(1);
        mipSl.addChangeListener(slListener);
        
        EventPublisher.getInstance().addPropertyChangeListener(
                "(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionWA.MIP_OPTION.cmd()  
                + ")|(" + EventPublisher.VIEWER_ACTION_CHANGED + ActionWA.MIP_DEPTH.cmd()
                + ")|(" + EventPublisher.VIEWER_SELECTED + ")", new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String cmd = evt.getPropertyName();
                if (EventPublisher.VIEWER_SELECTED.equals(cmd)
                        && evt.getNewValue() instanceof GridElement) {
                    GridElement view = (GridElement) evt.getNewValue();
                    
                    mipSl.removeChangeListener(slListener);
                    Object data = view.getActionData(ActionWA.MIP_DEPTH.cmd());
                    Object depth = view.getActionValue(ActionWA.MIP_DEPTH.cmd());
                    if (data instanceof Integer) {
                        mipSl.setMaximum((Integer) data);
                        if (depth instanceof Double) {
                            mipSl.setValue((int) Math.round(
                                    ((Integer) data) * ((Double) depth)));
                        }
                        mipSl.setEnabled(true);
                    } else {
                        mipSl.setEnabled(false);
                    }
                    mipSl.addChangeListener(slListener);
                    
                    box.removeActionListener(boxListener);
                    TextureImageCanvas.MipOption[] actionData =
                            (TextureImageCanvas.MipOption[]) view.getActionData(
                            ActionWA.MIP_OPTION.cmd());
                     TextureImageCanvas.MipOption opt =
                             (TextureImageCanvas.MipOption) view.getActionValue(
                             ActionWA.MIP_OPTION.cmd());
                    box.removeAllItems();
                    if (actionData != null && actionData.length > 0) {
                        for (int i = 0; i < actionData.length; i++) {
                            box.addItem(actionData[i]); 
                        }
                        box.setEnabled(true);
                    } else {
                        box.setEnabled(false);
                    }
                    if (opt != null) {
                        box.setSelectedItem(opt);
                        if (opt.equals(TextureImageCanvas.MipOption.None)) {
                            mipSl.setEnabled(false);
                        }
                    }
                    box.addActionListener(boxListener);
                    
                } else if (cmd != null && cmd.endsWith(ActionWA.MIP_OPTION.cmd())) {
                    box.removeActionListener(boxListener);
                    if (evt.getNewValue() instanceof List) {
                        box.removeAllItems();
                        for (Object object : (List) evt.getNewValue()) {
                            box.addItem(object);
                        }
                    } else {
                        box.setSelectedItem(evt.getNewValue());
                    }                    
                    box.addActionListener(boxListener);                         
                } else if (cmd != null && cmd.endsWith(ActionWA.MIP_DEPTH.cmd())) {
                    mipSl.removeChangeListener(slListener);
                    final Object data = ((Object[]) evt.getNewValue())[0];
                    final Object depth = ((Object[]) evt.getNewValue())[1];
                    if (data instanceof Integer) {
                        mipSl.setMaximum((Integer) data);
                        if (depth instanceof Double) {
                            mipSl.setValue((int) Math.round(
                                    ((Integer) data) * ((Double) depth)));
                        }
                        mipSl.setEnabled(false);
                    }
                    mipSl.addChangeListener(slListener);
                }
            }
        });
        panel.add(mipSl.getParent());
        return panel;
    }
    
    private boolean isViewSelected(Object source) {
        if (source != null) {
            ViewerPlugin selectedViewerPlugin = GUIManager.getInstance().getSelectedViewerPlugin();
            if (selectedViewerPlugin instanceof AbstractViewsContainer) {
                GridElement selectedPane = ((AbstractViewsContainer) selectedViewerPlugin).getSelectedPane();
                if (source.equals(selectedPane)) {
                    return true;
                } else {
                    return source.equals(selectedPane.getComponent());
                }
            }
        }
        return false;
    }
}
