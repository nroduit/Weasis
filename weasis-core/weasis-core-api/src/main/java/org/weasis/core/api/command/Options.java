/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.weasis.core.api.util.StringUtil;

/**
 * Yet another GNU long options parser. This one is configured by parsing its Usage string.
 */
public class Options implements Option {

    public static final String NL = System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$

    // Note: need to double \ within ""
    private static final String REGEX = "(?x)\\s*" + "(?:-([^-]))?" + // 1: short-opt-1 //$NON-NLS-1$ //$NON-NLS-2$
        "(?:,?\\s*-(\\w))?" + // 2: short-opt-2 //$NON-NLS-1$
        "(?:,?\\s*--(\\w[\\w-]*)(=\\w+)?)?" + // 3: long-opt-1 and 4:arg-1 //$NON-NLS-1$
        "(?:,?\\s*--(\\w[\\w-]*))?" + // 5: long-opt-2 //$NON-NLS-1$
        ".*?(?:\\(default=(.*)\\))?\\s*"; // 6: default //$NON-NLS-1$

    private static final int GROUP_SHORT_OPT_1 = 1;
    private static final int GROUP_SHORT_OPT_2 = 2;
    private static final int GROUP_LONG_OPT_1 = 3;
    private static final int GROUP_ARG_1 = 4;
    private static final int GROUP_LONG_OPT_2 = 5;
    private static final int GROUP_DEFAULT = 6;

    private final Pattern parser = Pattern.compile(REGEX);
    private final Pattern uname = Pattern.compile("^Usage:\\s+(\\w+)"); //$NON-NLS-1$

    private final Map<String, Boolean> unmodifiableOptSet;
    private final Map<String, Object> unmodifiableOptArg;
    private final Map<String, Boolean> optSet = new HashMap<>();
    private final Map<String, Object> optArg = new HashMap<>();

    private final Map<String, String> optName = new HashMap<>();
    private final Map<String, String> optAlias = new HashMap<>();
    private final List<Object> xargs = new ArrayList<>();
    private List<String> args = null;

    private static final String UNKNOWN = "unknown"; //$NON-NLS-1$
    private String usageName = UNKNOWN;
    private int usageIndex = 0;

    private final String[] spec;
    private final String[] gspec;
    private final String defOpts;
    private final String[] defArgs;
    private String error = null;

    private boolean optionsFirst = false;
    private boolean stopOnBadOption = false;

