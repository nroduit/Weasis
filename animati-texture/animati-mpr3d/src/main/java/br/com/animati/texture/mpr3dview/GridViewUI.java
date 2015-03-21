/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview;

import br.com.animati.texture.mpr3dview.api.AbstractViewsContainer;
import br.com.animati.texture.mpr3dview.api.ActionWA;
import br.com.animati.texture.mpr3dview.api.GridElement;
import br.com.animati.texture.mpr3dview.api.ViewCore;
import br.com.animati.texture.mpr3dview.api.ViewsGrid;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.display.PresetWindowLevel;

/**
 * Common elements and actions for Texture and Jai views.
 * 
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 10 Out.
 */
public class GridViewUI implements GridElement, FocusListener, KeyListener {
    
    /** Class logger. */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(GridViewUI.class);
    
    /** Maximun scale (limit of zoom). see DefaultViewModel.SCALE_MAX */
    public static final double SCALE_MAX = 12.0;
    /** Minimun scale (limit of zoom). see DefaultViewModel.SCALE_MIN */
    public static final double SCALE_MIN = 1.0 / 12.0;
    
    protected static final Color focusColor = Color.orange;
    private final Border normalBorder = new EtchedBorder(BevelBorder.LOWERED,
            Color.gray, Color.white);
    private final Border focusBorder = new EtchedBorder(BevelBorder.LOWERED,
            focusColor, focusColor);
    
    protected final ViewCore viewCore;
    
    private String profile;
    
