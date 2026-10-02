/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.util.StringUtil;

/**
 * Temporary substitution of identifying tag values, for a capture or an outbound request.
 *
 * <p>A mask is bound to the current thread for the dynamic extent of {@link #runMasked} or {@link
 * #callMasked}, so the live session keeps showing real identities while the masked operation sees
 * substituted ones. The binding unwinds with the stack, which is why nothing has to be restored.
 *
 * <p>This is a display-time mask. Persistent de-identification of stored data is a separate concern
 * handled at DICOM export time.
 *
 * @param policy the action to apply per tag
 * @param pseudonymizer produces the replacement for {@link AnonymizationAction#PSEUDONYMIZE}
 * @param dateShiftDays offset applied by {@link AnonymizationAction#SHIFT}
 */
public record IdentityMask(TagPolicy policy, Pseudonymizer pseudonymizer, int dateShiftDays) {

  /** Resolves the action to apply to a tag, or to a whole category. */
  public interface TagPolicy {
    AnonymizationAction actionFor(TagW tag);

    /**
     * The action for a kind of information no tag carries, such as a region burned into the pixels.
     * A policy that does not classify by category keeps everything.
     */
    default AnonymizationAction actionFor(TagCategory category) {
      return AnonymizationAction.KEEP;
    }
  }

  /**
   * Produces a replacement value for a tag whose action is {@link
   * AnonymizationAction#PSEUDONYMIZE}.
   */
  @FunctionalInterface
  public interface Pseudonymizer {
    String pseudonymFor(TagW tag, String value);
  }

  private static final ScopedValue<IdentityMask> ACTIVE = ScopedValue.newInstance();

  public IdentityMask {
    Objects.requireNonNull(policy);
    Objects.requireNonNull(pseudonymizer);
  }

  /** Mask for the on-screen toggle, applying {@link #sessionProfile()}. */
  public static IdentityMask session() {
    return forProfile(sessionProfile());
  }

  /**
   * The profile of session masking: the one chosen with {@link #setSessionMasking(boolean,
   * MaskingProfile)}, or the configured session profile when none was chosen or the chosen one no
   * longer exists after a reload of the masking model.
   */
  public static MaskingProfile sessionProfile() {
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    String id = sessionProfileId;
    return id == null
        ? registry.sessionProfile()
        : registry.profile(id).orElseGet(registry::sessionProfile);
  }

  /**
   * A mask applying {@code profile}, with the session salt and date offset so pseudonyms and
   * shifted dates agree across profiles for the same run.
   */
  public static IdentityMask forProfile(MaskingProfile profile) {
    return new IdentityMask(
        profile,
        SessionSalt.PSEUDONYMIZER,
        profile.shiftsDates() ? SessionSalt.DATE_SHIFT_DAYS : 0);
  }

  private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

  private static volatile boolean sessionMasking;

  /** Id of the profile chosen for session masking; null for the configured one. */
  private static volatile String sessionProfileId;

  /**
   * The mask in force: the one bound to the current thread, or the session mask while {@link
   * #isSessionMasking()} is on.
   */
  public static Optional<IdentityMask> active() {
    if (ACTIVE.isBound()) {
      return Optional.of(ACTIVE.get());
    }
    return sessionMasking ? Optional.of(session()) : Optional.empty();
  }

  /**
   * Whether the whole session is masked. Unlike a scoped binding this outlives the call stack,
   * which is what surfaces caching a derived label — a docking tab title, a titled border — need,
   * since they are not re-rendered per paint. Prefer {@link #runMasked} wherever the value is
   * resolved during the operation itself.
   */
  public static boolean isSessionMasking() {
    return sessionMasking;
  }

  /**
   * Switches session masking, keeping the chosen profile, and notifies listeners; call from the EDT
   * for UI consistency.
   */
  public static void setSessionMasking(boolean enabled) {
    if (sessionMasking != enabled) {
      sessionMasking = enabled;
      LISTENERS.forEach(Runnable::run);
    }
  }

  /**
   * Switches session masking with {@code profile}, or the configured session profile when null.
   * Listeners are notified when the state or, while masking, the profile changes.
   */
  public static void setSessionMasking(boolean enabled, MaskingProfile profile) {
    String id = profile == null ? null : profile.id();
    boolean changed =
        sessionMasking != enabled || (enabled && !Objects.equals(sessionProfileId, id));
    sessionProfileId = id;
    sessionMasking = enabled;
    if (changed) {
      LISTENERS.forEach(Runnable::run);
    }
  }

  /** Cached labels depend on the session profile, so a new model is a change while masking. */
  static void modelChanged() {
    if (sessionMasking) {
      LISTENERS.forEach(Runnable::run);
    }
  }

  /**
   * Registers a listener notified whenever {@link #setSessionMasking} changes the state, or the
   * masking model changes while session masking is on.
   */
  public static void addChangeListener(Runnable listener) {
    LISTENERS.add(Objects.requireNonNull(listener));
  }