    public static void main(String[] args) {
        final String[] usage = { "test - test Options usage", //$NON-NLS-1$
            "  text before Usage: is displayed when usage() is called and no error has occurred.", //$NON-NLS-1$
            "  so can be used as a simple help message.", "", "Usage: testOptions [OPTION]... PATTERN [FILES]...", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            "  Output control: arbitrary non-option text can be included.", "  -? --help                show help", //$NON-NLS-1$ //$NON-NLS-2$
            "  -c --count=COUNT           show COUNT lines", //$NON-NLS-1$
            "  -h --no-filename         suppress the prefixing filename on output", //$NON-NLS-1$
            "  -q --quiet, --silent     suppress all normal output", //$NON-NLS-1$
            "     --binary-files=TYPE   assume that binary files are TYPE", //$NON-NLS-1$
            "                           TYPE is 'binary', 'text', or 'without-match'", //$NON-NLS-1$
            "  -I                       equivalent to --binary-files=without-match", //$NON-NLS-1$
            "  -d --directories=ACTION  how to handle directories (default=skip)", //$NON-NLS-1$
            "                           ACTION is 'read', 'recurse', or 'skip'", //$NON-NLS-1$
            "  -D --devices=ACTION      how to handle devices, FIFOs and sockets", //$NON-NLS-1$
            "                           ACTION is 'read' or 'skip'", //$NON-NLS-1$
            "  -R, -r --recursive       equivalent to --directories=recurse" }; //$NON-NLS-1$

        Option opt = Options.compile(usage).parse(args);

        if (opt.isSet("help")) { //$NON-NLS-1$
            opt.usage(); // includes text before Usage:
            return;
        }

        if (opt.args().isEmpty()) {
            throw opt.usageError("PATTERN not specified"); //$NON-NLS-1$
        }

        System.out.println(opt);
        if (opt.isSet("count")) { //$NON-NLS-1$
            System.out.println("count = " + opt.getNumber("count")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        System.out.println("--directories specified: " + opt.isSet("directories")); //$NON-NLS-1$ //$NON-NLS-2$
        System.out.println("directories=" + opt.get("directories")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static Option compile(String[] optSpec) {
        return new Options(optSpec, null, null);
    }

    public static Option compile(String optSpec) {
        return compile(optSpec.split("\\n")); //$NON-NLS-1$
    }

    public static Option compile(String[] optSpec, Option gopt) {
        return new Options(optSpec, null, gopt);
    }

    public static Option compile(String[] optSpec, String[] gspec) {
        return new Options(optSpec, gspec, null);
    }

    @Override
    public Option setStopOnBadOption(boolean stopOnBadOption) {
        this.stopOnBadOption = stopOnBadOption;
        return this;
    }

    @Override
    public Option setOptionsFirst(boolean optionsFirst) {
        this.optionsFirst = optionsFirst;
        return this;
    }

    @Override
    public boolean isSet(String name) {
        if (!optSet.containsKey(name)) {
            throw new IllegalArgumentException("option not defined in spec: " + name); //$NON-NLS-1$
        }

        return optSet.get(name);
    }

    @Override
    public Object getObject(String name) {
        if (!optArg.containsKey(name)) {
            throw new IllegalArgumentException("option not defined with argument: " + name); //$NON-NLS-1$
        }

        List<Object> list = getObjectList(name);

        return list.isEmpty() ? "" : list.get(list.size() - 1); //$NON-NLS-1$
    }

    @Override
    public List<Object> getObjectList(String name) {
        List<Object> list;
        Object arg = optArg.get(name);

        if (arg == null) {
            throw new IllegalArgumentException("option not defined with argument: " + name); //$NON-NLS-1$
        }

        if (arg instanceof String) { // default value
            list = new ArrayList<>();
            if (StringUtil.hasText((String) arg)) {
                list.add(arg);
            }
        } else {
            list = (List<Object>) arg;
        }

        return list;
    }

    @Override
    public List<String> getList(String name) {
        ArrayList<String> list = new ArrayList<>();
        for (Object o : getObjectList(name)) {
            try {
                list.add((String) o);
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("option not String: " + name); //$NON-NLS-1$
            }
        }
        return list;
    }

    private void addArg(String name, Object value) {
        List<Object> list;
        Object arg = optArg.get(name);

        if (arg instanceof String) { // default value
            list = new ArrayList<>();
            optArg.put(name, list);
        } else {
            list = (List<Object>) arg;
        }

        list.add(value);
    }

    @Override
    public String get(String name) {
        try {
            return (String) getObject(name);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("option not String: " + name); //$NON-NLS-1$
        }
    }

    @Override
    public int getNumber(String name) {
        String number = get(name);
        try {
            if (number != null) {
                return Integer.parseInt(number);
            }
            return 0;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("option '" + name + "' not Number: " + number); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    @Override
    public List<Object> argObjects() {
        return xargs;
    }

    @Override
    public List<String> args() {
        if (args == null) {
            args = new ArrayList<>();
            for (Object arg : xargs) {
                args.add(arg == null ? "null" : arg.toString()); //$NON-NLS-1$
            }
        }
        return args;
    }

    @Override
    public void usage() {
        StringBuilder buf = new StringBuilder();
        int index = 0;

        if (error != null) {
            buf.append(error);
            buf.append(NL);
            index = usageIndex;
        }

        for (int i = index; i < spec.length; ++i) {
            buf.append(spec[i]);
            buf.append(NL);
        }
        System.err.print(buf.toString());
    }

    /**
     * prints usage message and returns IllegalArgumentException, for you to throw.
     */
    @Override
    public IllegalArgumentException usageError(String s) {
        error = usageName + ": " + s; //$NON-NLS-1$
        usage();
        return new IllegalArgumentException(error);
    }

    // internal constructor
    private Options(String[] spec, String[] gspec, Option opt) {
        this.gspec = gspec;
        Options gopt = (Options) opt;

        if (gspec == null && gopt == null) {
            this.spec = spec;
        } else {
            ArrayList<String> list = new ArrayList<>();
            list.addAll(Arrays.asList(spec));
            list.addAll(Arrays.asList(gspec != null ? gspec : gopt.gspec));
            this.spec = list.toArray(new String[0]);
        }

        Map<String, Boolean> myOptSet = new HashMap<>();
        Map<String, Object> myOptArg = new HashMap<>();

        parseSpec(myOptSet, myOptArg);

        if (gopt != null) {
            for (Entry<String, Boolean> e : gopt.optSet.entrySet()) {
                if (e.getValue()) {
                    myOptSet.put(e.getKey(), true);
                }
            }

            for (Entry<String, Object> e : gopt.optArg.entrySet()) {
                if (!"".equals(e.getValue())) { //$NON-NLS-1$
                    myOptArg.put(e.getKey(), e.getValue());
                }
            }

            gopt.reset();
        }

        unmodifiableOptSet = Collections.unmodifiableMap(myOptSet);
        unmodifiableOptArg = Collections.unmodifiableMap(myOptArg);

        defOpts = System.getenv(usageName.toUpperCase() + "_OPTS"); //$NON-NLS-1$
        defArgs = (defOpts != null) ? defOpts.split("\\s+") : new String[0]; //$NON-NLS-1$
    }

    /**
     * parse option spec.
     */
    private void parseSpec(Map<String, Boolean> myOptSet, Map<String, Object> myOptArg) {
        int index = 0;
        for (String line : spec) {
            Matcher m = parser.matcher(line);

            if (m.matches()) {
                final String opt = m.group(GROUP_LONG_OPT_1);
                final String name = (opt != null) ? opt : m.group(GROUP_SHORT_OPT_1);

                if (name != null) {
                    if (myOptSet.containsKey(name)) {
                        throw new IllegalArgumentException("duplicate option in spec: --" + name); //$NON-NLS-1$
                    }
                    myOptSet.put(name, false);
                }

                String dflt = (m.group(GROUP_DEFAULT) != null) ? m.group(GROUP_DEFAULT) : ""; //$NON-NLS-1$
                if (m.group(GROUP_ARG_1) != null) {
                    myOptArg.put(opt, dflt);
                }

                String opt2 = m.group(GROUP_LONG_OPT_2);
                if (opt2 != null) {
                    optAlias.put(opt2, opt);
                    myOptSet.put(opt2, false);
                    if (m.group(GROUP_ARG_1) != null) {
                        myOptArg.put(opt2, ""); //$NON-NLS-1$
                    }
                }

                for (int i = 0; i < 2; ++i) {
                    String sopt = m.group(i == 0 ? GROUP_SHORT_OPT_1 : GROUP_SHORT_OPT_2);
                    if (sopt != null) {
                        if (optName.containsKey(sopt)) {
                            throw new IllegalArgumentException("duplicate option in spec: -" + sopt); //$NON-NLS-1$
                        }
                        optName.put(sopt, name);
                    }
                }
            }

            if (usageName == UNKNOWN) { //NOSONAR compare object not string
                Matcher u = uname.matcher(line);
                if (u.find()) {
                    usageName = u.group(1);
                    usageIndex = index;
                }
            }

            index++;
        }
    }

    private void reset() {
        optSet.clear();
        optSet.putAll(unmodifiableOptSet);
        optArg.clear();
        optArg.putAll(unmodifiableOptArg);
        xargs.clear();
        args = null;
        error = null;
    }

    @Override
    public Option parse(Object[] argv) {
        return parse(argv, false);
    }

    @Override
    public Option parse(List<? extends Object> argv) {
        return parse(argv, false);
    }

    @Override
    public Option parse(Object[] argv, boolean skipArg0) {
        if (null == argv) {
            throw new IllegalArgumentException("argv is null"); //$NON-NLS-1$
        }

        return parse(Arrays.asList(argv), skipArg0);
    }

    @Override
    public Option parse(List<? extends Object> argv, boolean skipArg0) {
        reset();
        List<Object> arguments = new ArrayList<>();
        arguments.addAll(Arrays.asList(defArgs));

        for (Object arg : argv) {
            if (skipArg0) {
                skipArg0 = false;
                usageName = arg.toString();
            } else {
                arguments.add(arg);
            }
        }

        String needArg = null;
        String needOpt = null;
        boolean endOpt = false;

        for (Object oarg : arguments) {
            String arg = oarg == null ? "null" : oarg.toString(); //$NON-NLS-1$

            if (endOpt) {
                xargs.add(oarg);
            } else if (needArg != null) {
                addArg(needArg, oarg);
                needArg = null;
                needOpt = null;
            } else if (!arg.startsWith("-") || "-".equals(oarg)) { //$NON-NLS-1$ //$NON-NLS-2$
                if (optionsFirst) {
                    endOpt = true;
                }
                xargs.add(oarg);
            } else {
                if ("--".equals(arg)) { //$NON-NLS-1$
                    endOpt = true;
                } else if (arg.startsWith("--")) { //$NON-NLS-1$
                    int eq = arg.indexOf('='); 
                    String value = (eq == -1) ? null : arg.substring(eq + 1);
                    String name = arg.substring(2, (eq == -1) ? arg.length() : eq);
                    List<String> names = new ArrayList<>();

                    if (optSet.containsKey(name)) {
                        names.add(name);
                    } else {
                        for (String k : optSet.keySet()) {
                            if (k.startsWith(name)) {
                                names.add(k);
                            }
                        }
                    }

                    switch (names.size()) {
                        case 1:
                            name = names.get(0);
                            optSet.put(name, true);
                            if (optArg.containsKey(name)) {
                                if (value != null) {
                                    addArg(name, value);
                                } else {
                                    needArg = name;
                                }
                            } else if (value != null) {
                                throw usageError("option '--" + name + "' doesn't allow an argument"); //$NON-NLS-1$ //$NON-NLS-2$
                            }
                            break;

                        case 0:
                            if (stopOnBadOption) {
                                endOpt = true;
                                xargs.add(oarg);
                                break;
                            } else {
                                throw usageError("invalid option '--" + name + "'"); //$NON-NLS-1$ //$NON-NLS-2$
                            }

                        default:
                            throw usageError("option '--" + name + "' is ambiguous: " + names); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                } else {
                    for (int i = 1; i < arg.length(); i++) {
                        String c = String.valueOf(arg.charAt(i));
                        if (optName.containsKey(c)) {
                            String name = optName.get(c);
                            optSet.put(name, true);
                            if (optArg.containsKey(name)) {
                                int k = i + 1;
                                if (k < arg.length()) {
                                    addArg(name, arg.substring(k));
                                } else {
                                    needOpt = c;
                                    needArg = name;
                                }
                                break;
                            }
                        } else {
                            if (stopOnBadOption) {
                                xargs.add("-" + c); //$NON-NLS-1$
                                endOpt = true;
                            } else {
                                throw usageError("invalid option '" + c + "'"); //$NON-NLS-1$ //$NON-NLS-2$
                            }
                        }
                    }
                }
            }
        }

        if (needArg != null) {
            String name = (needOpt != null) ? needOpt : "--" + needArg; //$NON-NLS-1$
            throw usageError("option '" + name + "' requires an argument"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // remove long option aliases
        for (Entry<String, String> alias : optAlias.entrySet()) {
            if (optSet.get(alias.getKey())) {
                optSet.put(alias.getValue(), true);
                if (optArg.containsKey(alias.getKey())) {
                    optArg.put(alias.getValue(), optArg.get(alias.getKey()));
                }
            }
            optSet.remove(alias.getKey());
            optArg.remove(alias.getKey());
        }

        return this;
    }

    @Override
    public String toString() {
        return "isSet" + optSet + "\nArg" + optArg + "\nargs" + xargs; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

}
