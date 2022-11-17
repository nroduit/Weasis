/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.auth;

import com.github.scribejava.core.model.OAuth2AccessToken;

public interface AuthMethod {

  String getCode();

  String getUid();

  void resetToken();

  OAuth2AccessToken getToken();

  AuthRegistration getAuthRegistration();

  boolean isLocal();

  void setLocal(boolean local);

  AuthProvider getAuthProvider();
}
