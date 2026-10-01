#!/bin/bash
# Developer entry point of the Weasis command-line client, for a source build: runs the client class
# of the launcher jar found in weasis-launcher/target. Installed Weasis ships the weasis-cli launcher.
#
#   ./weasis-cli.sh info    run one command
#   . ./weasis-cli.sh       install a weasis-cli function in the current shell, then: weasis-cli info
#
# Export WEASIS_JDK as its own command before sourcing, as a "VAR=x . script" prefix applies to the
# sourcing only and the function would later fall back to JAVA_HOME:
#   export WEASIS_JDK=/path/to/jdk-27
#   . /path/to/weasis-cli.sh
#
# WEASIS_LAUNCH_COMMAND and WEASIS_LAUNCH_DIR override what --launch starts (by default the jar of
# this source build, in weasis-launcher/); set them to run an installed Weasis instead.
#
# The JDK is taken from WEASIS_JDK, else JAVA_HOME, else the PATH. Point it at the JDK the installers
# bundle (the PATH java is often older) to run the client on the same runtime, which version-gated
# behaviour such as AOT class linking (JDK 27) depends on. The AOT cache itself stays off here: it is
# only trained from a jpackage launcher, never from a plain java command.
#   WEASIS_JDK=/path/to/jdk-27 ./weasis-cli.sh info
#
# To call weasis-cli plainly before an installer has registered it, link this file from a directory
# of the PATH (the macOS package later links /usr/local/bin/weasis-cli to the bundled launcher):
#   ln -s "$PWD/weasis-distributions/script/weasis-cli.sh" ~/.local/bin/weasis-cli

# Path of this file, so that the repository root is still known once the function is sourced.
if [ -n "$BASH_VERSION" ]; then
  weasis_cli_self="${BASH_SOURCE[0]}"
elif [ -n "$ZSH_VERSION" ]; then
  weasis_cli_self="${(%):-%x}"
else
  weasis_cli_self="$0"
fi
# Called through a link on the PATH, the repository is found from the target of the link.
weasis_cli_file="$(readlink -f "$weasis_cli_self" 2>/dev/null || echo "$weasis_cli_self")"
WEASIS_CLI_HOME="$(cd "$(dirname "$weasis_cli_file")/../.." && pwd)"
unset weasis_cli_file

weasis-cli() {
  local jar java_cmd jdk_home target app_version
  # target/ keeps the jars of the branches built before, so the name of the current one is read from
  # weasis-parent rather than guessed: picking the first of the list ran an outdated client, or one
  # with no client at all, and failed on a missing org.weasis.launcher.CommandClient. The newest
  # build answers for a tree whose version cannot be read.
  target="$WEASIS_CLI_HOME/weasis-launcher/target"
  app_version=$(sed -n \
    -e 's/.*<revision>\([^<]*\)<.*/\1/p' \
    -e 's/.*<changelist>\([^<]*\)<.*/\1/p' \
    "$WEASIS_CLI_HOME/weasis-parent/pom.xml" 2>/dev/null | head -n 2 | tr -d '\n')
  jar="$target/weasis-launcher-$app_version.jar"
  if [ -z "$app_version" ] || [ ! -f "$jar" ]; then
    jar=$(ls -t "$target"/weasis-launcher-*.jar 2>/dev/null | grep -v -- '-sources' | head -n 1)
  fi
  if [ -z "$jar" ] || [ ! -f "$jar" ]; then
    echo "weasis-launcher jar not found: build it with 'mvn -pl weasis-launcher install -DskipTests'" >&2
    return 5
  fi

  java_cmd="java"
  for jdk_home in "$WEASIS_JDK" "$JAVA_HOME"; do
    if [ -n "$jdk_home" ]; then
      if [ ! -x "$jdk_home/bin/java" ]; then
        echo "no java executable in $jdk_home/bin: fix WEASIS_JDK or JAVA_HOME" >&2
        return 5
      fi
      java_cmd="$jdk_home/bin/java"
      break
    fi
  done

  # The jar targets the release of weasis-parent: report a too old JDK rather than let the JVM fail
  # with an UnsupportedClassVersionError. Reading the release file avoids starting a second JVM.
  local home version required
  home="$jdk_home"
  if [ -z "$home" ]; then
    home=$(readlink -f "$(command -v java)" 2>/dev/null) && home=${home%/bin/java}
  fi
  version=$(sed -n 's/^JAVA_VERSION="\([^"]*\)"/\1/p' "$home/release" 2>/dev/null)
  version=${version#1.}
  version=${version%%[!0-9]*}
  required=$(sed -n 's/.*<java-version>\([0-9]*\).*/\1/p' "$WEASIS_CLI_HOME/weasis-parent/pom.xml" \
    2>/dev/null | head -n 1)
  if [ -n "$version" ] && [ -n "$required" ] && [ "$version" -lt "$required" ]; then
    echo "$java_cmd is Java $version, the client needs $required or later: set WEASIS_JDK" >&2
    return 5
  fi

  # --launch has no launcher next to a source build: start the jar that was just built instead, with
  # the options and the working directory of the IDE launch configuration.
  local options="-Xms64m -Xmx1024m -Dsun.awt.disablegrab=true --enable-native-access=ALL-UNNAMED"
  WEASIS_LAUNCH_COMMAND="${WEASIS_LAUNCH_COMMAND:-\"$java_cmd\" $options -cp \"$jar\" org.weasis.launcher.AppLauncher}" \
    WEASIS_LAUNCH_DIR="${WEASIS_LAUNCH_DIR:-$WEASIS_CLI_HOME/weasis-launcher}" \
    "$java_cmd" -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xshare:auto -cp "$jar" \
    org.weasis.launcher.CommandClient "$@"
}

# Executed rather than sourced ($0 is this file): run the command and exit with its status.
if [ "$0" = "$weasis_cli_self" ]; then
  weasis-cli "$@"
  exit $?
fi
unset weasis_cli_self
