package org.weasis.acquire.dockable.components.actions.annotate.comp;

import java.util.Objects;

import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.model.graphic.Graphic;

public class AnnotationActionState extends ComboItemListener implements ActionState {
    private static AnnotationActionState instance = null;

    private AnnotationActionState() {
        super(ActionW.DRAW_GRAPHICS, MeasureToolBar.drawGraphicList.toArray(new Graphic[MeasureToolBar.drawGraphicList.size()]));
    }

    public static AnnotationActionState getInstance() {
        if (Objects.isNull(instance)) {
            instance = new AnnotationActionState();
            instance.enableAction(true);
            EventManager eventManager = EventManager.getInstance();
            // Replace the default implementation for drawing graphics
            eventManager.setAction(instance);
            
            // Remove actions which are not useful
            eventManager.removeAction(ActionW.SCROLL_SERIES);
            eventManager.removeAction(ActionW.WINDOW);
            eventManager.removeAction(ActionW.LEVEL);
            eventManager.removeAction(ActionW.ROTATION);
            eventManager.removeAction(ActionW.FLIP);
            eventManager.removeAction(ActionW.FILTER);
            eventManager.removeAction(ActionW.INVERSESTACK);
            eventManager.removeAction(ActionW.INVERT_LUT);
            eventManager.removeAction(ActionW.LUT);
            eventManager.removeAction(ActionW.LAYOUT);
            eventManager.removeAction(ActionW.SYNCH);
        }
        return instance;

    }

    @Override
    public void itemStateChanged(Object object) {
        ImageViewerPlugin<?> selectedView2dContainer = EventManager.getInstance().getSelectedView2dContainer();
        if (object instanceof Graphic && Objects.nonNull(selectedView2dContainer)) {
            selectedView2dContainer.setDrawActions((Graphic) object);
        }
    }

}
