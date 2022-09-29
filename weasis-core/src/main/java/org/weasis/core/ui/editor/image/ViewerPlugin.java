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
import bibliothek.gui.dock.action.view.ActionViewConverter;
import bibliothek.gui.dock.action.view.ViewTarget;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.action.CAction;
import bibliothek.gui.dock.common.action.CButton;
import bibliothek.gui.dock.common.action.core.CommonSimpleButtonAction;
import bibliothek.gui.dock.common.action.predefined.CCloseAction;
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
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
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
        new CCloseAction(UIManager.DOCKING_CONTROL) {
          @Override
          public void close(CDockable dockable) {
            super.close(dockable);
            if (dockable.getFocusComponent() instanceof SeriesViewer<?> seriesViewer) {
              seriesViewer.close();
            }
            Dockable prevDockable =
                UIManager.DOCKING_CONTROL
                    .getController()
                    .getFocusHistory()
                    .getNewestOn(dockable.getWorkingArea().getStation());
            if (prevDockable == null) {
              handleFocusAfterClosing();
            } else {
              if (prevDockable instanceof DefaultCommonDockable defaultCommonDockable
                  && defaultCommonDockable.getDockable()
                      instanceof AbstractCDockable abstractCDockable) {
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

  public void setSelectedAndGetFocus() {
    UIManager.DOCKING_CONTROL
        .getController()
        .setFocusedDockable(new DefaultFocusRequest(dockable.intern(), this, false, true, false));
  }

  public void handleFocusAfterClosing() {
    int size = UIManager.VIEWER_PLUGINS.size();
    if (size > 0) {
      ViewerPlugin<?> lp = UIManager.VIEWER_PLUGINS.get(size - 1);
      if (lp != null) {
        lp.dockable.toFront();
      }
    } else {
      ViewerPluginBuilder.DefaultDataModel.firePropertyChange(
          new ObservableEvent(
              ObservableEvent.BasicAction.NULL_SELECTION, ViewerPlugin.this, null, null));
    }
  }

  @Override
  public void close() {
    GuiExecutor.instance()
        .execute(
            () -> {
              UIManager.VIEWER_PLUGINS.remove(ViewerPlugin.this);
              UIManager.DOCKING_CONTROL.removeDockable(dockable);
            });
  }

  public Component getComponent() {
    return this;
  }

  public final DefaultSingleCDockable getDockable() {
    return dockable;
  }

  public void showDockable() {
    GuiExecutor.instance()
        .execute(
            () -> {
              if (!dockable.isVisible()) {
                if (!UIManager.VIEWER_PLUGINS.contains(ViewerPlugin.this)) {
                  UIManager.VIEWER_PLUGINS.add(ViewerPlugin.this);
                }
                dockable.add(getComponent());
                dockable.setFocusComponent(ViewerPlugin.this);
                UIManager.MAIN_AREA.add(getDockable());
                dockable.setDefaultLocation(
                    ExtendedMode.NORMALIZED, CLocation.working(UIManager.MAIN_AREA).stack());
                dockable.setVisible(true);
              }
            });
  }

  public ViewerToolBar getViewerToolBar() {
    List<Toolbar> bars = getToolBar();
    if (bars != null) {
      synchronized (bars) {
        for (Toolbar t : bars) {
          if (t instanceof ViewerToolBar viewerToolBar) {
            return viewerToolBar;
          }
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
