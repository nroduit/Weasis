@echo off
rem Developer entry point of the Weasis command-line client, for a source build (see weasis-cli.sh).
rem The JDK is taken from WEASIS_JDK, else JAVA_HOME, else the PATH.
setlocal
for %%d in ("%~dp0..\..") do set "ROOT=%%~fd"
set "TARGET=%ROOT%\weasis-launcher\target"
rem target/ keeps the jars of the branches built before, so the name of the current one is read from
rem weasis-parent rather than taken from whichever the listing yields first: that ran an outdated
rem client, or one with no client at all (see weasis-cli.sh).
for /f "tokens=2 delims=<> " %%v in ('findstr /i /c:"<revision>" "%ROOT%\weasis-parent\pom.xml" 2^>nul') do (
  if not defined REVISION set "REVISION=%%v"
)
for /f "tokens=2 delims=<> " %%v in ('findstr /i /c:"<changelist>" "%ROOT%\weasis-parent\pom.xml" 2^>nul') do (
  if not defined CHANGELIST set "CHANGELIST=%%v"
)
rem An empty <changelist> of a release leaves the closing tag as the token.
if "%CHANGELIST:~0,1%"=="/" set "CHANGELIST="
if defined REVISION if exist "%TARGET%\weasis-launcher-%REVISION%%CHANGELIST%.jar" (
  set "JAR=%TARGET%\weasis-launcher-%REVISION%%CHANGELIST%.jar"
)
rem The newest build answers for a tree whose version cannot be read, or whose version is not built.
if not defined JAR (
  for /f "delims=" %%f in ('dir /b /o-d "%TARGET%\weasis-launcher-*.jar" 2^>nul') do call :keepFirstBuilt "%%f"
)
if not defined JAR (
  echo weasis-launcher jar not found: build it with "mvn -pl weasis-launcher install -DskipTests" >&2
  exit /b 5
)
set "JDK_HOME=%WEASIS_JDK%"
if "%JDK_HOME%"=="" set "JDK_HOME=%JAVA_HOME%"
set "JAVA_CMD=java"
if not "%JDK_HOME%"=="" (
  if not exist "%JDK_HOME%\bin\java.exe" (
    echo no java executable in %JDK_HOME%\bin: fix WEASIS_JDK or JAVA_HOME >&2
    exit /b 5
  )
  set "JAVA_CMD=%JDK_HOME%\bin\java.exe"
)
rem --launch has no launcher next to a source build: start the jar that was just built instead.
set "OPTIONS=-Xms64m -Xmx1024m --enable-native-access=ALL-UNNAMED"
if not defined WEASIS_LAUNCH_COMMAND set "WEASIS_LAUNCH_COMMAND="%JAVA_CMD%" %OPTIONS% -cp "%JAR%" org.weasis.launcher.AppLauncher"
if not defined WEASIS_LAUNCH_DIR set "WEASIS_LAUNCH_DIR=%ROOT%\weasis-launcher"
"%JAVA_CMD%" -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xshare:auto -cp "%JAR%" org.weasis.launcher.CommandClient %*
exit /b %ERRORLEVEL%

rem Takes the first jar of the listing that is not a sources one; a call expands %1 without the
rem delayed expansion a for body would need.
:keepFirstBuilt
if defined JAR exit /b 0
set "NAME=%~1"
if not "%NAME:-sources=%"=="%NAME%" exit /b 0
set "JAR=%TARGET%\%NAME%"
exit /b 0
