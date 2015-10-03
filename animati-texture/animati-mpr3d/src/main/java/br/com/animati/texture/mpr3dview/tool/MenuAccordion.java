/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.tool;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 * @author Rafaelo Pinheiro (rafaelo@animati.com.br)
 * @version 20/08/2013
 */
public class MenuAccordion extends JPanel implements ActionListener {

    private Map<Integer, AccordionItem> items;
    private int curIndex = 0;
    private GridBagConstraints grid = new GridBagConstraints();
    private Insets titleInsets = new Insets(8, 5, 0, 5);
    private Insets panelInsets = new Insets(0, 5, 0, 5);
    private List<ChangeListener> listeners = new ArrayList<ChangeListener>();

    public MenuAccordion() {
        items = new HashMap<Integer, AccordionItem>();
        setLayout(new GridBagLayout());
        grid.fill = GridBagConstraints.HORIZONTAL;
        grid.insets = titleInsets;
        grid.gridx = 0;
        grid.gridy = 0;
        grid.ipady = 10;
        grid.gridwidth = 1;
        grid.weightx = 1.0;
    }

    public void addItem(String id, String title, JPanel panel, Boolean enable) {
        AccordionItem item = new AccordionItem(id, title, curIndex, panel, enable);
        add(item, grid);
        grid.gridy++;
        if (enable) {
            grid.insets = panelInsets;
            add(item.getPanel(), grid);
            grid.insets = titleInsets;
            grid.gridy++;
        }
        validate();
        items.put(new Integer(item.getIndex()), item);
        curIndex++;
        item.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        AccordionItem item = (AccordionItem) e.getSource();
        item.selected = !item.selected;
        setOpen(item, item.selected);
    }

    public Map<Integer, AccordionItem> getItems() {
        return items;
    }

    public void setOpen(AccordionItem item, boolean aBoolean) {
        int indexGridY = 0;
        int indexTemp = 0;
        item.selected = !aBoolean;
        if (item.isOpen()) {
            item.setButtonIcon(new ImageIcon(MenuAccordion.class.getResource("/icon/16x16/down_arrow.png")));
            item.selected = false;
            removeAll();
            validate();
            grid.gridx = 0;
            grid.gridy = 0;
            int index = item.getIndex();
            AccordionItem cur;
            for (Integer i : items.keySet()) {
                cur = items.get(i);
                if (cur.getIndex() < index) {
                    add(cur, grid);
                    if (cur.isOpen()) {
                        grid.gridy++;
                        grid.insets = panelInsets;
                        add(cur.getPanel(), grid);
                        grid.insets = titleInsets;
                    }
                    indexGridY = grid.gridy + 1;
                    grid.gridy++;
                } else if (cur.getIndex() > index) {
                    grid.gridy++;
                    add(cur, grid);
                    if (cur.isOpen()) {
                        grid.gridy++;
                        grid.insets = panelInsets;
                        add(cur.getPanel(), grid);
                        grid.insets = titleInsets;
                    }
                }
            }
            grid.gridy = indexGridY;
            add(item, grid);
        } else {
            item.setButtonIcon(new ImageIcon(MenuAccordion.class.getResource("/icon/16x16/up_arrow.png")));
            item.selected = true;
            removeAll();
            validate();
            grid.gridx = 0;
            grid.gridy = 0;
            int index = item.getIndex();
            AccordionItem cur;
            for (Integer i : items.keySet()) {
                cur = items.get(i);
                if (cur.getIndex() < index) {
                    add(cur, grid);
                    if (cur.isOpen()) {
                        grid.gridy++;
                        grid.insets = panelInsets;
                        add(cur.getPanel(), grid);
                        grid.insets = titleInsets;
                    }
                    indexGridY = grid.gridy + 1;
                    grid.gridy++;
                } else if (cur.getIndex() > index) {
                    grid.gridy = (indexTemp + indexGridY) + 2;
                    add(cur, grid);
                    if (cur.isOpen()) {
                        grid.gridy++;
                        grid.insets = panelInsets;
                        add(cur.getPanel(), grid);
                        grid.insets = titleInsets;
                        indexTemp += 2;
                    } else {
                        indexTemp++;
                    }
                }
            }
            grid.gridy = indexGridY;
            add(item, grid);
            grid.gridy++;
            JPanel panelChildren = item.getPanel();
            grid.insets = panelInsets;
            add(panelChildren, grid);
            grid.insets = titleInsets;
        }
        validate();
        updateUI();

        fireActionEvent(new ChangeEvent(this));
    }

    /**
     * Adds a change listener. ChangeEvents are fired when any item's state (open/close) changes.
     * 
     * @param listener
     *            A listener.
     */
    public void addChangeListener(final ChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(final ChangeListener listener) {
        listeners.remove(listener);
    }

    protected void fireActionEvent(final ChangeEvent event) {
        if (event != null) {
            for (ChangeListener listener : listeners) {
                listener.stateChanged(event);
            }
        }
    }

    public class AccordionItem extends JButton {

        private int index;
        private JPanel panel;
        private boolean selected;
        private ImageIcon icon;
        private String titleButton;
        private String identifyer;

        public AccordionItem(String id, String title, int index, JPanel panel, Boolean enable) {
            identifyer = id;
            this.panel = panel;
            this.index = index;
            titleButton = title;
            selected = enable;
            icon = setIcon();

            setLayout(new BorderLayout());
            JLabel titleLabel = new JLabel(title, SwingConstants.LEFT);
            add(titleLabel, BorderLayout.WEST);
            JLabel iconLabel = new JLabel(icon, SwingConstants.RIGHT);
            add(iconLabel, BorderLayout.EAST);

            setPreferredSize(new Dimension(200, 14));

        }

        private ImageIcon setIcon() {
            if (selected) {
                return new ImageIcon(MenuAccordion.class.getResource("/icon/16x16/up_arrow.png"));
            }
            return new ImageIcon(MenuAccordion.class.getResource("/icon/16x16/down_arrow.png"));
        }

        public JPanel getPanel() {
            return this.panel;
        }

        public int getIndex() {
            return this.index;
        }

        public boolean isOpen() {
            return this.selected;
        }

        public void setButtonIcon(ImageIcon icon) {
            this.icon = icon;
            removeAll();
            JLabel titleLabel = new JLabel(titleButton, SwingConstants.LEFT);
            add(titleLabel, BorderLayout.WEST);
            JLabel iconLabel = new JLabel(this.icon, SwingConstants.RIGHT);
            add(iconLabel, BorderLayout.EAST);
        }

        /**
         * @return the identifyer
         */
        public String getIdentifyer() {
            return identifyer;
        }

    }

}
