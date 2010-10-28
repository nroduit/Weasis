/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JToggleButton;

import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.AngleToolGraphic;
import org.weasis.core.ui.graphic.CircleGraphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.RectangleGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.util.WtoolBar;

public abstract class MeasureToolBar extends WtoolBar implements ActionListener {

    protected static final LineGraphic lineGraphic = new LineGraphic(2.0f, Color.YELLOW);
    protected static final AngleToolGraphic angleToolGraphic = new AngleToolGraphic(2.0f, Color.YELLOW);
    protected static final CircleGraphic circleGraphic = new CircleGraphic(1.0f, Color.YELLOW, false);
    protected static final RectangleGraphic rectangleGraphic = new RectangleGraphic(1.0f, Color.YELLOW, false);
    protected final ButtonGroup buttonChoice = new ButtonGroup();
    protected final JButton jButtondelete = new JButton();
    protected final JToggleButton jTgleButtonArrow = new JToggleButton();
    protected final JToggleButton jTgleButtonLine = new JToggleButton();
    protected final JToggleButton jTgleButtonAngle = new JToggleButton();
    protected final JToggleButton jTgleButtonRectangle = new JToggleButton();
    protected final JToggleButton jTgleButtonEllipse = new JToggleButton();
    protected final Component measureButtonGap = Box.createRigidArea(new Dimension(10, 0));

    public MeasureToolBar() {
        super("measure2dBar", TYPE.tool); //$NON-NLS-1$
        addSeparator();
        jTgleButtonArrow.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/24x24/selection.png"))); //$NON-NLS-1$
        jTgleButtonArrow.setToolTipText(Messages.getString("MeasureToolBar.sel")); //$NON-NLS-1$
        jTgleButtonArrow.addActionListener(this);
        add(jTgleButtonArrow);

        jTgleButtonLine.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/24x24/segment.png"))); //$NON-NLS-1$
        jTgleButtonLine.setToolTipText(Messages.getString("MeasureToolBar.line")); //$NON-NLS-1$
        jTgleButtonLine.addActionListener(this);
        add(jTgleButtonLine);

        jTgleButtonAngle.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/24x24/angle.png"))); //$NON-NLS-1$
        jTgleButtonAngle.setToolTipText(Messages.getString("MeasureToolBar.angle")); //$NON-NLS-1$
        jTgleButtonAngle.addActionListener(this);
        add(jTgleButtonAngle);

        jTgleButtonRectangle.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/24x24/square.png"))); //$NON-NLS-1$
        jTgleButtonRectangle.setToolTipText(Messages.getString("MeasureToolBar.rect")); //$NON-NLS-1$
        jTgleButtonRectangle.addActionListener(this);
        add(jTgleButtonRectangle);

        jTgleButtonEllipse.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/24x24/circle.png"))); //$NON-NLS-1$
        jTgleButtonEllipse.setToolTipText(Messages.getString("MeasureToolBar.ellipse")); //$NON-NLS-1$
        jTgleButtonEllipse.addActionListener(this);
        add(jTgleButtonEllipse);

        buttonChoice.add(jTgleButtonArrow);
        buttonChoice.add(jTgleButtonLine);
        buttonChoice.add(jTgleButtonRectangle);
        buttonChoice.add(jTgleButtonAngle);
        buttonChoice.add(jTgleButtonEllipse);

        add(measureButtonGap);
        jButtondelete.setToolTipText(Messages.getString("MeasureToolBar.del")); //$NON-NLS-1$
        jButtondelete.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/24x24/selection_delete.png"))); //$NON-NLS-1$
        jButtondelete.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                AbstractLayerModel model = getCurrentLayerModel();
                if (model != null) {
                    if (model.getSelectedGraphics().size() == 0) {
                        model.setSelectedGraphics(model.getdAllGraphics());
                    }
                    model.deleteSelectedGraphics();
                }
            }
        });
        add(jButtondelete);
    }

    protected abstract AbstractLayerModel getCurrentLayerModel();

    public void actionPerformed(ActionEvent e) {
        setDrawActions();
    }

    public abstract void setDrawActions();

    public void setToSelectionAction() {
        jTgleButtonArrow.doClick();
    }

    @Override
    public void initialize() {
        jTgleButtonLine.doClick();
    }
}
