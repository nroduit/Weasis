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
package org.weasis.core.api.command;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.weasis.core.api.Messages;

public class Util {

    private final static Pattern patternSpaceExceptQuotes = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'"); //$NON-NLS-1$

    static String[] splitStringExceptQuotes(String s) {
        if (s == null) {
            return new String[0];
        }
        List<String> matchList = new ArrayList<String>();

        Matcher regexMatcher = patternSpaceExceptQuotes.matcher(s);
        while (regexMatcher.find()) {
            matchList.add(regexMatcher.group());
        }
        return matchList.toArray(new String[matchList.size()]);
    }

}
