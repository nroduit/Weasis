/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.imp;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlID;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.weasis.core.ui.model.utils.UUIDable;

public class DefaultUUID implements UUIDable {

  private String uuid;

  public DefaultUUID() {
    this.uuid = UUID.randomUUID().toString();
  }

  public DefaultUUID(String uuid) {
    setUuid(uuid);
  }

  @Override
  @XmlID
  @XmlAttribute(name = "uuid", required = true)
  public String getUuid() {
    return uuid;
  }

  @Override
  public void setUuid(String uuid) {
    this.uuid = Optional.ofNullable(uuid).orElseGet(UUID.randomUUID()::toString);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DefaultUUID that = (DefaultUUID) o;
    return uuid.equals(that.uuid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid);
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "[" + this.uuid + "]";
  }
}
