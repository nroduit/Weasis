/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.tool;

import br.com.animati.texture.mpr3dview.EventPublisher;
import br.com.animati.texture.mpr3dview.GUIManager;
import br.com.animati.texture.mpr3dview.api.AbstractViewsContainer;
import br.com.animati.texture.mpr3dview.internal.Messages;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.util.WtoolBar;

/**
 * To change actions of mouse buttons.
 * 
 * Can`t use ViewerToolBar becouse it uses ImageViewerEventManager
 * & ImageViewerPlugin.
 * 
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 29 Jul.
 */
public class View3dToolBar extends WtoolBar implements ActionListener,
        PropertyChangeListener {
       
    private static final String subscribeRegex =
            "(" + EventPublisher.CONTAINER_SELECTED
            + ")|(MouseActions.left)|(" + ActionW.DRAW_MEASURE.cmd() + ")";
    
    private DropDownButton mouseLeft;
    private DropDownButton mouseMiddle;
    private DropDownButton mouseRight;
    private DropDownButton mouseWheel;
    
    /** Reference to same instance of GUIManager and EventManager. */
    private MouseActions mActions;
    
    /** General Icon for layouts. */
    private static final Icon layIcon = new ImageIcon(
            WtoolBar.class.getResource("/icon/22x22/layout1x1.png"));
    
    
    public View3dToolBar(MouseActions actions, int activeMouse, final int index) {
        super("View 3d Main Toolbar", index);
         
        if (actions == null) {
            throw new IllegalArgumentException("MouseActions cannot be null");
        }
        mActions = actions;
        
        initButtons(activeMouse);
        
        add(Box.createRigidArea(new Dimension(5, 0)));
        
        final DropDownButton layout =
                new DropDownButton("layout", new DropButtonIcon(
                new ImageIcon(WtoolBar.class.getResource("/icon/32x32/layout.png")))) {
            @Override
            protected JPopupMenu getPopupMenu() {
                return getLayoutPopupMenuButton();
            }
        };
        layout.setToolTipText(Messages.getString("View3dToolBar.layout"));
        add(layout);
        
        add(Box.createRigidArea(new Dimension(5, 0)));
        
        final JButton resetButton = new JButton();
        resetButton.setToolTipText(
                Messages.getString("View3dToolBar.disReset"));
        resetButton.setIcon(new ImageIcon(
                WtoolBar.class.getResource("/icon/32x32/reset.png")));
        resetButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {                
                String[] resets = new String[] {ActionW.WINLEVEL.cmd(),
                        ActionW.PRESET.cmd(), ActionW.LUT.cmd(), ActionW.LUT_SHAPE.cmd(), 
                        ActionW.FILTER.cmd(), ActionW.INVERT_LUT.cmd(), "mip-opt", "mip-dep",
                        ActionW.ZOOM.cmd(), ActionW.ROTATION.cmd(), ActionW.PAN.cmd(),
                        ActionW.FLIP.cmd(), "interpolate", "resetToAxial"
                    };
                    EventPublisher.getInstance().publish(new PropertyChangeEvent(
                            resetButton, EventPublisher.VIEWER_DO_ACTION
                            + ActionW.RESET.cmd(), null, resets));
            }
        });
        add(resetButton);
    }
    
    public static String getListenerRegex() {
        return subscribeRegex;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JRadioButtonMenuItem) {
            JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
            if (item.getParent() instanceof JPopupMenu) {
                JPopupMenu pop = (JPopupMenu) item.getParent();
                MouseActions mouseActions = GUIManager.getInstance().getMouseActions();
                mouseActions.setAction(pop.getLabel(), item.getActionCommand());

                ViewerPlugin plugin = GUIManager.getInstance().getSelectedViewerPlugin();
                
                if (plugin instanceof AbstractViewsContainer) {
                    ((AbstractViewsContainer) plugin).setMouseActions(mouseActions);
                }
                if (pop.getInvoker() instanceof DropDownButton) {
                    changeButtonState(pop.getLabel(), item.getActionCommand());
                }
            }
        }
    }

    /**
     * Init GUI (buttons).
     * @param activeMouse 
     */
    private void initButtons(final int activeMouse) {
        MouseActions actions = GUIManager.getInstance().getMouseActions();
        if (((activeMouse & InputEvent.BUTTON1_DOWN_MASK) == InputEvent.BUTTON1_DOWN_MASK)) {
            mouseLeft = buildMouseBtn(actions, MouseActions.LEFT);
            mouseLeft.setToolTipText("Change...");
            add(mouseLeft);
        }
        if (((activeMouse & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK)) {
            add(mouseMiddle = buildMouseBtn(actions, MouseActions.MIDDLE));
        }
        if (((activeMouse & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK)) {
            add(mouseRight = buildMouseBtn(actions, MouseActions.RIGHT));
        }
        if (((activeMouse & MouseActions.SCROLL_MASK) == MouseActions.SCROLL_MASK)) {
            mouseWheel =
                new DropDownButton(MouseActions.WHEEL, buildIcon(MouseActions.WHEEL,
                    actions.getAction(MouseActions.WHEEL))) {

                    @Override
                    protected JPopupMenu getPopupMenu() {
                        return getPopupMenuScroll(this);
                    }
                };
            mouseWheel.setToolTipText("change..."); //$NON-NLS-1
            add(mouseWheel);
        }
        
    }

    private DropDownButton buildMouseBtn(MouseActions mActions, String label) {
        String action = mActions.getAction(label);
        final DropDownButton button = new DropDownButton(label,
                buildIcon(label, action)) {

            @Override
            protected JPopupMenu getPopupMenu() {
                return getPopupMenuButton(this);
            }

        };
        button.setActionCommand(action);
        button.setToolTipText("Change...");
        return button;
    }

    private Icon buildIcon(String label, String action) {
        final Icon mouseIcon = getMouseIcon(label);
        ActionW actionW = getAction(GUIManager.getInstance().getMouseButtonActions(), action);
        final Icon smallIcon = actionW == null ? ActionW.NO_ACTION.getIcon() : actionW.getIcon();

        return new DropButtonIcon(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                mouseIcon.paintIcon(c, g, x, y);
                if (smallIcon != null) {
                    x += mouseIcon.getIconWidth() - smallIcon.getIconWidth();
                    y += mouseIcon.getIconHeight() - smallIcon.getIconHeight();
                    smallIcon.paintIcon(c, g, x, y);
                }
            }

            @Override
            public int getIconWidth() {
                return mouseIcon.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return mouseIcon.getIconHeight();
            }
        });
    }
    
    private JPopupMenu getPopupMenuButton(DropDownButton dropButton) {
        String type = dropButton.getType();
        String action = GUIManager.getInstance().getMouseActions().getAction(type);
        JPopupMenu popupMouseButtons = new JPopupMenu(type);
        popupMouseButtons.setInvoker(dropButton);
        ButtonGroup groupButtons = new ButtonGroup();
        List<ActionW> mouseButtonActions = GUIManager.getInstance().getMouseButtonActions();
        for (int i = 0; i < mouseButtonActions.size(); i++) {
            ActionW butt = mouseButtonActions.get(i);
            JRadioButtonMenuItem radio =
                new JRadioButtonMenuItem(butt.getTitle(),
                    butt.getIcon(), butt.cmd().equals(action));
            radio.setActionCommand(butt.cmd());
            radio.addActionListener(this);
            if (MouseActions.LEFT.equals(type)) {
                radio.setAccelerator(KeyStroke.getKeyStroke(
                        butt.getKeyCode(), butt.getModifier()));
            }
            popupMouseButtons.add(radio);
            groupButtons.add(radio);
        }
        
        return popupMouseButtons;
    }
    
    private JPopupMenu getPopupMenuScroll(DropDownButton dropButton) {
        String type = dropButton.getType();
        String action = GUIManager.getInstance().getMouseActions().getAction(type);
        JPopupMenu popupMouseScroll = new JPopupMenu(type);
        popupMouseScroll.setInvoker(dropButton);
        ButtonGroup groupButtons = new ButtonGroup();
        
        ActionW[] actionsScroll = GUIManager.getInstance().getMouseScrollActions();
        for (int i = 0; i < actionsScroll.length; i++) {
            JRadioButtonMenuItem radio =
                new JRadioButtonMenuItem(
                    actionsScroll[i].getTitle(),
                    actionsScroll[i].getIcon(),
                    actionsScroll[i].cmd().equals(action));
            radio.setActionCommand(actionsScroll[i].cmd());
            radio.addActionListener(this);
            popupMouseScroll.add(radio);
            groupButtons.add(radio);
        }

        return popupMouseScroll;
    }
    
    private Icon getMouseIcon(String type) {
        if (MouseActions.LEFT.equals(type)) {
            return ViewerToolBar.MouseLeftIcon;
        } else if (MouseActions.RIGHT.equals(type)) {
            return ViewerToolBar.MouseRightIcon;
        } else if (MouseActions.MIDDLE.equals(type)) {
            return ViewerToolBar.MouseMiddleIcon;
        } else if (MouseActions.WHEEL.equals(type)) {
            return ViewerToolBar.MouseWheelIcon;
        }
        return ViewerToolBar.MouseLeftIcon;
    }

    public ActionW getAction(List<ActionW> actionsButtons, String action) {
        if (actionsButtons != null) {
            synchronized (actionsButtons) {
                for (ActionW a : actionsButtons) {
                    if (a.cmd().equals(action)) {
                        return a;
                    }
                }
            }
        }
        return null;
    }

    private void changeButtonState(String label, String actionCommand) {
        DropDownButton button = getDropDownBtn(label);
        if (button != null) {
            Icon icon = buildIcon(label, actionCommand);
            button.setIcon(icon);
            button.setActionCommand(actionCommand);
        }
    }
    
    public DropDownButton getDropDownBtn(String label) {
        if (MouseActions.LEFT.equals(label)) {
            return mouseLeft;
        } else if (MouseActions.RIGHT.equals(label)) {
            return mouseRight;
        } else if (MouseActions.MIDDLE.equals(label)) {
            return mouseMiddle;
        } else if (MouseActions.WHEEL.equals(label)) {
            return mouseWheel;
        }
        return null;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("MouseActions.left".equals(evt.getPropertyName())) {
            String command = (String) evt.getNewValue();
            if (command == null) {
                String actionCommand = mouseLeft.getActionCommand();
                ActionW action = getAction(actionCommand);
                List<ActionW> buttons = GUIManager.getInstance().getMouseButtonActions();
                int indexOf = buttons.indexOf(action);
                int nextIndex = indexOf + 1;
                if (nextIndex >= buttons.size()) {
                    nextIndex = 0;
                }
                command = buttons.get(nextIndex).cmd();
            }
            changeButtonState(MouseActions.LEFT, command);
            changeActionsOnSelectedPlugin(
                        MouseActions.LEFT, command);
        } else if (ActionW.DRAW_MEASURE.cmd().equals(evt.getPropertyName())) {
            if  (!GUIManager.getInstance().equals(evt.getSource())
                    && !isCommandActive(ActionW.MEASURE.cmd())) {
                changeActionsOnSelectedPlugin(
                        MouseActions.LEFT, ActionW.MEASURE.cmd());
                changeButtonState(MouseActions.LEFT, ActionW.MEASURE.cmd());
            }
        } else {
            updateAllButtons();
        }
    }

    private ActionW getAction(String command) {
        List<ActionW> buttons = GUIManager.getInstance().getMouseButtonActions();
        if (buttons != null) {
            synchronized (buttons) {
                for (ActionW a : buttons) {
                    if (a.cmd().equals(command)) {
                        return a;
                    }
                }
            }
        }
        return null;
    }

    private void changeActionsOnSelectedPlugin(String label, String command) {
        MouseActions mouseActions = mActions;
        mouseActions.setAction(label, command);
        ViewerPlugin plugin = GUIManager.getInstance().getSelectedViewerPlugin();
        if (plugin != null && plugin instanceof AbstractViewsContainer) {
            ((AbstractViewsContainer) plugin).setMouseActions(mouseActions);
        }

    }

    private void updateAllButtons() {
        changeButtonState(MouseActions.LEFT, mActions.getLeft());
        changeButtonState(MouseActions.RIGHT, mActions.getRight());
        changeButtonState(MouseActions.MIDDLE, mActions.getMiddle());
        changeButtonState(MouseActions.WHEEL, mActions.getWheel());
    }
    
    private JPopupMenu getLayoutPopupMenuButton() {
        JPopupMenu popupMouseButtons = new JPopupMenu();
        ViewerPlugin plg = GUIManager.getInstance().getSelectedViewerPlugin();
        if (plg instanceof AbstractViewsContainer) {
            AbstractViewsContainer plugin = (AbstractViewsContainer) plg;
            
            Object actionData = plugin.getActionData(ActionW.LAYOUT.cmd());
            final Object actionValue = plugin.getActionValue(ActionW.LAYOUT.cmd());
            if (actionData instanceof List && !((List) actionData).isEmpty()) {
                List<GridBagLayoutModel> layoutList = (List) actionData;
                GroupRadioMenu radioMenu = new GroupRadioMenu();
                final DefaultComboBoxModel dModel = new DefaultComboBoxModel(
                        layoutList.toArray());
                if (actionValue instanceof GridBagLayoutModel) { 
                    dModel.setSelectedItem(actionValue);
                }
                radioMenu.setModel(dModel);
                final JMenu menu = radioMenu.createMenu("layout");

                popupMouseButtons.setInvoker(this);
                Component[] cps = menu.getMenuComponents();
                for (int i = 0; i < cps.length; i++) {
                    if (cps[i] instanceof RadioMenuItem
                            && ((RadioMenuItem) cps[i]).getIcon() == null) {
                        ((RadioMenuItem) cps[i]).setIcon(
                                layoutList.get(i).getIcon());
                    }
                    popupMouseButtons.add(cps[i]);
                }
                
                dModel.addListDataListener(new ListDataListener() {
                    @Override public void intervalAdded(ListDataEvent e) {}
                    @Override public void intervalRemoved(ListDataEvent e) {}
                    @Override
                    public void contentsChanged(ListDataEvent e) {
                        EventPublisher.getInstance().publish(
                                new PropertyChangeEvent(
                                menu, EventPublisher.CONTAINER_DO_ACTION
                                + ActionW.LAYOUT.cmd(),
                                null, dModel.getSelectedItem()));
                    }
                });
            }
        }
        return popupMouseButtons;
    }
    
    public boolean isCommandActive(String cmd) {
        int active = mActions.getActiveButtons();
        if (cmd != null
            && cmd.equals(mouseLeft.getActionCommand())
            || (((active & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK)
                && ((mouseMiddle == null)
                ? false : cmd.equals(mouseMiddle.getActionCommand())))
            || (((active & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK)
                && ((mouseRight == null)
                ? false : cmd.equals(mouseRight.getActionCommand())))) {
            return true;
        }
        return false;
    }

}
