/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Window;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.ws.WorkspaceListener;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.ProtocolNames;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.hp.ActiveHanging;
import org.weasis.dicom.explorer.hp.HangingPlanApplier;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Applied;
import org.weasis.dicom.explorer.hp.HpSettings;
import org.weasis.dicom.explorer.hp.HpStudies;
import org.weasis.dicom.explorer.hp.Session;
import org.weasis.dicom.explorer.hp.SessionActions;
import org.weasis.dicom.explorer.pref.hp.HangingProtocolPrefView;
import org.weasis.dicom.viewer2d.dockable.HangingWhyTool;

/**
 * The protocol bar: one button that names the protocol of the selected viewer and opens the menu of
 * everything a hanging offers (the other protocols that apply to the study, the reading steps, the
 * kept session, saving the display, whether studies are hung when they open), the previous and next
 * reading steps, and the button that keeps the display. A menu rather than a list, so that the bar
 * is as narrow as its icons and works from the menu of the icons the row does not show. Everything
 * goes through {@link ActiveHanging}, as the {@code hp:group} verb does; a viewer that was not hung
 * by a protocol can be hung from the menu with the protocols that apply to its study.
 */
public class HangingProtocolBar extends WtoolBar {

  private static final Logger LOGGER = LoggerFactory.getLogger(HangingProtocolBar.class);

  /** The longest protocol name shown on the button; the menu and the tooltip give it whole. */
  private static final int MAX_NAME = 18;

  private final DropDownButton protocols;
  private final JLabel step = new JLabel();
  private final JButton previous = new JButton();
  private final JButton next = new JButton();
  private final JButton keep = new JButton();

