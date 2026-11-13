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

import com.formdev.flatlaf.extras.FlatSVGIcon;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.weasis.core.Messages;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.editor.ExternalDisplay;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.SplitLayout;
import org.weasis.core.ui.editor.TabFocusPolicy;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.util.StringUtil;

public abstract class ViewerPlugin<E extends MediaElement> extends JPanel
    implements WorkspaceView, SeriesViewer<E> {

  public static final int TITLE_MAX_LENGTH = 25;

  private final String uid;
  private final Runnable maskListener = () -> GuiExecutor.execute(this::onMaskingChanged);
  private Supplier<String> identifyingName;
  private final String tooltips;
  private String pluginName;
  private Icon icon;
  private boolean closable = true;
  private boolean detachable = true;
  private MediaSeriesGroup groupID;

  protected ViewerPlugin(String pluginName) {
    this(null, pluginName, null, null);
  }

  protected ViewerPlugin(String uid, String pluginName, Icon icon, String tooltips) {
    this.uid = uid == null ? UUID.randomUUID().toString() : uid;
    this.pluginName = pluginName;
    setLayout(new BorderLayout());
    setName(pluginName);
    this.tooltips = tooltips;
    setToolTipText(tooltips);
    this.icon = icon instanceof FlatSVGIcon flatSVGIcon ? flatSVGIcon.derive(20, 20) : icon;
    IdentityMask.addChangeListener(maskListener);
  }

  private static WorkspaceManager workspace() {
    return GuiUtils.getUICore().getWorkspaceManager();
  }

  @Override
  public MediaSeriesGroup getGroupID() {
    return groupID;
  }

  public void setGroupID(MediaSeriesGroup groupID) {
    this.groupID = groupID;
  }

  @Override
  public String viewId() {
    return uid;
  }

  @Override
  public JComponent component() {
    return this;
  }

  @Override
  public String tabTitle() {
    return pluginName;
  }

  @Override
  public Icon tabIcon() {
    return icon;
  }

  @Override
  public String tabTooltip() {
    String current = getToolTipText();
    return current != null ? current : tooltips;
  }

  @Override
  public boolean isClosable() {
    return closable;
  }

  public void setClosable(boolean closable) {
    this.closable = closable;
  }

  @Override
  public boolean isDetachable() {
    return detachable;
  }

  public void setDetachable(boolean detachable) {
    this.detachable = detachable;
  }

  @Override
  public String getPluginName() {
    return pluginName;
  }

  public void setPluginName(String pluginName) {
    this.identifyingName = null;
    this.pluginName = pluginName;
    setName(pluginName);
    updateTabInfo();
  }

  public void setIcon(Icon icon) {
    this.icon = icon;
    updateTabInfo();
  }

  public Icon getIcon() {
    return icon;
  }

  private void updateTabInfo() {
    WorkspaceManager manager = workspace();
    if (manager != null) {
      manager.updateTabInfo(this);
    }
  }

  /**
   * Sets a title derived from identifying data. A tab title is a stored string rather than
   * something repainted from the model, so the supplier is retained and re-invoked whenever session
   * masking changes.
   *
   * <p>The tab shows the name truncated to {@value #TITLE_MAX_LENGTH} characters, its tooltip the
   * full name.
   *
   * @param nameSupplier recomputes the full name; must itself honour the active {@link
   *     IdentityMask}
   */
  public void setPluginName(Supplier<String> nameSupplier) {
    this.identifyingName = nameSupplier;
    applyIdentifyingName();
  }

  /** Annotations are painted from the model, so the whole plugin has to be repainted. */
  private void onMaskingChanged() {
    applyIdentifyingName();
    repaint();
  }

  private void applyIdentifyingName() {
    if (identifyingName == null) {
      return;
    }
    String fullName = identifyingName.get();
    this.pluginName = truncatedTitle(fullName);
    setName(pluginName);
    setToolTipText(fullName);
    updateTabInfo();
  }

  private static String truncatedTitle(String fullName) {
    return fullName == null
        ? null
        : StringUtil.getTruncatedString(fullName, TITLE_MAX_LENGTH, StringUtil.Suffix.THREE_PTS);
  }

  /**
   * The title under the {@link IdentityMask} in force on this thread: the displayed one follows
   * session masking only, which a command bound to a stricter mask must not read.
   */
  private String maskedPluginName() {
    String name = identifyingName == null ? pluginName : truncatedTitle(identifyingName.get());
    return name == null ? StringUtil.EMPTY_STRING : name;
  }

  /**
   * Requests focus for this viewer, bringing its tab to the foreground.
   *
   * <p>Callers that need to respect a {@link TabFocusPolicy} should check {@link
   * TabFocusPolicy#shouldBringToFront()} before calling.
   */
  public void setSelectedAndGetFocus() {
    WorkspaceManager manager = workspace();
    if (manager != null && manager.isShowing(this)) {
      manager.select(this);
    }
    getFocusTarget().requestFocusInWindow();
  }

  /**
   * The component that takes the keyboard focus when this viewer is selected: the viewer itself by
   * default; an image viewer gives its selected view, so that the viewer shortcuts reach it.
   */
  public java.awt.Component getFocusTarget() {
    return this;
  }

  /**
   * Selects another remaining viewer after this one is closed so its tools and toolbars are
   * restored. When no viewer remains, a {@code NULL_SELECTION} event is fired to clear the UI.
   */
  public void handleFocusAfterClosing() {
    List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
    ViewerPlugin<?> last = null;
    for (int i = viewerPlugins.size() - 1; i >= 0; i--) {
      ViewerPlugin<?> p = viewerPlugins.get(i);
      if (p != this) {
        last = p;
        break;
      }
    }
    if (last != null) {
      WorkspaceManager manager = workspace();
      if (manager != null && manager.isShowing(last)) {
        manager.select(last);
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
    IdentityMask.removeChangeListener(maskListener);
    GuiExecutor.execute(
        () -> {
          GuiUtils.getUICore().getViewerPlugins().remove(ViewerPlugin.this);
          WorkspaceManager manager = workspace();
          if (manager != null && manager.isShowing(ViewerPlugin.this)) {
            manager.close(ViewerPlugin.this);
          }
        });
  }

  /**
   * Invoked by the workspace when the user clicks the tab close button. Disposes this viewer (and
   * its series) and selects the next viewer.
   */
  @Override
  public boolean requestClose() {
    // Remove eagerly so handleFocusAfterClosing() does not pick this viewer as the next selection.
    GuiUtils.getUICore().getViewerPlugins().remove(this);
    close();
    handleFocusAfterClosing();
    return true;
  }

  @Override
  public void populateTabMenu(JPopupMenu menu) {
    JMenuItem closeOthers = new JMenuItem(Messages.getString("ViewerPlugin.close_other"));
    closeOthers.addActionListener(e -> closeOtherViewers(false));
    menu.add(closeOthers);

    JMenuItem closeAll = new JMenuItem(Messages.getString("ViewerPlugin.close_all"));
    closeAll.addActionListener(e -> closeOtherViewers(true));
    menu.add(closeAll);

    menu.addSeparator();
    TabPlacement.addMenuItems(menu, this);
  }

  private void closeOtherViewers(boolean closeAll) {
    GuiExecutor.execute(
        () -> {
          SeriesViewerUI.updateTools(this, closeAll ? null : this, true);
          for (ViewerPlugin<?> plugin : new ArrayList<>(GuiUtils.getUICore().getViewerPlugins())) {
            if (plugin.isClosable() && (closeAll || plugin != this)) {
              plugin.close();
              plugin.handleFocusAfterClosing();
            }
          }
        });
  }

  public Component getComponent() {
    return this;
  }

  /** The id of the factory that creates this viewer type, so a snapshot can recreate it. */
  @Override
  public String typeId() {
    String type = ViewDisplayState.viewerTypeId(this);
    return type != null ? type : WorkspaceView.super.typeId();
  }

  /**
   * Workspace snapshot state: the plugin name and the identifiers of the open series, the
   * foundation for restoring sessions and future hanging protocols. Subclasses may add specifics.
   */
  @Override
  public JsonObject saveState() {
    JsonObjectBuilder builder =
        Json.createObjectBuilder().add("name", maskedPluginName()); // NON-NLS
    List<MediaSeries<E>> openSeries = getOpenSeries();
    if (openSeries != null && !openSeries.isEmpty()) {
      JsonArrayBuilder series = Json.createArrayBuilder();
      for (MediaSeries<E> s : openSeries) {
        Object id = s.getTagValue(s.getTagID());
        if (id != null) {
          series.add(id.toString());
        }
      }
      builder.add("series", series); // NON-NLS
    }
    return builder.build();
  }

  /**
   * Gives a recreated viewer back the content and presentation of a {@link #saveState()} state; the
   * resolver finds the loaded series by id.
   *
   * @return whether this viewer type restores a state; {@code false} leaves the viewer out of the
   *     restored layout
   */
  public boolean restoreState(JsonObject state, SeriesResolver resolver) {
    return false;
  }

  public void showDockable() {
    showDockable(ViewerOpenOptions.defaults());
  }

  public void showDockable(ViewerOpenOptions options) {
    ViewerOpenOptions opts = options == null ? ViewerOpenOptions.defaults() : options;
    SplitLayout splitLayout =
        opts.placement() instanceof ViewerPlacement.Split(SplitLayout layout)
            ? layout
            : SplitLayout.NONE;
    TabFocusPolicy focusPolicy = opts.tabFocusPolicy();

    GuiExecutor.execute(
        () -> {
          WorkspaceManager manager = workspace();
          if (manager != null && !manager.isShowing(this)) {
            List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
            if (!viewerPlugins.contains(ViewerPlugin.this)) {
              viewerPlugins.add(ViewerPlugin.this);
            }
            if (opts.placement() instanceof ViewerPlacement.External(ExternalDisplay display)) {
              manager.detach(this, display.screenBounds());
            } else {
              // the anchor is the selected viewer, so a new viewer opens in the active window;
              // without a selection, the first viewer showing
              ViewerPlugin<?> anchor =
                  manager.selectedView().orElse(null) instanceof ViewerPlugin<?> selected
                      ? selected
                      : viewerPlugins.stream().filter(manager::isShowing).findFirst().orElse(null);
              if (anchor == null) {
                // first viewer fills the (empty) work area of the active window
                manager.openInActive(this);
              } else if (splitLayout.isSplit()) {
                manager.split(this, anchor, splitLayout.toDockSide(), splitLayout.ratio());
              } else {
                manager.stack(this, anchor);
              }
            }
            if (focusPolicy.shouldBringToFront()) {
              setSelectedAndGetFocus();
            }
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
}
