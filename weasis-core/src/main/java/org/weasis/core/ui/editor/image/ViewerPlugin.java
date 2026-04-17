/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import bibliothek.gui.DockStation;
import bibliothek.gui.Dockable;
import bibliothek.gui.dock.StackDockStation;
import bibliothek.gui.dock.action.view.ActionViewConverter;
import bibliothek.gui.dock.action.view.ViewTarget;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.CWorkingArea;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.action.CAction;
import bibliothek.gui.dock.common.action.CButton;
import bibliothek.gui.dock.common.action.core.CommonSimpleButtonAction;
import bibliothek.gui.dock.common.action.predefined.CCloseAction;
import bibliothek.gui.dock.common.event.CVetoFocusListener;
import bibliothek.gui.dock.common.intern.AbstractCDockable;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.intern.CommonDockable;
import bibliothek.gui.dock.common.intern.DefaultCommonDockable;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import bibliothek.gui.dock.control.focus.DefaultFocusRequest;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.UUID;
import javax.swing.Icon;
import javax.swing.JPanel;
import org.weasis.core.Messages;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.SplitLayout;
import org.weasis.core.ui.editor.TabFocusPolicy;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.Toolbar;

public abstract class ViewerPlugin<E extends MediaElement> extends JPanel
    implements SeriesViewer<E> {

  private final String dockableUID;
  private MediaSeriesGroup groupID;
  private String pluginName;
  private final Icon icon;
  private final String tooltips;
  private final DefaultSingleCDockable dockable;

  protected ViewerPlugin(String pluginName) {
    this(null, pluginName, null, null);
  }

  protected ViewerPlugin(String uid, String pluginName, Icon icon, String tooltips) {
    setLayout(new BorderLayout());
    setName(pluginName);
    this.pluginName = pluginName;
    this.icon = icon;
    this.tooltips = tooltips;
    this.dockableUID = uid == null ? UUID.randomUUID().toString() : uid;
    Icon titleIcon = icon instanceof FlatSVGIcon flatSVGIcon ? flatSVGIcon.derive(20, 20) : icon;
    this.dockable = new DefaultSingleCDockable(dockableUID, titleIcon, pluginName);
    this.dockable.setTitleText(pluginName);
    this.dockable.setTitleToolTip(tooltips);
    this.dockable.setTitleIcon(titleIcon);
    this.dockable.setFocusComponent(this);
    this.dockable.setStackable(true);
    this.dockable.setSingleTabShown(true);
    this.dockable.putAction(
        CDockable.ACTION_KEY_CLOSE,
        new CCloseAction(GuiUtils.getUICore().getDockingControl()) {
          @Override
          public void close(CDockable dockable) {
            CControl control = GuiUtils.getUICore().getDockingControl();
            CVetoFocusListener vetoFocus = GuiUtils.getUICore().getDockingVetoFocus();
            control.addVetoFocusListener(vetoFocus);
            super.close(dockable);
            control.removeVetoFocusListener(vetoFocus);
            if (dockable.getFocusComponent() instanceof SeriesViewer<?> seriesViewer) {
              List<DockableTool> oldTool = seriesViewer.getSeriesViewerUI().tools;
              for (DockableTool p : oldTool) {
                p.closeDockable();
              }
              seriesViewer.close();
            }
            Dockable prevDockable =
                control
                    .getController()
                    .getFocusHistory()
                    .getNewestOn(dockable.getWorkingArea().getStation());
            if (prevDockable == null) {
              handleFocusAfterClosing();
            } else {
              if (prevDockable instanceof DefaultCommonDockable defaultCommonDockable
                  && defaultCommonDockable.getDockable()
                      instanceof AbstractCDockable abstractCDockable) {
                if (abstractCDockable.getFocusComponent() instanceof SeriesViewer<?> plugin) {
                  SeriesViewerUI.updateTools(null, plugin, true);
                }
                abstractCDockable.toFront();
              }
            }
          }
        });
    this.dockable.setCloseable(true);
    this.dockable.setMinimizable(false);
    this.dockable.setExternalizable(false);
    this.dockable.addAction(new CloseOthersAction(dockable, false));
    this.dockable.addAction(new CloseOthersAction(dockable, true));
  }

  @Override
  public MediaSeriesGroup getGroupID() {
    return groupID;
  }

  public void setGroupID(MediaSeriesGroup groupID) {
    this.groupID = groupID;
  }

  @Override
  public String getPluginName() {
    return pluginName;
  }

  public Icon getIcon() {
    return icon;
  }

  public String getTooltips() {
    return tooltips;
  }

  @Override
  public String getDockableUID() {
    return dockableUID;
  }

  public void setPluginName(String pluginName) {
    this.pluginName = pluginName;
    this.dockable.setTitleText(pluginName);
  }

  /**
   * Requests focus for this dockable, bringing it to the foreground.
   *
   * <p>This method unconditionally requests focus. Callers that need to respect a {@link
   * TabFocusPolicy} should check {@link TabFocusPolicy#shouldBringToFront()} before calling.
   */
  public void setSelectedAndGetFocus() {
    GuiUtils.getUICore()
        .getDockingControl()
        .getController()
        .setFocusedDockable(new DefaultFocusRequest(dockable.intern(), this, false, true, false));
  }

  public void handleFocusAfterClosing() {
    List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
    int size = viewerPlugins.size();
    if (size > 0) {
      ViewerPlugin<?> lp = viewerPlugins.get(size - 1);
      if (lp != null) {
        lp.dockable.toFront();
      }
    } else {
      ViewerPluginBuilder.DefaultDataModel.firePropertyChange(
          new ObservableEvent(
              ObservableEvent.BasicAction.NULL_SELECTION,
              ViewerPlugin.this.getSeriesViewerUI(),
              null,
              null));
    }
  }

  @Override
  public void close() {
    GuiExecutor.execute(
        () -> {
          GuiUtils.getUICore().getViewerPlugins().remove(ViewerPlugin.this);
          GuiUtils.getUICore().getDockingControl().removeDockable(dockable);
        });
  }

  public Component getComponent() {
    return this;
  }

  public final DefaultSingleCDockable getDockable() {
    return dockable;
  }

  /**
   * Makes this viewer's dockable visible, applying placement and focus behaviour from the given
   * options.
   *
   * @param options the open options controlling split layout and tab focus policy; if {@code null},
   *     defaults are used (tab stacking, foreground focus)
   */
  public void showDockable(ViewerOpenOptions options) {
    ViewerOpenOptions opts = options == null ? ViewerOpenOptions.defaults() : options;
    SplitLayout splitLayout =
        opts.placement() instanceof ViewerPlacement.Split split
            ? split.splitLayout()
            : SplitLayout.NONE;
    TabFocusPolicy focusPolicy = opts.tabFocusPolicy();

    GuiExecutor.execute(
        () -> {
          if (!dockable.isVisible()) {
            List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
            if (!viewerPlugins.contains(ViewerPlugin.this)) {
              viewerPlugins.add(ViewerPlugin.this);
            }

            dockable.add(getComponent());
            dockable.setFocusComponent(ViewerPlugin.this);
            CWorkingArea mainArea = GuiUtils.getUICore().getMainArea();
            mainArea.add(getDockable());
            dockable.setDefaultLocation(
                ExtendedMode.NORMALIZED, CLocation.working(mainArea).stack());

            CControl ctl = GuiUtils.getUICore().getDockingControl();
            StackDockStation viewerStack = null;
            Dockable previousFront = null;
            CDockable singleDockable = null;
            for (ViewerPlugin<?> vp : viewerPlugins) {
              if (vp != ViewerPlugin.this && vp.getDockable().isVisible()) {
                DockStation parent = vp.getDockable().intern().getDockParent();
                if (parent instanceof StackDockStation stack) {
                  viewerStack = stack;
                  previousFront = stack.getFrontDockable();
                  break;
                } else if (singleDockable == null) {
                  // The dockable is alone and not yet part of a StackDockStation
                  singleDockable = vp.getDockable();
                }
              }
            }
            // When no stack exists yet, treat the lone visible dockable as the "front"
            if (viewerStack == null && singleDockable != null) {
              previousFront = singleDockable.intern();
            }

            if (!splitLayout.isSplit()) {
              // Place the new tab at the end of the tab list.
              CDockable lastInStack = null;
              if (viewerStack != null) {
                int count = viewerStack.getDockableCount();
                if (count > 0) {
                  Dockable last = viewerStack.getDockable(count - 1);
                  if (last instanceof CommonDockable commonDockable) {
                    lastInStack = commonDockable.getDockable();
                  }
                }
              } else if (singleDockable != null) {
                // No stack yet – use the lone dockable as the placement anchor
                lastInStack = singleDockable;
              }
              if (lastInStack != null) {
                CDockable target = lastInStack;
                dockable.setLocationsAside(item -> item == target);
              } else {
                dockable.setLocationsAsideFocused();
              }
            } else {
              // A specific split direction was requested.
              CLocation explicitLocation = splitLayout.toLocation(mainArea);
              if (explicitLocation != null) {
                // Directional split (LEFT, RIGHT, TOP, BOTTOM)
                dockable.setLocation(explicitLocation);
              } else {
                // AUTO mode – place in a different split area from the focused one.

                CDockable focused = ctl.getFocusedCDockable();
                DockStation focusedParent =
                    focused != null ? focused.intern().getDockParent() : null;
                boolean focusedInStack = focusedParent instanceof StackDockStation;
                boolean found =
                    dockable.setLocationsAside(
                        item ->
                            item != dockable
                                && item != focused
                                && item.getWorkingArea() == dockable.getWorkingArea()
                                && item.isVisible()
                                // Exclude dockables in the same tab group as the focused one
                                && !(focusedInStack
                                    && item.intern().getDockParent() == focusedParent));
                if (!found) {
                  // No other dockable exists (no split yet): force a right-side split
                  dockable.setLocation(CLocation.working(mainArea).east(splitLayout.ratio()));
                }
              }
            }

            CVetoFocusListener vetoFocus = GuiUtils.getUICore().getDockingVetoFocus();
            ctl.addVetoFocusListener(vetoFocus);
            dockable.setVisible(true);
            ctl.removeVetoFocusListener(vetoFocus);

            // When the policy says the tab should stay in background, restore the previously
            // visible tab as the front one so the user's current view remains undisturbed.
            if (!focusPolicy.shouldBringToFront() && previousFront != null) {
              DockStation newParent = dockable.intern().getDockParent();
              if (newParent instanceof StackDockStation stack) {
                stack.setFrontDockable(previousFront);
              } else if (viewerStack != null) {
                viewerStack.setFrontDockable(previousFront);
              }
            }
            setSelectedAndGetFocus();
          }
        });
  }

  public ViewerToolBar getViewerToolBar() {
    List<Toolbar> bars = getSeriesViewerUI().toolBars;
    synchronized (bars) {
      for (Toolbar t : bars) {
        if (t instanceof ViewerToolBar viewerToolBar) {
          return viewerToolBar;
        }
      }
    }
    return null;
  }

  private static class CloseOthersAction extends CButton {
    private final CDockable dockable;
    private final boolean closeAll;

    public CloseOthersAction(CDockable dockable, boolean closeAll) {
      // prevent standard initialization of the action by calling the protected constructor
      super(null);
      // initialize with a modified action
      init(new MenuOnlySimpleAction(this));
      this.dockable = dockable;
      this.closeAll = closeAll;
      setText(
          closeAll
              ? Messages.getString("ViewerPlugin.close_all")
              : Messages.getString("ViewerPlugin.close_other"));
    }

    @Override
    protected void action() {
      super.action();
      if (dockable.getFocusComponent() instanceof SeriesViewer<?> plugin) {
        SeriesViewerUI.updateTools(plugin, closeAll ? null : plugin, true);
      }
      // We need to access the Core API to find out which other Dockables exist.
      DockStation parent = dockable.intern().getDockParent();

      // Because closing a Dockable may remove the parent DockStation, we first collect all the
      // Dockables we may later close
      Dockable[] children = new Dockable[parent.getDockableCount()];
      for (int i = 0; i < children.length; i++) {
        children[i] = parent.getDockable(i);
      }
      for (Dockable child : children) {
        // we are not interested in things like entire stacks, or our own Dockable. So let's do
        // some checks before closing a Dockable
        if (child instanceof CommonDockable commonDockable) {
          CDockable cChild = commonDockable.getDockable();
          if (cChild.isCloseable() && (closeAll || cChild != dockable)) {
            if (cChild.getFocusComponent() instanceof SeriesViewer<?> seriesViewer) {
              seriesViewer.close();
              if (cChild.getFocusComponent() instanceof ViewerPlugin<?> viewerPlugin) {
                viewerPlugin.handleFocusAfterClosing();
              }
            } else {
              cChild.setVisible(false);
            }
          }
        }
      }
    }
  }

  private static class MenuOnlySimpleAction extends CommonSimpleButtonAction {
    public MenuOnlySimpleAction(CAction action) {
      super(action);
    }

    @Override
    public <V> V createView(
        ViewTarget<V> target, ActionViewConverter converter, Dockable dockable) {
      // This method creates the view (e.g. a JMenuItem) for this DockAction. Since we do not want
      // to show it up everywhere, we just ignore some places (targets).
      if (ViewTarget.TITLE == target) {
        return null;
      }
      return super.createView(target, converter, dockable);
    }
  }
}
