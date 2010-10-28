/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.internal.mime;

import java.util.ArrayList;

import org.weasis.core.api.Messages;

public class InvalidMagicMimeEntryException extends Exception {

    public InvalidMagicMimeEntryException() {
        super("Invalid Magic Mime Entry: Unknown entry"); //$NON-NLS-1$
    }

    public InvalidMagicMimeEntryException(ArrayList mimeMagicEntry) {
        super("Invalid Magic Mime Entry: " + mimeMagicEntry.toString()); //$NON-NLS-1$
    }
}
