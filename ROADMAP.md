# Roadmap

`cljp` wraps the official [deps and CLI](https://clojure.org/guides/deps_and_cli)
and [tools.build](https://clojure.org/guides/tools_build) guides behind a
single, fast-starting native binary. The guiding question for every command
on this list is: *what would most Clojurians reach for, most of the time?*
— not *what could a build tool theoretically do.*

Cargo is the reference point because it gets the shape right: a handful of
verbs (`new`, `add`, `build`, `run`, `test`, `fmt`, `publish`) that cover the
whole day-to-day loop, each one a thin, transparent wrapper over the
underlying toolchain rather than a reimplementation of it. `cljp` should feel
the same way for Clojure.

## Principles (non-goals included)

- **Wrap, don't reimplement.** Every command shells out to `clojure`/
  `tools.deps`/`tools.build` (or an existing, well-known library like
  `cljfmt`/`clj-kondo`). `cljp` never re-implements dependency resolution,
  Maven/Clojars interaction, or its own lockfile format. `deps.edn` stays the
  single source of truth.
- **Transparent.** Commands print the underlying `clojure ...` invocation
  they ran, the way `cargo -v` does. No hidden magic.
- **`build.clj` stays yours.** Scaffolded projects get a normal, hand-editable
  `build.clj`. `cljp build`/`cljp run`/etc. are convenience wrappers around
  it, never a replacement for it.
- **No package registry, no new lockfile format, no editor/REPL integration.**
  Those are solved problems (Clojars/Maven Central, `deps.edn`, nREPL/CIDER/
  Calva) that `cljp` has no business re-solving.

## Phase 1 — foundation

| Command | Does | Why first |
|---|---|---|
| `cljp version` / `--version` | Prints the binary's embedded version | Prerequisite for self-update (below) and just expected of any CLI |
| `cljp add <lib>[@version]` | Adds a dependency to `deps.edn` (resolves the latest version from Clojars/Maven Central if none given) | The single biggest everyday gap in the Clojure CLI today — there's no `cargo add` equivalent; people hand-edit `deps.edn` or paste from Clojars. Highest leverage item on this list. |
| `cljp build` | Runs the current project's `build.clj` (`clj -T:build uber`/`jar`), auto-detecting which task applies | Lets people stop memorizing `-T:build` invocations |
| `cljp run` | Builds nothing; just runs `-main` via `clj -M -m <ns>`, inferring the namespace from `deps.edn`/`build.clj` | Matches `cargo run` muscle memory for newcomers |

## Phase 2 — everyday loop

| Command | Does |
|---|---|
| `cljp test` | Runs the project's test suite (detects `cognitect-labs/test-runner`, `kaocha`, etc. and uses whichever is configured) |
| `cljp fmt` | Wraps `cljfmt` (`cljp fmt` fixes, `cljp fmt --check` just reports) |
| `cljp lint` | Wraps `clj-kondo` |
| `cljp outdated` | Lists dependencies in `deps.edn` with newer versions available on Clojars/Maven Central (read-only; `cljp add` or manual edit still does the bumping) |

## Phase 3 — distribution & lifecycle

| Command | Does |
|---|---|
| `cljp self-update` | Checks GitHub Releases for a newer `cljp`, downloads it, and atomically replaces the running binary |
| *(passive check)* | On ordinary invocations, at most once a week, do a fast/non-blocking check against GitHub Releases; print a one-line nudge (`cljp 0.3.0 is available — run 'cljp self-update'`) if a newer version exists. Never auto-installs. Opt-out via an env var (`CLJP_NO_UPDATE_CHECK=1`). Fails silently offline. |
| `cljp publish` | Wraps `tools.build` + `deps-deploy` to ship a library jar to Clojars |

### Design notes for `cljp self-update`

This is the one item here that isn't "just" a wrapper around an existing
Clojure tool, so it deserves more detail up front:

1. **Needs an embedded version first.** The binary has to know its own
   version at runtime to compare against the latest GitHub release — hence
   `cljp version` landing in Phase 1. Simplest approach: `build.clj` bakes
   the git tag (or `"dev"` for untagged local builds) into a resource file
   at build time; `cljp.main` reads it as a compile-time constant.
2. **Two separate mechanisms, not one:**
   - A **passive check** (read-only, just prints a notice) that piggybacks
     on normal command invocations, rate-limited to ~weekly via a timestamp
     file (e.g. `~/.cache/cljp/last-update-check`).
   - An **explicit `cljp self-update`** command that actually downloads and
     installs. Nothing replaces the running binary without the user asking.
3. **Atomic replace is OS-specific.** On Linux/macOS, download the new
   binary to a temp file in the same directory and `rename(2)` it over the
   current executable — safe even while the old binary is still running,
   since Unix lets you unlink/replace an in-use inode. On Windows you
   generally *can't* overwrite a running `.exe`; the usual trick is to
   rename the current `cljp.exe` aside (e.g. to `cljp.exe.old`) and drop the
   new one in its place, cleaning up the `.old` file on the *next* run.
   There's no existing Clojure library for this (it's a Rust-ecosystem
   pattern — see `rustup`/`self_replace`); this needs to be hand-rolled.
4. **Verify what you download.** Once release checksums/attestations exist
   (see the earlier discussion in this project), `self-update` should check
   them before replacing anything — this is the one command where "did I
   just install what the maintainer actually published" matters most, since
   it runs unattended-ish and replaces an executable.

## Phase 4 — nice-to-have

| Command | Does |
|---|---|
| `cljp repl` | Opinionated REPL launcher (e.g. with `rebel-readline` if present) |
| `cljp doc` | Wraps `codox` (or similar) to generate API docs |
| `cljp tree` | Pretty-prints the dependency tree — mostly for discoverability/consistency under one CLI, since `clojure -Stree` already does this |

## Out of scope (for now)

- Reimplementing or vendoring dependency resolution
- A `cljp`-specific package registry or lockfile
- Editor/REPL integration (nREPL middleware, CIDER/Calva features)
- Cross-compiling native binaries for architectures we don't already build
  in CI (Linux ARM, Intel Mac) — revisit once there's real demand
