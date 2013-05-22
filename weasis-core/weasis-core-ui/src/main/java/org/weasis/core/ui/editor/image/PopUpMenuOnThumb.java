package org.weasis.core.ui.editor.image;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * The Class PopUpMenuOnThumb.
 * 
 * @author Nicolas Roduit
 */
public class PopUpMenuOnThumb extends JPopupMenu {

    private final Panner<?> panner;
    private JMenuItem jMenuItemThumb = new JMenuItem();
    private JMenuItem jMenuItemOrigin = new JMenuItem();
    private JMenuItem jMenuItemCenter = new JMenuItem();

    public PopUpMenuOnThumb(Panner<?> panner) {
        this.panner = panner;
        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        jMenuItemThumb.setText("Hide Thumbnail");
        jMenuItemThumb.addActionListener(new java.awt.event.ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO To implement
            }
        });
        jMenuItemOrigin.setText("Move to Origin");
        jMenuItemOrigin.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                panner.moveToOrigin();
            }
        });
        ;
        jMenuItemCenter.setText("Move to Center");
        jMenuItemCenter.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                panner.moveToCenter();
            }
        });
        this.add(jMenuItemThumb);
        this.addSeparator();
        this.add(jMenuItemOrigin);
        this.add(jMenuItemCenter);
        this.addSeparator();
    }

}