    private TexturePannerListener panAction =
            new TexturePannerListener(ActionW.PAN) {
        @Override
        public void pointChanged(int x, int y) {
            Dimension offset = (Dimension) new Dimension(x, y);
            viewCore.moveImageOffset(offset.width, offset.height);
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    GridViewUI.this,
                    EventPublisher.VIEWER_ACTION_CHANGED + ActionW.PAN.cmd(),
                    null, offset));
        }
    };
    
    public GridViewUI(ViewCore core) {
        viewCore = core;
        
        viewCore.getComponent().addFocusListener(this);
        viewCore.getComponent().setFocusable(true);
        viewCore.registerDefaultListeners();
        
        if (viewCore.getComponent() instanceof JComponent) {
            ((JComponent) viewCore.getComponent()).setBorder(normalBorder);
        }
    }
    
    public void center() {
        viewCore.fixPosition();
    }
    
    @Override
    public void setSeries(MediaSeries<DicomImageElement> series) {
        viewCore.setSeries(series);
    }
    
    @Override
    public MediaSeries getSeries() {
        return viewCore.getSeries();
    }

    @Override
    public boolean hasContent() {
        return viewCore.hasContent();
    }
    
    /**
     * What needs to hapen when one serie is being closed.
     * @param series Series that closed.
     */
    public void closingSeries(MediaSeries series) {
        if (series != null) {
            boolean open = false;
            synchronized (UIManager.VIEWER_PLUGINS) {
                List<ViewerPlugin<?>> plugins = UIManager.VIEWER_PLUGINS;
                pluginList: for (final ViewerPlugin plugin : plugins) {
                    List<MediaSeries> openSeries = plugin.getOpenSeries();
                    if (openSeries != null) {
                        for (MediaSeries s : openSeries) {
                            if (series == s) {
                                open = true;
                                break pluginList;
                            }
                        }
                    }
                }
            }
            series.setOpen(open);
            series.setSelected(false, null);
            series.setFocused(false);
        }
    }

    @Override
    public void dispose() {
        MediaSeries seriesToClose = getSeries();
        viewCore.disposeView();
        closingSeries(seriesToClose);
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            ((JComponent) viewCore.getComponent()).setBorder(focusBorder);
            if (getSeries() != null) {
                getSeries().setSelected(true, null);
                getSeries().setFocused(true);
            }
        } else {
            ((JComponent) viewCore.getComponent()).setBorder(normalBorder);
            if (getSeries() != null) {
                getSeries().setSelected(false, null);
                getSeries().setFocused(true);
            }
        }
    }

    
    @Override
    public void focusGained(FocusEvent focusEvt) {
        if (!focusEvt.isTemporary()) {
            ViewerPlugin container =
                    GUIManager.getInstance().getSelectedViewerPlugin();
            
            if (container instanceof AbstractViewsContainer
                    && ((AbstractViewsContainer) container).isContainingView(
                    this)) {
                ((AbstractViewsContainer) container)
                        .setSelectedImagePaneFromFocus(this);
            }
        }
    }

    @Override
    public void focusLost(FocusEvent e) { /*Empty*/ }
    

    @Override
    public void propertyChange(PropertyChangeEvent evt) {      
        String command = evt.getPropertyName();
        if (command.endsWith("." + ActionW.RESET.cmd())) {
            Object value = evt.getNewValue();
            if (value instanceof String[]) {                            
                resetActions((String[]) value);
            } else {
                resetActions(null);
            }
        } else {
            viewCore.propertyChange(evt);
        }
    }
    
    @Override
    public Object getActionValue(String action) {
        return viewCore.getActionValue(action);
    }

    @Override
    public Object getActionData(String command) {
        return viewCore.getActionData(command);
    }

    @Override
    public boolean isActionEnabled(String command) {
        return viewCore.isActionEnabled(command);
    }

    @Override
    public Component getComponent() {
        return viewCore.getComponent();
    }
    
    /* ********************************************************************
     *             MOUSE LISTENERS
     * *******************************************************************/
    
    @Override
    public void enableMouseAndKeyListener(MouseActions actions) {     
        disableMouseAndKeyListeners();
        initDefaultMouseListeners();
        iniDefaultKeyListener();
        
        getComponent().setCursor(AbstractLayerModel.DEFAULT_CURSOR);
        
        //left:
        addMouseAdapter(actions.getLeft(), InputEvent.BUTTON1_DOWN_MASK);
        
        //middle click
        if (actions.getMiddle().equals(actions.getLeft())) {
            //If mouse action is already registered, only add the modifier mask
            MouseActionAdapter adapter = getMouseAdapter(actions.getMiddle());
            if (adapter != null) {
                adapter.setButtonMaskEx(adapter.getButtonMaskEx()
                        | InputEvent.BUTTON2_DOWN_MASK);
                if (ActionW.WINLEVEL.cmd().equals(actions.getMiddle())) {
                    MouseActionAdapter win = getMouseAdapter(ActionW.WINDOW.cmd());
                    if (win != null) {
                        win.setButtonMaskEx(win.getButtonMaskEx() | InputEvent.BUTTON2_DOWN_MASK);
                    }
                }
            }
        } else {
            addMouseAdapter(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK);
        }
        
        //right
        if (actions.getRight().equals(actions.getLeft())
                || actions.getRight().equals(actions.getMiddle())) {
            MouseActionAdapter adapter = getMouseAdapter(actions.getRight());
            if (adapter != null) {
                adapter.setButtonMaskEx(adapter.getButtonMaskEx()
                        | InputEvent.BUTTON3_DOWN_MASK);
                if (ActionW.WINLEVEL.cmd().equals(actions.getLeft())) {
                    MouseActionAdapter win = getMouseAdapter(ActionW.WINDOW.cmd());
                    if (win != null) {
                        win.setButtonMaskEx(win.getButtonMaskEx() | InputEvent.BUTTON3_DOWN_MASK);
                    }
                }
            }
        } else {
            addMouseAdapter(actions.getRight(), InputEvent.BUTTON3_DOWN_MASK);
        }
        
        //wheel
        getComponent().addMouseWheelListener(getMouseAdapter(actions.getWheel()));
    }
    
    /**
     * Need to remove all to clear.
     */
    private void disableMouseAndKeyListeners() {
        Component comp = viewCore.getComponent();
        for (MouseListener mouseListener : comp.getMouseListeners()) {
            comp.removeMouseListener(mouseListener);
            if (mouseListener instanceof MouseActionAdapter) {
                ((MouseActionAdapter) mouseListener).setButtonMaskEx(0);
            }
        }
        for (MouseMotionListener listener : comp.getMouseMotionListeners()) {
            comp.removeMouseMotionListener(listener);
            if (listener instanceof MouseActionAdapter) {
                ((MouseActionAdapter) listener).setButtonMaskEx(0);
            }
        }
        for (MouseWheelListener wheelList : comp.getMouseWheelListeners()) {
            comp.removeMouseWheelListener(wheelList);
            if (wheelList instanceof MouseActionAdapter) {
                ((MouseActionAdapter) wheelList).setButtonMaskEx(0);
            }
        }
        for (KeyListener keyListener : comp.getKeyListeners()) {
            comp.removeKeyListener(keyListener);
        }
    }
    
    private void initDefaultMouseListeners() {
        getComponent().addMouseListener(new MouseAdapter() {
            
            @Override 
            public void mouseClicked(MouseEvent e) {
                getComponent().requestFocus();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Container parent = getComponent().getParent();
                    if (parent instanceof ViewsGrid) {
                        ((ViewsGrid) parent).maximizeElement(GridViewUI.this);
                        return;
                    }
                }
                getComponent().requestFocus();
                changeCursor(e.getModifiersEx());
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                viewCore.showPixelInfos(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                getComponent().setCursor(AbstractLayerModel.DEFAULT_CURSOR);
            }

            private void changeCursor(int modifiers) {
                MouseActions mouseActions = GUIManager.getInstance().getMouseActions();
                ActionW action = null;
                // left mouse button, always active
                if ((modifiers & InputEvent.BUTTON1_DOWN_MASK) != 0) {
                    action = getActionMouseBtnActFromCommand(mouseActions.getLeft());
                } // middle mouse button
                else if ((modifiers & InputEvent.BUTTON2_DOWN_MASK) != 0
                        && ((mouseActions.getActiveButtons()
                        & InputEvent.BUTTON2_DOWN_MASK) != 0)) {
                    action = getActionMouseBtnActFromCommand(mouseActions.getMiddle());
                } // right mouse button
                else if ((modifiers & InputEvent.BUTTON3_DOWN_MASK) != 0
                        && ((mouseActions.getActiveButtons()
                        & InputEvent.BUTTON3_DOWN_MASK) != 0)) {
                    action = getActionMouseBtnActFromCommand(mouseActions.getRight());
                }
                getComponent().setCursor(action == null
                        ? AbstractLayerModel.DEFAULT_CURSOR : action.getCursor());
            }
        });
        
        getComponent().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                viewCore.showPixelInfos(e);
            }
        });
        
        getComponent().addMouseWheelListener(new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent mwe) {
                ViewerPlugin container =
                    GUIManager.getInstance().getSelectedViewerPlugin();
            
                if (container instanceof AbstractViewsContainer
                        && ((AbstractViewsContainer) container).isContainingView(
                        GridViewUI.this)
                        && ((AbstractViewsContainer) container).getSelectedPane()
                        != GridViewUI.this) {
                    ((AbstractViewsContainer) container)
                            .setSelectedImagePaneFromFocus(GridViewUI.this);
                }
                
            }
        });
    }
    
    private void iniDefaultKeyListener() {
        getComponent().addKeyListener(this);
        getComponent().addKeyListener(panAction);
    }
    
    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (e.isControlDown()) {
                //Ctrl + Space passa para a proxima action.
                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    this, "MouseActions.left", null, null));
            } else {
                //Liga/desliga informacoes do paciente.
                boolean visible = getInfoLayer().isVisible();
                getInfoLayer().setVisible(!visible);
                getComponent().repaint();
            }
        } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_H) {
            //Flip horizontal
            Object actionValue = getActionValue(ActionW.FLIP.cmd());
            boolean val = JMVUtils.getNULLtoFalse(actionValue);
            setActionValue(ActionW.FLIP.cmd(), !val);
            viewCore.getComponent().repaint();
        } else if (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_L) {
            //Rotaciona para a esquerda
            rotateImage(90);
        } else if (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_R) {
            //Rotaciona para a esquerda
            rotateImage(-90);
        } else {
            handleMouseActionKeys(e);
        }
    }

    @Override public void keyTyped(KeyEvent e) { /*Empty*/ }
    @Override public void keyReleased(KeyEvent e) { /* TODO: ctrl + c*/ }
    
    private void handleMouseActionKeys(KeyEvent e) {
        ActionW action = GUIManager.getInstance().getActionFromkeyEvent(
                    e.getKeyCode(), e.getModifiers());
        if (action == null) {
            if (e.getKeyCode() == KeyEvent.VK_H) {
                if (isActionEnabled(ActionW.CROSSHAIR.cmd())) {
                    EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        this, "MouseActions.left", null, ActionW.CROSSHAIR.cmd()));
                }
            } else {
                Object actionData = getActionData(ActionW.PRESET.cmd());
                int keyCode = e.getKeyCode();
                if (actionData instanceof List) {
                    for (Object object : (List) actionData) {
                        if (object instanceof PresetWindowLevel) {
                            PresetWindowLevel val = (PresetWindowLevel) object;
                            if (keyCode == val.getKeyCode()) {
                                setActionValue(ActionW.PRESET.cmd(), val);
                            }
                        }
                    }
                }
            }
        } else {
            EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    this, "MouseActions.left", null, action.cmd()));
        }
    }
    
    private void rotateImage(int angle) {
        Object actionValue = getActionValue(ActionW.ROTATION.cmd());
        if (actionValue instanceof Integer) {
            Integer actual = (Integer) actionValue;
            if (angle > 0) {
                setActionValue(ActionW.ROTATION.cmd(), (actual + angle) % 360);
            } else {
                setActionValue(ActionW.ROTATION.cmd(), (actual + angle + 360) % 360);
            }
        }
    }
    
    private ActionW getActionMouseBtnActFromCommand(String cmd) {
        List<ActionW> actionsButs =
                GUIManager.getInstance().getMouseButtonActions();
        for (ActionW actionW : actionsButs) {
            if (actionW.cmd().equals(cmd)) { 
                return actionW;
            }
        }
        return null;
    }
    
    protected MouseActionAdapter getMouseAdapter(String action) {
        if (action.equals(ActionW.PAN.cmd())) {
            return panAction;
        } else if (action.equals(ActionW.CONTEXTMENU.cmd())) {
            //TODO ContextMenuAdapter(viewCore);
        } else {
            return viewCore.getMouseAdapter(action);
        }
        return null;
    }
    
    private void addMouseAdapter(String actionName, int buttonMask) {
        MouseActionAdapter adapter = getMouseAdapter(actionName);
        if (adapter == null) {
            return;
        }

        adapter.setButtonMaskEx(adapter.getButtonMaskEx() | buttonMask);
        if (actionName.equals(ActionW.MEASURE.cmd())) {
            //TODO measure listener
        }
        
        // For window/level action set window action on x axis
        if (actionName.equals(ActionW.WINLEVEL.cmd())) {
            MouseActionAdapter win = getMouseAdapter(ActionW.WINDOW.cmd());
            if (win != null) {
                win.setButtonMaskEx(win.getButtonMaskEx() | buttonMask);
                win.setMoveOnX(true);
                getComponent().addMouseListener(win);
                getComponent().addMouseMotionListener(win);
            }
            //set level action with inverse progression like view 2d
            //(move the cursor down will decrease the values)
            adapter.setInverse(true);
        }
        
        getComponent().addMouseListener(adapter);
        getComponent().addMouseMotionListener(adapter);
    }

    /**
     * @return the profile
     */
    public String getProfile() {
        return profile;
    }

    /**
     * @param profile the profile to set
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }
    
    public static int viewScaleToSliderValue(double viewScale) {
        final double v = Math.log(viewScale) / Math.log(SCALE_MAX)
                * ViewCore.ZOOM_SLIDER_MAX;
        return (int) Math.round(v);
    }
    
    public static double sliderValueToViewScale(final int sliderValue) {
        final double v = sliderValue / (double) ViewCore.ZOOM_SLIDER_MAX;
        double viewScale = Math.exp(v * Math.log(SCALE_MAX));
        viewScale = roundAndCropViewScale(viewScale,
                SCALE_MIN, SCALE_MAX);
        return viewScale;
    }
    
    private static double roundAndCropViewScale(double viewScale,
            double minViewScale, double maxViewScale) {
        viewScale *= 1000.0;
        double v = Math.floor(viewScale);
        if (viewScale - v >= 0.5) {
            v += 0.5;
        }
        viewScale = v / 1000.0;
        if (viewScale < minViewScale) {
            viewScale = minViewScale;
        }
        if (viewScale > maxViewScale) {
            viewScale = maxViewScale;
        }
        return viewScale;
    }

    @Override
    public void setActionValue(String cmd, Object value) {
        viewCore.setActionsInView(cmd, value, false);
    }

    /************  RESET *******************/
    
    private void resetActions(String[] cmd) {
        if (cmd == null) {
            resetAction(null);
            return;
        }
        for (String string : cmd) {
            resetAction(string);
        }
    }
    
    public void resetAction(String cmd) {
        // Pan
        if (cmd == null || ActionW.PAN.cmd().equals(cmd)) {
            center();
        }
        // Win/Level and Preset
        if (cmd == null || ActionW.WINLEVEL.cmd().equals(cmd)
                || ActionW.PRESET.cmd().equals(cmd)) {
            Object actionData = getActionData(ActionW.PRESET.cmd());
            if (actionData instanceof List) {
                Object get = ((List) actionData).get(0);
                setActionValue(ActionW.PRESET.cmd(), get);
            }
        }
        // LUT
        if (cmd == null || ActionW.LUT.cmd().equals(cmd)) {
            Object actionData = getActionData(ActionW.LUT.cmd());
            if (actionData instanceof List) {                
                Object get = ((List) actionData).get(0);
                setActionValue(ActionW.LUT.cmd(), get);
            }
        }
        // Filter
        if (cmd == null || ActionW.FILTER.cmd().equals(cmd)) {
            Object actionData = getActionData(ActionW.FILTER.cmd());
            if (actionData instanceof List) {                
                Object get = ((List) actionData).get(0);
                setActionValue(ActionW.FILTER.cmd(), get);
            }            
        }
        // InverseLUT
        if (cmd == null || ActionW.INVERT_LUT.cmd().equals(cmd)) {
            setActionValue(ActionW.INVERT_LUT.cmd(), false);
        }
        if (cmd == null || ActionW.ZOOM.cmd().equals(cmd)) {            
            setActionValue(ActionW.ZOOM.cmd(), -1.0D);
        }
         // Smoothing
        if (cmd == null || ActionWA.SMOOTHING.cmd().equals(cmd)) {            
            setActionValue(ActionWA.SMOOTHING.cmd(), true);            
        }
        if (cmd == null || ActionW.ROTATION.cmd().equals(cmd)) {
            setActionValue(ActionW.ROTATION.cmd(), 0);
        }
        if (cmd == null || ActionW.FLIP.cmd().equals(cmd)) {
            setActionValue(cmd, false);
        }
        
        if (viewCore instanceof ViewTexture) {
            ((ViewTexture) viewCore).resetAction(cmd); 
        }
        
        getComponent().repaint();
    }

    @Override
    public AnnotationsLayer getInfoLayer() {
        return null;//TODO getInfoLayer();
    }

    
    private MediaSeriesGroup getParentGroup() {
        ViewerPlugin vPlugin = getParentViewerPlugin(getComponent());
        if (vPlugin != null) {
            return vPlugin.getGroupID();
        }
        return null;
    }

    private ViewerPlugin getParentViewerPlugin(Component component) {
        Container parent = component.getParent();
        while (!(parent instanceof ViewerPlugin) && parent != null) {
            parent = parent.getParent();
        }
        return (ViewerPlugin) parent;
    }

}