  public static void removeChangeListener(Runnable listener) {
    LISTENERS.remove(listener);
  }

  /** Runs {@code task} with this mask bound to the current thread. */
  public void runMasked(Runnable task) {
    ScopedValue.where(ACTIVE, this).run(task);
  }

  /** Calls {@code task} with this mask bound to the current thread. */
  public <T> T callMasked(ScopedValue.CallableOp<T, RuntimeException> task) {
    return ScopedValue.where(ACTIVE, this).call(task);
  }

  /** The profile this mask applies, when it was built from one. */
  public Optional<MaskingProfile> profile() {
    return policy instanceof MaskingProfile maskingProfile
        ? Optional.of(maskingProfile)
        : Optional.empty();
  }

  /** What this mask does to a kind of information, used by the regions burned over the pixels. */
  public AnonymizationAction actionFor(TagCategory category) {
    return policy.actionFor(category);
  }

  public AnonymizationAction actionFor(TagW tag) {
    return policy.actionFor(tag);
  }

  /**
   * Applies the mask in force to a raw value read from {@code tag}, for callers that render a tag
   * outside a {@link TagView} — a tooltip, an attribute dump, a report header.
   *
   * @return the value to display, or {@code null} when the mask hides the tag
   */
  public static Object maskValue(TagW tag, Object value) {
    IdentityMask mask = active().orElse(null);
    if (mask == null || value == null) {
      return value;
    }
    return switch (mask.actionFor(tag)) {
      case REMOVE, CLEAR -> null;
      default -> mask.apply(tag, value);
    };
  }

  /** {@link #maskValue} for callers that already hold the formatted text. */
  public static String maskText(TagW tag, String value) {
    if (!StringUtil.hasText(value)) {
      return value;
    }
    Object masked = maskValue(tag, value);
    return masked == null ? StringUtil.EMPTY_STRING : String.valueOf(masked);
  }

  /**
   * Applies this mask to a value already known not to be {@link AnonymizationAction#REMOVE}d or
   * {@link AnonymizationAction#CLEAR}ed — those are decided by the caller, which must skip the tag
   * or emit an empty value instead of formatting anything.
   */
  public Object apply(TagW tag, Object value) {
    return switch (actionFor(tag)) {
      case PSEUDONYMIZE -> pseudonymize(tag, value);
      case SHIFT -> shift(value);
      default -> value;
    };
  }

  private Object pseudonymize(TagW tag, Object value) {
    String text = TagW.getFormattedText(value, null);
    return StringUtil.hasText(text) ? pseudonymizer.pseudonymFor(tag, text) : value;
  }

  private Object shift(Object value) {
    return switch (value) {
      case LocalDate date -> date.plusDays(dateShiftDays);
      case LocalDateTime dateTime -> dateTime.plusDays(dateShiftDays);
      case LocalTime time -> time;
      default -> value;
    };
  }

  /**
   * One salt and one date offset for the whole run: pseudonyms stay coherent across views and
   * requests, and shifted dates keep the intervals between studies intact.
   */
  private static final class SessionSalt {
    private static final byte[] VALUE = newSalt();
    private static final int DATE_SHIFT_DAYS = shiftFrom(VALUE);
    private static final Pseudonymizer PSEUDONYMIZER = saltedPseudonymizer(VALUE);

    private SessionSalt() {}

    private static byte[] newSalt() {
      byte[] salt = new byte[16];
      new SecureRandom().nextBytes(salt); // NOSONAR called once per run
      return salt;
    }

    /** A stable offset of a few months, derived from the salt so it needs no separate secret. */
    private static int shiftFrom(byte[] salt) {
      return 30 + Math.floorMod(Arrays.hashCode(salt), 300);
    }
  }

  /**
   * Deterministic pseudonymizer: the same input always yields the same output for a given salt, so
   * a patient stays coherent across views and across successive requests, while a new salt makes
   * the mapping unguessable from the outside.
   */
  public static Pseudonymizer saltedPseudonymizer(byte[] salt) {
    byte[] copy = salt.clone();
    return (tag, value) -> {
      String token = token(copy, value);
      return tag.getType() == TagType.DICOM_PERSON_NAME ? "ANONYMOUS^" + token : token; // NON-NLS
    };
  }

  private static String token(byte[] salt, String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256"); // NON-NLS
      digest.update(salt);
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().withUpperCase().formatHex(hash, 0, 4);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Mask that applies {@code action} to every tag — mostly useful for tests and fixed profiles. */
  public static IdentityMask uniform(AnonymizationAction action) {
    TagPolicy policy =
        new TagPolicy() {
          @Override
          public AnonymizationAction actionFor(TagW tag) {
            return action;
          }

          @Override
          public AnonymizationAction actionFor(TagCategory category) {
            return action;
          }
        };
    return new IdentityMask(policy, saltedPseudonymizer(new byte[0]), 0);
  }
}
