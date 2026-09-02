/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import org.weasis.core.Messages;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.util.StringUtil;

/**
 * Chooses how much identity leaves with the data. Shared by every dialog that produces something
 * others may see, so the same wording and the same profiles are offered each time.
 *
 * <p>{@link #runMasked} is how the choice takes effect: the mask is bound only for the rendering,
 * so what is on screen never changes and nothing has to be restored afterwards.
 */
public class MaskingProfileSelector extends JComboBox<MaskingProfile> {

  public MaskingProfileSelector() {
    this(true);
  }

  private MaskingProfileSelector(boolean withNone) {
    List<MaskingProfile> profiles =
        new ArrayList<>(MaskingModelRegistry.getInstance().exportProfiles());
    MaskingProfile session = IdentityMask.sessionProfile();
    if (withNone) {
      addItem(null);
    } else if (!profiles.contains(session)) {
      profiles.addFirst(session);
    }
    profiles.forEach(this::addItem);
    if (!withNone) {
      setSelectedItem(session);
    } else if (IdentityMask.isSessionMasking()) {
      // While the screen is masked, start from the same profile: the output must not reveal more
      setSelectedItem(
          profiles.contains(session) ? session : profiles.stream().findFirst().orElse(null));
    } else {
      setSelectedItem(null);
    }
    setToolTipText(Messages.getString("masking.profile.tip"));
    setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            Object label = value == null ? Messages.getString("masking.profile.none") : value;
            return super.getListCellRendererComponent(list, label, index, selected, focus);
          }
        });
  }

  /** A selector for session masking: no "None" entry, starting from the current session profile. */
  public static MaskingProfileSelector forSession() {
    return new MaskingProfileSelector(false);
  }

  /**
   * A selector with a "None" entry that starts from {@code profileId} when the screen is not masked
   * (while it is, the session profile wins so the output never reveals more).
   */
  public static MaskingProfileSelector withDefault(String profileId) {
    MaskingProfileSelector selector = new MaskingProfileSelector(true);
    if (!IdentityMask.isSessionMasking()) {
      selector.setSelectedProfileId(profileId);
    }
    return selector;
  }

  /** A label for this selector, with the shared wording. */
  public JLabel createLabel() {
    JLabel label = new JLabel(Messages.getString("masking.profile") + StringUtil.COLON_AND_SPACE);
    label.setLabelFor(this);
    return label;
  }

  public MaskingProfile getSelectedProfile() {
    return (MaskingProfile) getSelectedItem();
  }

  /** The id of the selected profile, or an empty string for none; suited to a preference. */
  public String getSelectedProfileId() {
    MaskingProfile profile = getSelectedProfile();
    return profile == null ? StringUtil.EMPTY_STRING : profile.id();
  }

  /**
   * Selects a profile saved with {@link #getSelectedProfileId()}: an empty id selects none, a null
   * id or an id no longer offered keeps the current selection.
   */
  public void setSelectedProfileId(String id) {
    if (id == null) {
      return;
    }
    if (id.isBlank()) {
      setSelectedItem(null);
      return;
    }
    for (int i = 0; i < getItemCount(); i++) {
      MaskingProfile profile = getItemAt(i);
      if (profile != null && profile.id().equals(id)) {
        setSelectedIndex(i);
        return;
      }
    }
  }

  /** Whether anything at all is masked; drives the per-view annotation flag. */
  public boolean isMasking() {
    return getSelectedProfile() != null;
  }

  /** Runs {@code task} under the selected profile, or unmasked when none is selected. */
  public <T> T runMasked(Supplier<T> task) {
    MaskingProfile profile = getSelectedProfile();
    return profile == null ? task.get() : IdentityMask.forProfile(profile).callMasked(task::get);
  }

  /** {@link #runMasked(Supplier)} for a task without result. */
  public void runMaskedAction(Runnable task) {
    MaskingProfile profile = getSelectedProfile();
    if (profile == null) {
      task.run();
    } else {
      IdentityMask.forProfile(profile).runMasked(task);
    }
  }
}
