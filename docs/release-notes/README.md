# Release notes of the pre-releases

A tag with a suffix (`v5.0.0-beta.1`, `v5.0.0-rc.1`) is a **pre-release**: the Changelog workflow
publishes it on GitHub as a pre-release (never *Latest*, invisible to the update check of the
installed releases) and takes its body from `docs/release-notes/<tag>.md`, committed with the tag.
The workflow fails when the file is missing: a pre-release ships with its notes.

The changelog of the closed issues (`CHANGELOG.md`) is generated at the release only; the
pre-release tags are excluded from it (`.github_changelog_generator`).

One text per pre-release, the same structure every time, written for the testers:

```text
Weasis 5.0 beta N — preview, not for clinical use

What this preview is
  - who it is for, what it is not, how it coexists with 4.x, how to remove it
What is new since beta N-1 (or since 4.8.0 for beta 1)
  - one paragraph per feature, with its documentation page
What to test
  - one scenario per feature, the scripts shipped with the beta, how to send a report
Known problems
  - open blockers, features disabled by default, platforms with less coverage
For developers
  - breaking changes, the plugin guide, the command API version, what may still change
For translators
  - the weasis-5 project, the glossary, the new vocabulary
Credits
  - the testers of the previous beta who agreed to be named
```

The installers of a pre-release carry the full version in their file name
(`Weasis-5.0.0-beta.1-x86-64.msi`, `weasis_5.0.0-0.beta.1_amd64.deb`) and the package version of
the release they precede (`5.0.0`), so that the release upgrades them in place; like a release,
they register the `weasis://` scheme and `weasis-cli`.
See `weasis-distributions/docs/Native-Build-Overview.md`, section *Pre-release builds*.
