# Testing with commands

Every feature of Weasis is a command, so a test of the running application is a script of commands with expectations, and a test of a namespace is a unit test of its Picocli model.
The client and its syntax are described in the Agent Skill ([skills/weasis/SKILL.md](../skills/weasis/SKILL.md)); this page says how the tests are organized.

## Scripts with expectations

A `.weasis` script is one command per line.
`$name = <command>` keeps the JSON answer, `${name.path}` reads inside it, and `@expect <value> <op> <value>` asserts (`==`, `!=`, `<`, `<=`, `>`, `>=`, `~ <n> +-<tol>`, `matches <regex>`):

```text
dicom:unload --all
$load = dicom:load --path ${data} --wait
$info = dicom:info --series ${ct}
@expect ${info.modality} == CT
@expect ${info.imageCount} == 24
```

The script runs against a running instance through `script:run --file <script> --set key=value --report <junit.xml>`; `--keep-going` runs every step and reports all failures, `script:check --file` parses a script without running it, reports the quotes, verbs and references that would fail and lists the parameters it expects.
A refusal is pinned like an answer: `$bad = @fails <command>` passes when the command is refused and keeps its error (`${bad.code}`, `${bad.message}`), so a bug that was a missing check becomes a one-line test.
The report is a JUnit XML file, one test case per `@expect`, so a CI reads it like any other suite.
A script runs inside a checkpoint: `--rollback` restores the display when a step fails, `--keep` leaves it, and the summary names the checkpoint it created.

## The smoke suite

[tests/smoke](../tests/smoke/README.md) is the suite of a candidate build: `SmokeSuiteTest` starts one Weasis from the local Maven repository on a synthetic study with known geometry and values, runs every script of the folder through the command client and turns each into a JUnit report.
It runs under `xvfb-run` in CI after the install, and on a workstation with:

```bash
mvn -B -ntp install -DskipTests
xvfb-run -a mvn -pl tests -Dweasis.smoke=true -Dtest=SmokeSuiteTest -Dsurefire.failIfNoSpecifiedTests=false test
```

A scenario leaves the display as it found it, so the scripts stay independent.

## The tests a namespace carries

Next to its `CommandExtension`, a namespace pins its contract with plain JUnit tests on the model, without a running application:

- a **vocabulary test**: `CommandVocabulary.violations(namespace, model)` and `CommandVocabulary.resultViolations(namespace, model)` are empty, so every value is labelled and typed and every verb declares its result;
- a **parse test** of the command lines it documents: `CommandModel.parse(model, "sample:info --json")` does not throw, so a documented line keeps parsing across releases (`LegacyCommandsTest` of the package namespaces pins the 4.x lines Weasis keeps);
- an **execution test** where the verb has no UI dependency: `CommandModel.execute(model, "info", args)` returns a `CommandResult` with the status and the body to check.

The plugin archetypes generate the first two for the sample namespace.

## How a bug becomes a script

A bug reported by a tester or found by an agent is reproduced as a script: the commands that load the data, the verb that misbehaves, and the `@expect` that states the right answer.
The script joins the suite with the issue number in a comment, fails until the fix lands and guards the behavior afterwards.
When the data cannot be synthetic, the scenario names the public dataset it needs and the suite skips it where the dataset is absent.
