# AGENTS.md

Rules for every agent (and human) working on chromekit, the shared cybrcore UI kit.

## Hard rules
- **"Don't break stuff or remove features tho! Just make sure it's written smartly." (Oni3xe)**
  A chromekit change must not change any consuming app's look or behaviour unless Oni3xe has
  greenlit that exact change. Refactors prove "no change" (token diff, generated-file diff, code
  diff, font sha256) in the PR body.
- Work on feature branches and open pull requests. Never push to `main`, never merge into `main`.
- Only Chief of Staff merges pull requests and creates release tags (`v1.0`, ...).
- Never force push (no `--force`, no `--force-with-lease`), never rewrite pushed history, never move
  or delete a tag. Apps pin chromekit by tag; a moved tag silently changes every app.
- Never commit secrets: no PATs, deploy keys, keystores, passwords, tokens or `local.properties`.
- Pin every GitHub Action to a full commit SHA with a version comment.
- No network code, telemetry or analytics in the kit; the module adds no Android permissions.
- `tokens/tokens.json` is the approved cybrcore tokens file, copied unmodified from the design vault
  (`UI-Standard-v1.1.md`, `tokens.v1.json`). Never hand-edit it; a new cybrcore version comes from
  the design vault and is a version bump in every app's `cybrcore.version`.
- Generated files (`Tokens.kt`, `cybrcore_tokens.xml`) are never edited by hand; change the
  generator and regenerate.
- The fonts stay under the SIL OFL 1.1 with their notices; the MIT licence covers the code only.
- App-specific UI stays in the app. The kit holds only what is identical across apps.
- Commit early and often; push as rarely as possible; avoid redundant CI runs.
