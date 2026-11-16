#!/bin/bash
# Single source of truth for the jpackage JDK modules and the launch options of stage 1, the
# bootstrap JVM of the native launchers. It is shared by package-weasis.sh (local builds and the
# Linux deb/rpm built in Docker) and by .github/workflows/build-installer.yml (macOS and Windows
# builds), so that every installer ships launchers with the same configuration.
#
# The options of the application process (stage 2) are not here: they are served by the package
# (weasis.launcher.jvm-options in base.json, filtered from the pom) and applied by the bootstrap.
# macOS replaces stage 1 by stage 2 in the same process (execv) instead of spawning it; its .cfg
# still carries the full set for the in-process fallback.
#
# Usage: . launch-options.sh <macosx|windows|linux>
# Defines: JDK_MODULES, customOptions[], commonOptions[]

launchOptionsDir=$(dirname "${BASH_SOURCE[0]}")

# The curated module list is a fact of the package (jdk.modules in the pom, filtered into
# build.properties and into weasis.requires.modules of base.json), so the installers and the
# package agree by construction. jdk.localedata => other locale data; jdk.jdwp.agent => debugging.
JDK_MODULES_BASE=$(grep -i "^jdk.modules=" "${launchOptionsDir}/build.properties" | sed 's/^.*=//')
if [ -z "$JDK_MODULES_BASE" ] ; then
  echo "ERROR: jdk.modules is not defined in ${launchOptionsDir}/build.properties" >&2
  exit 1
fi

# Stage 1 is a tiny JVM: it checks the single instance, resolves the launcher set and spawns
# stage 2 with the served options. Its splash is closed when stage 2 starts.
stage1Options=("--java-options" "-XX:+UseSerialGC" \
"--java-options" "-XX:TieredStopAtLevel=1" \
"--java-options" "-Xshare:auto" \
"--java-options" "-Xmx96m")

case "$1" in
  macosx)
    JDK_MODULES="$JDK_MODULES_BASE"
    # No splash in stage 1: open-file events reach the process LaunchServices started, so the
    # bootstrap replaces itself (execv, same PID) by the stage-2 JVM before AppKit is initialized;
    # stage 2 shows the splash (weasis.launcher.jvm-options). The options below only serve the
    # fallback that runs stage 2 in this process (-Dweasis.bootstrap.inprocess=true, or a failed
    # execv): they must match weasis.launcher.jvm-options of the pom, except the accessibility
    # provider, since the system class loader holds weasis-bootstrap.jar only.
    customOptions=("--java-options" "-Dapple.laf.useScreenMenuBar=true" \
    "--java-options" "-Dapple.awt.application.appearance=NSAppearanceNameDarkAqua")
    commonOptions=("--java-options" "--enable-native-access=ALL-UNNAMED" \
    "--java-options" "-XX:MaxRAMPercentage=25" \
    "--java-options" "-XX:+UseStringDeduplication" \
    "--java-options" "-Djavax.accessibility.assistive_technologies=org.weasis.bootstrap.EmptyAccessibilityProvider" \
    "--java-options" "-Djavax.accessibility.screen_magnifier_present=false")
    ;;
  windows)
    JDK_MODULES="$JDK_MODULES_BASE,jdk.crypto.mscapi"
    customOptions=("--java-options" "-splash:\$APPDIR\resources\images\about-round.png")
    commonOptions=("${stage1Options[@]}")
    ;;
  linux)
    JDK_MODULES="$JDK_MODULES_BASE"
    customOptions=("--java-options" "-splash:\$APPDIR/resources/images/about-round.png")
    commonOptions=("${stage1Options[@]}")
    ;;
  *)
    echo -e "ERROR: launch-options.sh expects a platform (macosx, windows or linux), got '$1'" >&2
    exit 1
    ;;
esac

# Optional deployment options (configuration service URL, institutional defaults). The file is not
# part of the public distribution: when it is absent, only the options above are applied. A
# -Dweasis.* option given here reaches stage 2: the bootstrap forwards every weasis.* property.
if [[ -f "${launchOptionsDir}/launch-options-site.sh" ]] ; then
  source "${launchOptionsDir}/launch-options-site.sh"
fi
