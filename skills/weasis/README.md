# Weasis skill

An [Agent Skill](https://agentskills.io) that teaches a terminal agent (Claude Code, Codex, Gemini
CLI, ...) how to drive a running Weasis through `weasis-cli`.

- `SKILL.md`: the workflows, coordinate conventions and rules the agent follows. Its body is
  **generated**: the text lives next to the code (`agent-skill.md` in the launcher) and the running
  instance prints it with `weasis-cli help --agent`, filled with its own version, protocol and
  namespaces, and without the sections of a namespace it does not load. Only the front matter of
  the file is specific to the host format.
- `examples/`: a sample script for `script:run`, in the format the instructions describe.
- `scenarios/`: one replayable script per feature of the 5.0 preview, for the testers of a beta
  (see its `README.md`); also examples of `script:run` with `@expect` on real studies.
- `pointers/`: one line for a host that reads its own instruction file (`AGENTS.md`, `GEMINI.md`,
  `CLAUDE.md`) instead of a skill folder — "run `weasis-cli help --agent`".
- `reference.md`: every command namespace and verb with its options, a snapshot of
  `weasis-cli help --markdown`.
- `commands.json`: the same as a machine-readable descriptor (command API version, protocol
  version, verbs, options, since and deprecated markers), a snapshot of `weasis-cli help --json`.

Install it by copying or linking this folder into the agent's skills directory, for example
`~/.claude/skills/weasis` for Claude Code. The folder is also shipped with the application under
`lib/app/skills/weasis`.

## Without the installer

The client is a class of the launcher jar, so a build or the portable distribution can use it
directly; a shell function gives the same syntax as the packaged launcher:

```
weasis-cli() { java -cp /path/to/weasis-launcher-<version>.jar org.weasis.launcher.CommandClient "$@"; }
weasis-cli weasis:info
```

Weasis itself can be started any way (installer, portable folder, IDE): the client finds the
running instance through the singleton file of the current user, and `--instance <id>` picks one
when several run.

To refresh the three snapshots after a change of the command models or of the instructions, run
against a running instance:

```
weasis-cli help --agent > /tmp/skill-body.md    # then keep the front matter of SKILL.md
weasis-cli help --markdown > skills/weasis/reference.md
weasis-cli help --json > skills/weasis/commands.json
```

A namespace only appears when its plugin is loaded, so the two snapshots are the **merge of two
profiles**: the default one holds every namespace but `acquire:`, which belongs to the Dicomizer.
Dump the second one from a Dicomizer started with the filtered configuration of the build
(`weasis-launcher/target/conf/dicomizer.json`, where the Maven repository is resolved), then add
its `acquire` section to the reference and its `acquire` namespace to the descriptor, both in
alphabetical order. `SKILL.md` is written by hand: check its spellings against the new reference.
