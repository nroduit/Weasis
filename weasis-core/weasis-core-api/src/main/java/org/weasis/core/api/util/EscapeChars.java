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
package org.weasis.core.api.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.weasis.core.api.Messages;

/**
 * Convenience methods for escaping special characters related to HTML, XML, and regular expressions.
 * 
 */
public final class EscapeChars {

    private static final Pattern SCRIPT = Pattern.compile("<SCRIPT>", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$
    private static final Pattern SCRIPT_END = Pattern.compile("</SCRIPT>", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

    /**
     * Escape characters for text appearing in HTML markup.
     * 
     * <P>
     * The following characters are replaced with corresponding HTML character entities :
     * <table border='1' cellpadding='3' cellspacing='0'>
     * <tr>
     * <th>Character</th>
     * <th>Replacement</th>
     * </tr>
     * <tr>
     * <td><</td>
     * <td>&lt;</td>
     * </tr>
     * <tr>
     * <td>></td>
     * <td>&gt;</td>
     * </tr>
     * <tr>
     * <td>&</td>
     * <td>&amp;</td>
     * </tr>
     * <tr>
     * <td>"</td>
     * <td>&quot;</td>
     * </tr>
     * <tr>
     * <td>\t</td>
     * <td>&#009;</td>
     * </tr>
     * <tr>
     * <td>!</td>
     * <td>&#033;</td>
     * </tr>
     * <tr>
     * <td>#</td>
     * <td>&#035;</td>
     * </tr>
     * <tr>
     * <td>$</td>
     * <td>&#036;</td>
     * </tr>
     * <tr>
     * <td>%</td>
     * <td>&#037;</td>
     * </tr>
     * <tr>
     * <td>'</td>
     * <td>&#039;</td>
     * </tr>
     * <tr>
     * <td>(</td>
     * <td>&#040;</td>
     * </tr>
     * <tr>
     * <td>)</td>
     * <td>&#041;</td>
     * </tr>
     * <tr>
     * <td>*</td>
     * <td>&#042;</td>
     * </tr>
     * <tr>
     * <td>+</td>
     * <td>&#043;</td>
     * </tr>
     * <tr>
     * <td>,</td>
     * <td>&#044;</td>
     * </tr>
     * <tr>
     * <td>-</td>
     * <td>&#045;</td>
     * </tr>
     * <tr>
     * <td>.</td>
     * <td>&#046;</td>
     * </tr>
     * <tr>
     * <td>/</td>
     * <td>&#047;</td>
     * </tr>
     * <tr>
     * <td>:</td>
     * <td>&#058;</td>
     * </tr>
     * <tr>
     * <td>;</td>
     * <td>&#059;</td>
     * </tr>
     * <tr>
     * <td>=</td>
     * <td>&#061;</td>
     * </tr>
     * <tr>
     * <td>?</td>
     * <td>&#063;</td>
     * </tr>
     * <tr>
     * <td>@</td>
     * <td>&#064;</td>
     * </tr>
     * <tr>
     * <td>[</td>
     * <td>&#091;</td>
     * </tr>
     * <tr>
     * <td>\</td>
     * <td>&#092;</td>
     * </tr>
     * <tr>
     * <td>]</td>
     * <td>&#093;</td>
     * </tr>
     * <tr>
     * <td>^</td>
     * <td>&#094;</td>
     * </tr>
     * <tr>
     * <td>_</td>
     * <td>&#095;</td>
     * </tr>
     * <tr>
     * <td>`</td>
     * <td>&#096;</td>
     * </tr>
     * <tr>
     * <td>{</td>
     * <td>&#123;</td>
     * </tr>
     * <tr>
     * <td>|</td>
     * <td>&#124;</td>
     * </tr>
     * <tr>
     * <td></td>
     * <td>&#125;</td>
     * </tr>
     * <tr>
     * <td>~</td>
     * <td>&#126;</td>
     * </tr>
     * </table>
     * 
     * <P>
     * Note that JSTL's {@code <c:out>} escapes <em>only the first 
     * five</em> of the above characters.
     */
    public static String forHTML(String aText) {
        final StringBuilder result = new StringBuilder();
        final StringCharacterIterator iterator = new StringCharacterIterator(aText);
        char character = iterator.current();
        while (character != CharacterIterator.DONE) {
            if (character == '<') {
                result.append("&lt;"); //$NON-NLS-1$
            } else if (character == '>') {
                result.append("&gt;"); //$NON-NLS-1$
            } else if (character == '&') {
                result.append("&amp;"); //$NON-NLS-1$
            } else if (character == '\"') {
                result.append("&quot;"); //$NON-NLS-1$
            } else if (character == '\t') {
                addCharEntity(9, result);
            } else if (character == '!') {
                addCharEntity(33, result);
            } else if (character == '#') {
                addCharEntity(35, result);
            } else if (character == '$') {
                addCharEntity(36, result);
            } else if (character == '%') {
                addCharEntity(37, result);
            } else if (character == '\'') {
                addCharEntity(39, result);
            } else if (character == '(') {
                addCharEntity(40, result);
            } else if (character == ')') {
                addCharEntity(41, result);
            } else if (character == '*') {
                addCharEntity(42, result);
            } else if (character == '+') {
                addCharEntity(43, result);
            } else if (character == ',') {
                addCharEntity(44, result);
            } else if (character == '-') {
                addCharEntity(45, result);
            } else if (character == '.') {
                addCharEntity(46, result);
            } else if (character == '/') {
                addCharEntity(47, result);
            } else if (character == ':') {
                addCharEntity(58, result);
            } else if (character == ';') {
                addCharEntity(59, result);
            } else if (character == '=') {
                addCharEntity(61, result);
            } else if (character == '?') {
                addCharEntity(63, result);
            } else if (character == '@') {
                addCharEntity(64, result);
            } else if (character == '[') {
                addCharEntity(91, result);
            } else if (character == '\\') {
                addCharEntity(92, result);
            } else if (character == ']') {
                addCharEntity(93, result);
            } else if (character == '^') {
                addCharEntity(94, result);
            } else if (character == '_') {
                addCharEntity(95, result);
            } else if (character == '`') {
                addCharEntity(96, result);
            } else if (character == '{') {
                addCharEntity(123, result);
            } else if (character == '|') {
                addCharEntity(124, result);
            } else if (character == '}') {
                addCharEntity(125, result);
            } else if (character == '~') {
                addCharEntity(126, result);
            } else {
                // the char is not a special one
                // add it to the result as is
                result.append(character);
            }
            character = iterator.next();
        }
        return result.toString();
    }

    /**
     * Escape all ampersand characters in a URL.
     * 
     * <P>
     * Replaces all <tt>'&'</tt> characters with <tt>'&amp;'</tt>.
     * 
     *<P>
     * An ampersand character may appear in the query string of a URL. The ampersand character is indeed valid in a URL.
     * <em>However, URLs usually appear as an <tt>HREF</tt> attribute, and 
     * such attributes have the additional constraint that ampersands 
     * must be escaped.</em>
     * 
     * <P>
     * The JSTL <c:url> tag does indeed perform proper URL encoding of query parameters. But it does not, in general,
     * produce text which is valid as an <tt>HREF</tt> attribute, simply because it does not escape the ampersand
     * character. This is a nuisance when multiple query parameters appear in the URL, since it requires a little extra
     * work.
     */
    public static String forHrefAmpersand(String aURL) {
        return aURL.replace("&", "&amp;"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Synonym for <tt>URLEncoder.encode(String, "UTF-8")</tt>.
     * 
     * <P>
     * Used to ensure that HTTP query strings are in proper form, by escaping special characters such as spaces.
     * 
     * <P>
     * It is important to note that if a query string appears in an <tt>HREF</tt> attribute, then there are two issues -
     * ensuring the query string is valid HTTP (it is URL-encoded), and ensuring it is valid HTML (ensuring the
     * ampersand is escaped).
     */
    public static String forURL(String aURLFragment) {
        String result = null;
        try {
            result = URLEncoder.encode(aURLFragment, "UTF-8"); //$NON-NLS-1$
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException("UTF-8 not supported", ex); //$NON-NLS-1$
        }
        return result;
    }

    /**
     * Escape characters for text appearing as XML data, between tags.
     * 
     * <P>
     * The following characters are replaced with corresponding character entities :
     * <table border='1' cellpadding='3' cellspacing='0'>
     * <tr>
     * <th>Character</th>
     * <th>Encoding</th>
     * </tr>
     * <tr>
     * <td><</td>
     * <td>&lt;</td>
     * </tr>
     * <tr>
     * <td>></td>
     * <td>&gt;</td>
     * </tr>
     * <tr>
     * <td>&</td>
     * <td>&amp;</td>
     * </tr>
     * <tr>
     * <td>"</td>
     * <td>&quot;</td>
     * </tr>
     * <tr>
     * <td>'</td>
     * <td>&#039;</td>
     * </tr>
     * </table>
     * 
     * <P>
     */
    public static String forXML(String aText) {
        if (aText == null) {
            return null;
        }
        if (("".equals(aText))) { //$NON-NLS-1$
            return ""; //$NON-NLS-1$
        }
        final StringBuilder result = new StringBuilder();
        final StringCharacterIterator iterator = new StringCharacterIterator(aText);
        char c = iterator.current();
        while (c != CharacterIterator.DONE) {
            if (c == '<') {
                result.append("&lt;"); //$NON-NLS-1$
            } else if (c == '>') {
                result.append("&gt;"); //$NON-NLS-1$
            } else if (c == '\"') {
                result.append("&quot;"); //$NON-NLS-1$
            } else if (c == '\'') {
                result.append("&#039;"); //$NON-NLS-1$
            } else if (c == '&') {
                result.append("&amp;"); //$NON-NLS-1$
            }
            // This method ensures that the output String has only valid XML unicode characters as specified by the XML
            // 1.0
            // standard. For reference, please see <a href="http://www.w3.org/TR/2000/REC-xml-20001006#NT-Char">
            else if ((c == 0x9) || (c == 0xA) || (c == 0xD) || ((c >= 0x20) && (c <= 0xD7FF))
                || ((c >= 0xE000) && (c <= 0xFFFD)) || ((c >= 0x10000) && (c <= 0x10FFFF))) {
                // the char is not a special one
                // add it to the result as is
                result.append(c);
            }
            c = iterator.next();
        }
        return result.toString();
    }

/**
  * Return <tt>aText</tt> with all <tt>'<'</tt> and <tt>'>'</tt> characters
  * replaced by their escaped equivalents.
  */
    public static String toDisableTags(String aText) {
        final StringBuilder result = new StringBuilder();
        final StringCharacterIterator iterator = new StringCharacterIterator(aText);
        char character = iterator.current();
        while (character != CharacterIterator.DONE) {
            if (character == '<') {
                result.append("&lt;"); //$NON-NLS-1$
            } else if (character == '>') {
                result.append("&gt;"); //$NON-NLS-1$
            } else {
                // the char is not a special one
                // add it to the result as is
                result.append(character);
            }
            character = iterator.next();
        }
        return result.toString();
    }

    /**
     * Replace characters having special meaning in regular expressions with their escaped equivalents, preceded by a
     * '\' character.
     * 
     * <P>
     * The escaped characters include :
     *<ul>
     *<li>.
     *<li>\
     *<li>?, * , and +
     *<li>&
     *<li>:
     *<li>{ and }
     *<li>[ and ]
     *<li>( and )
     *<li>^ and $
     *</ul>
     */
    public static String forRegex(String aRegexFragment) {
        final StringBuilder result = new StringBuilder();

        final StringCharacterIterator iterator = new StringCharacterIterator(aRegexFragment);
        char character = iterator.current();
        while (character != CharacterIterator.DONE) {
            /*
             * All literals need to have backslashes doubled.
             */
            if (character == '.') {
                result.append("\\."); //$NON-NLS-1$
            } else if (character == '\\') {
                result.append("\\\\"); //$NON-NLS-1$
            } else if (character == '?') {
                result.append("\\?"); //$NON-NLS-1$
            } else if (character == '*') {
                result.append("\\*"); //$NON-NLS-1$
            } else if (character == '+') {
                result.append("\\+"); //$NON-NLS-1$
            } else if (character == '&') {
                result.append("\\&"); //$NON-NLS-1$
            } else if (character == ':') {
                result.append("\\:"); //$NON-NLS-1$
            } else if (character == '{') {
                result.append("\\{"); //$NON-NLS-1$
            } else if (character == '}') {
                result.append("\\}"); //$NON-NLS-1$
            } else if (character == '[') {
                result.append("\\["); //$NON-NLS-1$
            } else if (character == ']') {
                result.append("\\]"); //$NON-NLS-1$
            } else if (character == '(') {
                result.append("\\("); //$NON-NLS-1$
            } else if (character == ')') {
                result.append("\\)"); //$NON-NLS-1$
            } else if (character == '^') {
                result.append("\\^"); //$NON-NLS-1$
            } else if (character == '$') {
                result.append("\\$"); //$NON-NLS-1$
            } else {
                // the char is not a special one
                // add it to the result as is
                result.append(character);
            }
            character = iterator.next();
        }
        return result.toString();
    }

    /**
     * Escape <tt>'$'</tt> and <tt>'\'</tt> characters in replacement strings.
     * 
     * <P>
     * Synonym for <tt>Matcher.quoteReplacement(String)</tt>.
     * 
     * <P>
     * The following methods use replacement strings which treat <tt>'$'</tt> and <tt>'\'</tt> as special characters:
     * <ul>
     * <li><tt>String.replaceAll(String, String)</tt>
     * <li><tt>String.replaceFirst(String, String)</tt>
     * <li><tt>Matcher.appendReplacement(StringBuffer, String)</tt>
     * </ul>
     * 
     * <P>
     * If replacement text can contain arbitrary characters, then you will usually need to escape that text, to ensure
     * special characters are interpreted literally.
     */
    public static String forReplacementString(String aInput) {
        return Matcher.quoteReplacement(aInput);
    }

    /**
     * Disable all <tt><SCRIPT></tt> tags in <tt>aText</tt>.
     * 
     * <P>
     * Insensitive to case.
     */
    public static String forScriptTagsOnly(String aText) {
        String result = null;
        Matcher matcher = SCRIPT.matcher(aText);
        result = matcher.replaceAll("&lt;SCRIPT>"); //$NON-NLS-1$
        matcher = SCRIPT_END.matcher(result);
        result = matcher.replaceAll("&lt;/SCRIPT>"); //$NON-NLS-1$
        return result;
    }

    private static void addCharEntity(Integer aIdx, StringBuilder aBuilder) {
        String padding = ""; //$NON-NLS-1$
        if (aIdx <= 9) {
            padding = "00"; //$NON-NLS-1$
        } else if (aIdx <= 99) {
            padding = "0"; //$NON-NLS-1$
        } else {
            // no prefix
        }
        String number = padding + aIdx.toString();
        aBuilder.append("&#" + number + ";"); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
