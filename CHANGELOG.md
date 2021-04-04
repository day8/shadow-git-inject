## [Unreleased]

#### Fixed

- Use strings instead of regex literals for patterns in config. Fixes [#2](https://github.com/day8/shadow-git-inject/issues/2).
Thanks to [@schpaa](https://github.com/schpaa) for reporting the issue.

## [0.0.4] - 2021-04-01

#### Fixed

- Remove unnecessary update-ins. No user visible changes. See [#1](https://github.com/day8/shadow-git-inject/issues/1).

## [0.0.3] - 2021-04-01

#### Fixed

- Remove debugging output leftover from last change.

## [0.0.2] - 2021-04-01

#### Changed

- Only walk `:closure-defines` in `build-state` (i.e. the config). This fixes
[#1](https://github.com/day8/shadow-git-inject/issues/1). Thanks to [@thheller](https://github.com/thheller) for reporting and proposing fix.

## [0.0.1] - 2021-03-31

Initial release.
