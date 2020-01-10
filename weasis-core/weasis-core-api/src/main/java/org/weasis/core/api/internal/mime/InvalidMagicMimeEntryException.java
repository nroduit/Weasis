/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *******************************************************************************/
package org.weasis.core.api.internal.mime;

import java.util.List;

public class InvalidMagicMimeEntryException extends Exception {

    private static final long serialVersionUID = -1765168312253063736L;

    public InvalidMagicMimeEntryException(Throwable cause) {
        super("Invalid Magic Mime Entry: Unknown entry", cause); //$NON-NLS-1$
    }

    public InvalidMagicMimeEntryException(List<String> mimeMagicEntry, Throwable cause) {
        super("Invalid Magic Mime Entry: " + mimeMagicEntry.toString(), cause); //$NON-NLS-1$
    }
}
