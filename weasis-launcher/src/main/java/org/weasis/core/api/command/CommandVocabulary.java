/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * Checks a command model against the rules of the command vocabulary: every value is labelled, a
 * {@link SemanticType} shows the label of its type and can parse itself, long option names are
 * kebab-case, one option name carries one type, a verb of the 5.0 era has no short option, a file
 * read is an {@link InputPath} and a file written an {@link OutputPath}. Hidden options are left
 * out: they are the spellings of an earlier major, kept as they were. A namespace pins the rules in
 * its own test, and a plugin author sees the same report.
 *
 * <p>With a {@link Glossary}: every word of an option name and of a result key is a term or an
 * allowed word ({@link #termViolations}), no description uses a synonym the glossary bans ({@link
 * #synonymViolations}); and the handle graph: a type a verb produces appears in its result, a
 * handle some verb consumes has a producer ({@link #producesViolations}). The check that compares
 * the shipped snapshots with a fresh generation runs in the smoke suite, against a running
 * instance.
 */
public final class CommandVocabulary {

  private static final Pattern LONG_OPTION = Pattern.compile("--[a-z0-9]+(-[a-z0-9]+)*");

  /**
   * A result key, or the path of a nested key: {@code patients[].studies[].studyInstanceUID}; the
   * {@code []} says the key holds an array of objects, and the path goes on inside one of them.
   */
  private static final Pattern RESULT_KEY =
      Pattern.compile("[a-z][a-zA-Z0-9]*(\\[])?(\\.[a-z][a-zA-Z0-9]*(\\[])?)*");

  /** A result type: a word of the vocabulary, with {@code []} for an array of that type. */
  private static final Pattern RESULT_TYPE = Pattern.compile("[a-z][a-z0-9-]*(\\[])?");

  /** The option that reads a file, and the option that writes one: one type each. */
  private static final String FILE_OPTION = "--file"; // NON-NLS

  private static final String OUT_OPTION = "--out"; // NON-NLS

  private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|\\s+");

  private CommandVocabulary() {}

  /**
   * Every check of a namespace at once, with the glossary it declares on top of the core one: the
   * labels and types, the words, the synonyms and the handle graph. The result declarations ({@link
   * #resultViolations}) are a separate assertion, since a namespace adopts them verb by verb.
   */
  public static List<String> allViolations(String namespace, CommandExtension extension) {
    return allViolations(namespace, extension.getCommandModel(), Glossary.core().with(extension));
  }

  /** The same checks on a bare model, with the glossary given. */
  public static List<String> allViolations(String namespace, Object model, Glossary glossary) {
    List<String> violations = new ArrayList<>(violations(namespace, model));
    violations.addAll(termViolations(namespace, model, glossary));
    violations.addAll(synonymViolations(namespace, model, glossary));
    violations.addAll(producesViolations(namespace, model));
    return violations;
  }

  /**
   * Check 2: every word of a visible option name and of a result key is a word of a glossary term
   * or an allowed word, so a new noun is defined before it is used.
   */
  public static List<String> termViolations(String namespace, Object model, Glossary glossary) {
    List<String> violations = new ArrayList<>();
    CommandSpec root = CommandModel.newCommandLine(model).getCommandSpec();
    for (CommandSpec spec : verbs(root)) {
      String verb = namespace + ":" + spec.name();
      for (OptionSpec option : spec.options()) {
        if (option.usageHelp() || option.versionHelp() || option.hidden()) {
          continue;
        }
        String name = option.longestName();
        if (!name.startsWith("--")) {
          continue;
        }
        for (String word : name.substring(2).split("-")) {
          if (!word.isEmpty() && !glossary.allows(word)) {
            violations.add(verb + " " + name + ": '" + word + "' is not a word of the glossary");
          }
        }
      }
      ResultSchema result = CommandModel.resultOf(spec);
      if (result != null) {
        for (var field : result.fields()) {
          for (String word :
              CAMEL_BOUNDARY.split(field.name().replace("[]", "").replace('.', ' '))) {
            if (word.isBlank()) {
              continue;
            }
            if (!glossary.allows(word.toLowerCase(java.util.Locale.ROOT))) {
              violations.add(
                  verb + " " + field.name() + ": '" + word + "' is not a word of the glossary");
            }
          }
        }
      }
    }
    return violations;
  }

  /** Check 3: no description of a verb, an option or a parameter uses a banned synonym. */
  public static List<String> synonymViolations(String namespace, Object model, Glossary glossary) {
    List<String> violations = new ArrayList<>();
    Map<String, String> avoided = glossary.avoided();
    if (avoided.isEmpty()) {
      return violations;
    }
    Map<String, Pattern> patterns = new HashMap<>();
    for (String synonym : avoided.keySet()) {
      patterns.put(synonym, Pattern.compile("(?i)\\b" + Pattern.quote(synonym) + "(s|es)?\\b"));
    }
    CommandSpec root = CommandModel.newCommandLine(model).getCommandSpec();
    for (CommandSpec spec : verbs(root)) {
      String verb = namespace + ":" + spec.name();
      checkText(
          verb,
          "",
          String.join(" ", spec.usageMessage().description()),
          patterns,
          avoided,
          violations);
      for (OptionSpec option : spec.options()) {
        if (option.usageHelp() || option.versionHelp() || option.hidden()) {
          continue;
        }
        checkText(
            verb,
            option.longestName(),
            String.join(" ", option.description()),
            patterns,
            avoided,
            violations);
      }
      for (PositionalParamSpec parameter : spec.positionalParameters()) {
        if (!parameter.hidden()) {
          checkText(
              verb,
              parameter.paramLabel(),
              String.join(" ", parameter.description()),
              patterns,
              avoided,
              violations);
        }
      }
    }
    return violations;
  }

  private static void checkText(
      String verb,
      String where,
      String text,
      Map<String, Pattern> patterns,
      Map<String, String> avoided,
      List<String> violations) {
    for (Map.Entry<String, Pattern> e : patterns.entrySet()) {
      if (e.getValue().matcher(text).find()) {
        violations.add(
            verb
                + (where.isEmpty() ? "" : " " + where)
                + ": says '"
                + e.getKey()
                + "', the term is '"
                + avoided.get(e.getKey())
                + "'");
      }
    }
  }

  /**
   * Check 5, the half a namespace can check alone: a type a verb produces is a handle with a
   * definition and appears among the fields of its result. The other half, that every consumed
   * handle has a producer somewhere, is {@link TypeCatalog#handlesWithoutProducer()} over every
   * namespace.
   */
  public static List<String> producesViolations(String namespace, Object model) {
    List<String> violations = new ArrayList<>();
    CommandSpec root = CommandModel.newCommandLine(model).getCommandSpec();
    for (CommandSpec spec : verbs(root)) {
      String verb = namespace + ":" + spec.name();
      List<Class<?>> produced = CommandModel.producedTypes(spec);
      if (produced.isEmpty()) {
        continue;
      }
      ResultSchema result = CommandModel.resultOf(spec);
      for (Class<?> type : produced) {
        String name = SemanticType.nameOf(type);
        Definition definition = type.getAnnotation(Definition.class);
        if (definition == null || !definition.handle()) {
          violations.add(verb + ": produces " + name + ", which is not defined as a handle");
        }
        if (result == null) {
          violations.add(verb + ": produces " + name + " but declares no result (Returns)");
        } else if (result.fields().stream()
            .noneMatch(f -> name.equals(f.type()) || (name + "[]").equals(f.type()))) {
          violations.add(verb + ": produces " + name + " but no result field has that type");
        }
      }
    }
    return violations;
  }

  /** The violations of {@code model}, each naming the verb and the value it comes from. */
  public static List<String> violations(String namespace, Object model) {
    List<String> violations = new ArrayList<>();
    Map<String, String> typeOfOption = new HashMap<>();
    CommandSpec root = CommandModel.newCommandLine(model).getCommandSpec();
    for (CommandSpec spec : verbs(root)) {
      String verb = namespace + ":" + spec.name();
      checkShortOptions(verb, spec, violations);
      for (OptionSpec option : spec.options()) {
        // A hidden option is a spelling of an earlier major: its name and its type are history.
        if (option.usageHelp() || option.versionHelp() || option.hidden()) {
          continue;
        }
        checkOption(verb, option, typeOfOption, violations);
      }
      for (PositionalParamSpec parameter : spec.positionalParameters()) {
        checkLabel(verb, parameter.paramLabel(), parameter, violations);
      }
    }
    return violations;
  }

  /**
   * The results of a namespace: every verb of the 5.0 era declares what it answers ({@link
   * Returns}), its keys are camelCase and typed by a word of the vocabulary.
   */
  public static List<String> resultViolations(String namespace, Object model) {
    List<String> violations = new ArrayList<>();
    CommandSpec root = CommandModel.newCommandLine(model).getCommandSpec();
    for (CommandSpec spec : verbs(root)) {
      String verb = namespace + ":" + spec.name();
      if (CommandModel.since(spec.userObject()) == null) {
        continue;
      }
      ResultSchema result = CommandModel.resultOf(spec);
      if (result == null) {
        violations.add(verb + ": declares no result (implement Returns)");
        continue;
      }
      for (var field : result.fields()) {
        if (!RESULT_KEY.matcher(field.name()).matches()) {
          violations.add(
              verb + " " + field.name() + ": a result key is camelCase, nested as a.b[].c");
        }
        if (field.type() == null || !RESULT_TYPE.matcher(field.type()).matches()) {
          violations.add(verb + " " + field.name() + ": type " + field.type() + " is not a word");
        }
        if (field.description() == null || field.description().isBlank()) {
          violations.add(verb + " " + field.name() + ": no description");
        }
      }
    }
    return violations;
  }

  private static void checkOption(
      String verb, OptionSpec option, Map<String, String> typeOfOption, List<String> violations) {
    String name = option.longestName();
    if (option.type() != boolean.class && option.type() != Boolean.class) {
      checkLabel(verb, option.paramLabel(), option, violations);
    }
    if (name.startsWith("--") && !LONG_OPTION.matcher(name).matches()) {
      violations.add(verb + " " + name + ": a long option name is kebab-case");
    }
    checkFileType(verb, name, option, violations);
    String type = CommandModel.typeName(option);
    String known = typeOfOption.putIfAbsent(name, type);
    if (known != null && !known.equals(type)) {
      violations.add(
          verb + " " + name + ": carries " + type + " here and " + known + " in another verb");
    }
  }

  /** {@code --file} is read, {@code --out} is written, and each states it by its type. */
  private static void checkFileType(
      String verb, String name, OptionSpec option, List<String> violations) {
    Class<?> type = CommandModel.elementType(option);
    if (FILE_OPTION.equals(name) && type != InputPath.class) {
      violations.add(verb + " --file: reads a file, so its type is input-path");
    }
    if (OUT_OPTION.equals(name) && type != OutputPath.class) {
      violations.add(verb + " --out: writes a file, so its type is output-path");
    }
  }

  /** A verb of the 5.0 era takes long names only; the short letters are the 4.x spellings. */
  private static void checkShortOptions(String verb, CommandSpec spec, List<String> violations) {
    if (CommandModel.since(spec.userObject()) == null) {
      return;
    }
    for (OptionSpec option : spec.options()) {
      if (option.usageHelp() || option.versionHelp() || option.hidden()) {
        continue;
      }
      for (String name : option.names()) {
        if (!name.startsWith("--")) {
          violations.add(verb + " " + option.longestName() + ": " + name + " is a short option");
        }
      }
    }
  }

  private static void checkLabel(String verb, String label, ArgSpec arg, List<String> violations) {
    String name = arg.isOption() ? ((OptionSpec) arg).longestName() : label;
    if (label == null || label.isBlank()) {
      violations.add(verb + " " + name + ": no label");
      return;
    }
    Class<?> type = CommandModel.elementType(arg);
    if (!SemanticType.class.isAssignableFrom(type)) {
      return;
    }
    String expected = SemanticType.labelOf(type);
    if (!expected.equals(label)) {
      violations.add(verb + " " + name + ": label " + label + " instead of " + expected);
    }
    try {
      type.getMethod(SemanticType.PARSE_METHOD, String.class);
    } catch (NoSuchMethodException e) {
      violations.add(
          verb + " " + name + ": " + type.getSimpleName() + " declares no public parse(String)");
    }
  }

  private static List<CommandSpec> verbs(CommandSpec root) {
    List<CommandSpec> specs = new ArrayList<>();
    if (root.subcommands().isEmpty()) {
      specs.add(root);
    }
    root.subcommands().values().stream()
        .map(CommandLine::getCommandSpec)
        .distinct()
        .forEach(specs::add);
    return specs;
  }
}