  public HangingProtocolBar(int index) {
    super(Messages.getString("HangingProtocolBar.title"), index);
    protocols =
        new DropDownButton(
            "hp.menu", // NON-NLS
            null,
            DropButtonIcon.createDropButtonIcon(
                ResourceUtil.getToolBarIcon(ActionIcon.HANGING_PROTOCOL)),
            null) {
          @Override
          protected JPopupMenu getPopupMenu() {
            JPopupMenu menu = menu();
            menu.setInvoker(this);
            return menu;
          }
        };
    protocols.setName("hanging-protocol"); // NON-NLS
    add(protocols);

    previous.setIcon(ResourceUtil.getDirectionToolBarIcon(ActionIcon.PREVIOUS));
    previous.setToolTipText(Messages.getString("HangingProtocolBar.previous"));
    previous.addActionListener(e -> act(ActiveHanging::previous));
    add(previous);

    add(step);

    next.setIcon(ResourceUtil.getDirectionToolBarIcon(ActionIcon.NEXT));
    next.setToolTipText(Messages.getString("HangingProtocolBar.next"));
    next.addActionListener(e -> act(ActiveHanging::next));
    add(next);

    keep.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.KEEP_DISPLAY));
    keep.setToolTipText(Messages.getString("HangingProtocolBar.keep"));
    keep.addActionListener(e -> keepDisplay());
    add(keep);

    WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
    if (manager != null) {
      manager.addListener(
          new WorkspaceListener() {
            @Override
            public void viewSelected(WorkspaceView view, Window window) {
              refresh(view instanceof ViewerPlugin<?> plugin ? plugin : null);
            }
          });
    }
    HangingPlanApplier.addListener(
        applied -> {
          ViewerPlugin<?> selected = GuiUtils.getUICore().getSelectedPlugin().orElse(null);
          if (selected != null && applied.viewers().contains(selected)) {
            refresh(selected);
          }
        });
    refresh(GuiUtils.getUICore().getSelectedPlugin().orElse(null));
  }

  private static String nameOf(ProtocolScore score) {
    String id = score.document().id();
    String name = ProtocolNames.name(id, score.document().protocol().getHangingProtocolName());
    return name == null ? id : name;
  }

  private static String nameOf(Applied applied) {
    String name = applied.plan().name();
    return name == null ? applied.plan().protocolId() : name;
  }

  /** The study of the series the selected view shows, with the model that holds it. */
  private static Optional<StudyRef> selectedStudy() {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    MediaSeries<DicomImageElement> series = view == null ? null : view.getSeries();
    DicomModel model = HpStudies.model().orElse(null);
    if (series == null || model == null) {
      return Optional.empty();
    }
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    return study == null ? Optional.empty() : Optional.of(new StudyRef(model, study));
  }

  private record StudyRef(DicomModel model, MediaSeriesGroup study) {}

  /** The menu of the button, built when it opens from the hanging of the selected viewer. */
  private JPopupMenu menu() {
    JPopupMenu menu = new JPopupMenu();
    ViewerPlugin<?> viewer = GuiUtils.getUICore().getSelectedPlugin().orElse(null);
    Optional<Applied> active = viewer == null ? Optional.empty() : ActiveHanging.of(viewer);
    Optional<StudyRef> study =
        active.isPresent()
            ? active
                .filter(
                    a -> a.context().model() instanceof DicomModel && a.context().study() != null)
                .map(a -> new StudyRef((DicomModel) a.context().model(), a.context().study()))
            : selectedStudy();

    menu.add(GuiUtils.createMenuSectionLabel(Messages.getString("HangingProtocolBar.title")));
    addProtocols(menu, active.orElse(null), study.orElse(null));

    if (active.isPresent()) {
      Applied applied = active.get();
      List<HangingPlan.Step> steps = applied.plan().steps();
      if (steps.size() > 1) {
        menu.addSeparator();
        menu.add(GuiUtils.createMenuSectionLabel(Messages.getString("HangingProtocolBar.steps")));
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < steps.size(); i++) {
          int index = i;
          String label = steps.get(i).label();
          JRadioButtonMenuItem item =
              new JRadioButtonMenuItem(
                  label == null || label.isBlank() ? String.valueOf(i + 1) : (i + 1) + "  " + label,
                  i == applied.step());
          item.addActionListener(e -> act(current -> ActiveHanging.step(current, index)));
          group.add(item);
          menu.add(item);
        }
      }
      menu.addSeparator();
      JMenuItem again = new JMenuItem(Messages.getString("HangingProtocolBar.again"));
      again.setIcon(ResourceUtil.getIcon(ActionIcon.RESET));
      again.addActionListener(e -> act(current -> ActiveHanging.step(current, current.step())));
      menu.add(again);
      JMenuItem why = new JMenuItem(Messages.getString("HangingProtocolBar.why"));
      why.setIcon(ResourceUtil.getIcon(ResourceUtil.OtherIcon.HANGING_WHY));
      why.addActionListener(e -> showWhy());
      menu.add(why);
    }

    menu.addSeparator();
    study
        .flatMap(ref -> SessionActions.offerFor(ref.study()))
        .ifPresent(
            session -> {
              JMenuItem item =
                  new JMenuItem(
                      Messages.format("HangingProtocolBar.session", SessionActions.label(session)));
              item.addActionListener(
                  e -> {
                    SessionActions.restore(session.id());
                    refresh(GuiUtils.getUICore().getSelectedPlugin().orElse(null));
                  });
              menu.add(item);
            });
    JMenuItem keepItem = new JMenuItem(Messages.getString("HangingProtocolBar.keep"));
    keepItem.setIcon(ResourceUtil.getIcon(ActionIcon.KEEP_DISPLAY));
    keepItem.setEnabled(viewer != null);
    keepItem.addActionListener(e -> keepDisplay());
    menu.add(keepItem);
    JMenuItem save = new JMenuItem(Messages.getString("HangingProtocolBar.save"));
    save.setEnabled(
        active.isPresent()
            || (viewer instanceof ImageViewerPlugin<?> image && image.supportsViewportPanes()));
    save.addActionListener(
        e -> {
          ImageViewerPlugin<DicomImageElement> container =
              EventManager.getInstance().getSelectedView2dContainer();
          if (container != null) {
            SaveProtocolDialog.open(container);
          }
        });
    menu.add(save);

    menu.addSeparator();
    JCheckBoxMenuItem onOpen =
        new JCheckBoxMenuItem(
            Messages.getString("HangingProtocolBar.on_open"),
            Settings.get(HpSettings.OPENING) == HpSettings.Opening.PROTOCOL);
    onOpen.setToolTipText(Messages.getString("HangingProtocolBar.on_open.tip"));
    onOpen.addActionListener(
        e ->
            Settings.set(
                HpSettings.OPENING,
                onOpen.isSelected()
                    ? HpSettings.Opening.PROTOCOL
                    : HpSettings.Opening.FIRST_SERIES));
    menu.add(onOpen);
    JMenuItem preferences = new JMenuItem(Messages.getString("HangingProtocolBar.preferences"));
    preferences.addActionListener(e -> showPreferences());
    menu.add(preferences);
    return menu;
  }

  /** The protocols that apply to the study, best first, the one shown ticked. */
  private void addProtocols(JPopupMenu menu, Applied applied, StudyRef study) {
    List<ProtocolScore> candidates = List.of();
    try {
      if (applied != null) {
        candidates = ActiveHanging.candidates(applied);
      } else if (study != null) {
        candidates = HpStudies.candidates(study.model(), study.study());
      }
    } catch (RuntimeException e) {
      LOGGER.warn("Cannot list the protocols of the study", e);
    }
    ButtonGroup group = new ButtonGroup();
    boolean shown = false;
    for (ProtocolScore score : candidates) {
      String id = score.document().id();
      boolean current = applied != null && id.equals(applied.plan().protocolId());
      shown |= current;
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(nameOf(score), current);
      item.addActionListener(e -> hang(id, study));
      group.add(item);
      menu.add(item);
    }
    if (applied != null && !shown) {
      // a protocol asked for by name, or one that does not apply any more
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(nameOf(applied), true);
      group.add(item);
      menu.add(item, 1);
    }
    if (candidates.isEmpty() && applied == null) {
      JMenuItem none = new JMenuItem(Messages.getString("HangingProtocolBar.none"));
      none.setEnabled(false);
      menu.add(none);
    }
  }

  /** Hangs a protocol: on the viewers of the hanging shown, or on the study of the view. */
  private void hang(String protocolId, StudyRef study) {
    if (ActiveHanging.of(null).isPresent()) {
      act(current -> ActiveHanging.protocol(current, protocolId));
    } else if (study != null) {
      try {
        Applied applied = HpStudies.hang(study.model(), study.study(), protocolId);
        applied.container().ifPresent(ViewerPlugin::setSelectedAndGetFocus);
        refresh(applied.container().orElse(null));
      } catch (RuntimeException e) {
        LOGGER.warn("Cannot hang the study", e);
        refresh(GuiUtils.getUICore().getSelectedPlugin().orElse(null));
      }
    }
  }

  private static void showWhy() {
    GuiUtils.getUICore()
        .getSelectedPlugin()
        .map(viewer -> viewer.getSeriesViewerUI().tools)
        .flatMap(tools -> tools.stream().filter(HangingWhyTool.class::isInstance).findFirst())
        .ifPresent(tool -> ((PluginTool) tool).showDockable());
  }

  private void showPreferences() {
    Window win = GuiUtils.getUICore().getApplicationWindow();
    ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(win);
    PreferenceDialog dialog = new PreferenceDialog(win);
    dialog.showPage(HangingProtocolPrefView.PAGE_TITLE);
    ColorLayerUI.showCenterScreen(dialog, layer);
  }

  /** Keeps the display as a session and says so on the button, which the next refresh resets. */
  private void keepDisplay() {
    Optional<Session> kept = SessionActions.keepDisplay();
    refresh(GuiUtils.getUICore().getSelectedPlugin().orElse(null));
    if (kept.isPresent()) {
      keep.setToolTipText(
          Messages.format("HangingProtocolBar.kept", SessionActions.label(kept.get())));
    } else {
      java.awt.Toolkit.getDefaultToolkit().beep();
    }
  }

  private void act(UnaryOperator<Applied> action) {
    Optional<Applied> current = ActiveHanging.of(null);
    if (current.isEmpty()) {
      refresh(null);
      return;
    }
    try {
      Applied applied = action.apply(current.get());
      applied.container().ifPresent(ViewerPlugin::setSelectedAndGetFocus);
      refresh(applied.container().orElse(null));
    } catch (RuntimeException e) {
      LOGGER.warn("Cannot change the hanging", e);
      refresh(GuiUtils.getUICore().getSelectedPlugin().orElse(null));
    }
  }

  /** Shows the hanging of a viewer: its protocol on the button and its reading step. */
  public void refresh(ViewerPlugin<?> viewer) {
    Optional<Applied> active = viewer == null ? Optional.empty() : ActiveHanging.of(viewer);
    keep.setToolTipText(Messages.getString("HangingProtocolBar.keep"));
    keep.setEnabled(viewer != null);
    protocols.setEnabled(viewer != null);
    if (active.isEmpty()) {
      protocols.setText(null);
      protocols.setToolTipText(Messages.getString("HangingProtocolBar.protocol"));
      step.setText("");
      step.setToolTipText(null);
      previous.setEnabled(false);
      next.setEnabled(false);
      return;
    }
    Applied applied = active.get();
    String name = nameOf(applied);
    protocols.setText(name.length() > MAX_NAME ? name.substring(0, MAX_NAME - 1) + "…" : name);
    protocols.setToolTipText(Messages.format("HangingProtocolBar.shown", name));
    int steps = applied.plan().steps().size();
    step.setText(steps > 1 ? (applied.step() + 1) + "/" + steps : "");
    step.setToolTipText(Messages.format("HangingProtocolBar.step", applied.step() + 1, steps));
    previous.setEnabled(steps > 1);
    next.setEnabled(steps > 1);
  }
}
