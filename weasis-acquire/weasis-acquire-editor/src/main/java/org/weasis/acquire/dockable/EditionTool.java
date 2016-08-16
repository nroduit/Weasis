package org.weasis.acquire.dockable;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Optional;

import javax.swing.ImageIcon;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.AcquireSubmitButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.AcquireAction;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ViewCanvas;

import bibliothek.gui.dock.common.CLocation;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-06 - ylar - Creation
 * 
 */
public class EditionTool extends PluginTool implements SeriesViewerListener {
    private static final long serialVersionUID = -3662409181835644699L;

    public static final String BUTTON_NAME = Messages.getString("EditionTool.title"); //$NON-NLS-1$

    private final JScrollPane rootPane = new JScrollPane();

    public static final Font TITLE_FONT = FontTools.getFont12Bold();
    public static final Color TITLE_COLOR = Color.GRAY;

    private final AcquireActionButtonsPanel topPanel;
    private AbstractAcquireActionPanel centralPanel;
    private final AcquireSubmitButtonsPanel bottomPanel = new AcquireSubmitButtonsPanel();

    ViewCanvas<ImageElement> view = EventManager.getInstance().getSelectedViewPane();

    public EditionTool(Type type) {
        super(BUTTON_NAME, BUTTON_NAME, type, 9);
        dockable.setTitleIcon(new ImageIcon(this.getClass().getResource("/icon/22x22/text-html.png")));
        setDockableWidth(300);
        setLayout(new BorderLayout());

        topPanel = new AcquireActionButtonsPanel(this);

        add(topPanel, BorderLayout.NORTH);

        EventManager.getInstance().addSeriesViewerListener(this);
    }

    @Override
    public Component getToolComponent() {
        JViewport viewPort = rootPane.getViewport();
        rootPane.setViewport(Optional.ofNullable(viewPort).orElse(new JViewport()));

        if (viewPort.getView() != this) {
            viewPort.setView(this);
        }
        return rootPane;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // Auto-generated method stub
    }

    /**
     * The manager initialize the Annotation panel with the given image data
     */
    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (EVENT.SELECT_VIEW.equals(type) || EVENT.SELECT.equals(type) || EVENT.LAYOUT.equals(type)) {
            if (event.getSeriesViewer() instanceof View2dContainer) {
                ViewCanvas<ImageElement> view = ((View2dContainer) event.getSeriesViewer()).getSelectedImagePane();
                if (view != null) {
                    view.changeZoomInterpolation(0);
                    AcquireImageInfo old = AcquireManager.getCurrentAcquireImageInfo();
                    AcquireImageInfo info = AcquireManager.findByImage(view.getImage());
                    AcquireManager.setCurrentAcquireImageInfo(info);

                    if (info != null && info != old) {
                        centralPanel.initValues(info, info.getNextValues());
                    }
                }
            }
        }
    }

    public void setCentralPanel(AbstractAcquireActionPanel centralPanel) {
        Optional.ofNullable(this.centralPanel).ifPresent(p -> {
            p.remove(bottomPanel);
            this.remove(p);
        });
        this.centralPanel = centralPanel;
        this.add(this.centralPanel, BorderLayout.CENTER);
        if (centralPanel.needValidationPanel()) {
            this.centralPanel.add(bottomPanel);
        }
        revalidate();
        repaint();
    }

    public void setBottomPanelActions(AcquireAction acquireAction) {
        this.bottomPanel.setAcquireAction(acquireAction);
    }

    public AcquireActionButtonsPanel getTopPanel() {
        return topPanel;
    }
}
