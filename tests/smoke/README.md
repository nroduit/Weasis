# Scripted smoke suite

Each `*.weasis` file of this folder is one scenario of the smoke suite: the commands a tester
would type through `weasis-cli`, with `@expect` lines turning the answers into assertions.
`SmokeSuiteTest` (`tests/src/test/java/org/weasis/tests/smoke`) starts one Weasis from the local
Maven repository with its own profile under `tests/target/smoke-xdg`, writes the synthetic study of
`SmokeData` (a CT stack and an MR stack with known geometry and pixel values) into
`tests/target/smoke-data`, runs every script in one connection each and fails when a step fails.

## Run it

The suite needs a display and the modules installed in the local repository:

```bash
mvn -B -ntp install -DskipTests                       # the plugins the launcher loads
xvfb-run -a mvn -pl tests -Dweasis.smoke=true -Dtest=SmokeSuiteTest -Dsurefire.failIfNoSpecifiedTests=false test
```

On a workstation with a display, drop `xvfb-run -a`. The plain build never runs it: the test is
off unless `-Dweasis.smoke=true`.

Each script writes its JUnit report to `tests/target/surefire-reports/TEST-smoke-<name>.xml`, the
captures go to `tests/target/smoke-out`, the application log to
`tests/target/smoke-xdg/state/weasis/log`.

## Write a scenario

- One command per line, `$name = <command>` keeps the answer, `${name.path}` reads inside it,
  `@expect <value> <op> <value>` asserts (`==`, `!=`, `<`, `<=`, `>`, `>=`, `~ <n> +-<tol>`,
  `matches <regex>`), `$bad = @fails <command>` pins a refusal (the step passes when the command
  is refused, `${bad.code}` and `${bad.message}` read its error); see `skills/weasis/SKILL.md`,
  *Several commands in one call*.
- The parameters every script receives: `data` (the folder of the study), `out` (a scratch
  folder), `study`, `ct` and `mr` (the UIDs of `SmokeData`).
- Check a script without running it: `weasis-cli script:check --file smoke/<name>.weasis` reports
  the unknown commands and options, the unterminated quotes, a reference used before the step that
  assigns it, and lists the parameters the script expects from `--set`.
- Leave the display as you found it: a scenario that opens viewers ends with a rollback or a
  `dicom:unload`, so the scripts stay independent and their order does not matter.
- A bug found by a tester or an agent becomes a scenario here, with the issue number in a comment;
  `05-exploratory-findings.weasis` pins what the exploratory agent pass of 2026-11-09 found;
  `06-animation.weasis` runs the cine export and the recording verbs.

The scripts the testers of a preview replay on their own studies live in `skills/weasis/scenarios`
(one per feature, shipped with the installer); `SmokeSuiteTest` parses every one of them against the
instance, so a verb or an option they use cannot go stale unnoticed.
