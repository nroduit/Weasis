#!/bin/bash
# Single source of truth for the jpackage JDK modules and launch options. It is shared by
# package-weasis.sh (local builds and the Linux deb/rpm built in Docker) and by
# .github/workflows/build-installer.yml (macOS and Windows builds), so that every installer
# ships launchers with the same configuration.
#
# Usage: . launch-options.sh <macosx|windows|linux>
# Defines: JDK_MODULES, customOptions[], commonOptions[]

# jdk.localedata => other locale (en_us) data are included in the jdk.localedata
# jdk.jdwp.agent => package for debugging agent
# Base modules for all platforms
JDK_MODULES_BASE="java.base,java.compiler,java.datatransfer,java.net.http,java.desktop,java.logging,java.management,jdk.management,java.prefs,java.xml,jdk.localedata,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.jdwp.agent,java.sql"

case "$1" in
  macosx)
    JDK_MODULES="$JDK_MODULES_BASE"
    customOptions=("--java-options" "-splash:\$APPDIR/resources/images/about-round.png" \
    "--java-options" "-Dapple.laf.useScreenMenuBar=true" \
    "--java-options" "-Dapple.awt.application.appearance=NSAppearanceNameDarkAqua")
    ;;
  windows)
    JDK_MODULES="$JDK_MODULES_BASE,jdk.crypto.mscapi"
    customOptions=("--java-options" "-splash:\$APPDIR\resources\images\about-round.png")
    ;;
  linux)
    JDK_MODULES="$JDK_MODULES_BASE"
    # sun.awt.disablegrab works around unreliable X11 pointer grabs on XWayland (GNOME/mutter),
    # which can leave Swing popup menus invisible until the window regains focus (see issue #819).
    customOptions=("--java-options" "-splash:\$APPDIR/resources/images/about-round.png" \
    "--java-options" "-Dsun.awt.disablegrab=true")
    ;;
  *)
    echo -e "ERROR: launch-options.sh expects a platform (macosx, windows or linux), got '$1'" >&2
    exit 1
    ;;
esac

# Options applied to the main Weasis launcher on every platform. The Dicomizer launcher does not
# inherit them: jpackage drops the command line --java-options as soon as an --add-launcher
# properties file defines java-options, so they are repeated in
# resources/<platform>/dicomizer-launcher.properties.
commonOptions=("--java-options" "-Dgosh.port=17179" \
"--java-options" "--enable-native-access=ALL-UNNAMED" \
"--java-options" "-XX:MaxRAMPercentage=25" \
"--java-options" "-XX:+UseStringDeduplication" \
"--java-options" "-Djavax.accessibility.assistive_technologies=org.weasis.launcher.EmptyAccessibilityProvider" \
"--java-options" "-Djavax.accessibility.screen_magnifier_present=false")

# Optional deployment options (configuration service URL, institutional defaults). The file is not
# part of the public distribution: when it is absent, only the options above are applied.
launchOptionsDir=$(dirname "${BASH_SOURCE[0]}")
if [[ -f "${launchOptionsDir}/launch-options-site.sh" ]] ; then
  source "${launchOptionsDir}/launch-options-site.sh"
fi