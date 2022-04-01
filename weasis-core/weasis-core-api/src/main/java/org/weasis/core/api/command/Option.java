/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.util.List;

public interface Option {
  /**
   * stop parsing on the first unknown option. This allows one parser to get its own options and
   * then pass the remaining options to another parser.
   *
   * @param stopOnBadOption true will stop on the first unknown option.
   */
  Option setStopOnBadOption(boolean stopOnBadOption);

  /**
   * require options to precede args. Default is false, so options can appear between or after args.
   *
   * @param optionsFirst true if the options are before arguments
   */
  Option setOptionsFirst(boolean optionsFirst);

  /**
   * parse arguments. If skipArgv0 is true, then parsing begins at arg1. This allows for commands
   * where argv0 is the command name rather than a real argument.
   *
   * @param argv the arguments list
   * @param skipArg0 true will skip the first argument
   * @return the Option value
   */
  Option parse(List<?> argv, boolean skipArg0);

  /**
   * parse arguments. See {@link #parse(List, boolean)
   *
   *
   */
  Option parse(List<?> argv);

  /**
   * parse arguments. See {@link #parse(List, boolean)
   *
   *
   */
  Option parse(Object[] argv, boolean skipArg0);

  /**
   * parse arguments. See {@link #parse(List, boolean)
   *
   */
  Option parse(Object[] argv);

  /**
   * test whether specified option has been explicitly set.
   *
   * @param name the option name
   * @return true if the option has been set
   */
  boolean isSet(String name);

  /**
   * get value of named option. If multiple options given, this method returns the last one. Use
   * {@link #getList(String)} to get all values.
   *
   * @param name the option name
   * @return the String value
   * @throws IllegalArgumentException if value is not a String.
   */
  String get(String name);

  /**
   * get list of all values for named option.
   *
   * @param name the option name
   * @return empty list if option not given and no default specified.
   * @throws IllegalArgumentException if all values are not Strings.
   */
  List<String> getList(String name);

  /**
   * get value of named option as an Object. If multiple options given, this method returns the last
   * one. Use {@link #getObjectList(String)} to get all values.
   *
   * @param name the option name
   * @return the Object value
   */
  Object getObject(String name);

  /**
   * get list of all Object values for named option.
   *
   * @param name the option name
   * @return the list of all Object values
   */
  List<Object> getObjectList(String name);

  /**
   * get value of named option as a Number.
   *
   * @param name the option name
   * @return the integer value
   * @throws IllegalArgumentException if argument is not a Number.
   */
  int getNumber(String name);

  /**
   * get remaining non-options args as Strings.
   *
   * @return the arguments as Strings
   * @throws IllegalArgumentException if args are not Strings.
   */
  List<String> args();

  /**
   * get remaining non-options args as Objects.
   *
   * @return the arguments as Objects
   */
  List<Object> argObjects();

  /** print usage message to System.err. */
  void usage();

  /**
   * print specified usage error to System.err. You should explicitly throw the returned exception.
   *
   * @param error the error message
   * @return IllegalArgumentException
   */
  IllegalArgumentException usageError(String error);

  default boolean isOnlyOneOptionActivate(String... options) {
    int trueCount = 0;
    for (String name : options) {
      if (isSet(name)) {
        trueCount++;
      }
    }
    return trueCount == 1;
  }
}
