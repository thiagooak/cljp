# cljp

A proof of concept native-binary CLI for Clojure projects, wrapping the
[deps and CLI](https://clojure.org/guides/deps_and_cli) and
[tools.build](https://clojure.org/guides/tools_build) guides behind a single `cljp` (clojure project) command.

## Requirements

The [Clojure CLI](https://clojure.org/guides/install_clojure)
must be installed and on your `PATH`.

## Installation

### Linux and macOS (Apple Silicon)

```sh
curl -fsSL https://raw.githubusercontent.com/thiagooak/cljp/main/install.sh | sh
```

This downloads the latest release binary, verifies it against the release's
`SHA256SUMS`, and installs it to `~/.cljp/bin/cljp`. If that directory isn't
on your `PATH`, the script appends it to your shell profile.
Set `CLJP_INSTALL_DIR` to install elsewhere.

### Manual download

Pre-built binaries and `SHA256SUMS` are published on the [Releases page](https://github.com/thiagooak/cljp/releases/latest) for:

| Platform                | Asset                        |
|--------------------------|------------------------------|
| Linux (x86_64)           | `cljp-linux-x86_64`          |
| macOS (Apple Silicon)    | `cljp-macos-arm64`           |
| Windows (x86_64)         | `cljp-windows-x86_64.exe`    |

Download the asset for your platform, make it executable, and put it on your `PATH`:

```sh
curl -L -o cljp https://github.com/thiagooak/cljp/releases/latest/download/cljp-linux-x86_64
chmod +x cljp
sudo mv cljp /usr/local/bin/
```

(use `cljp-macos-arm64` instead on macOS). On Windows, download `cljp-windows-x86_64.exe`,
rename it to `cljp.exe`, and place it anywhere on your `%PATH%`.

### macOS: "Apple could not verify..."

`cljp` isn't code-signed/notarized yet, so macOS Gatekeeper will refuse to run a
binary downloaded through a browser. (The install script above isn't affected.) Clear the quarantine flag once after
downloading:

```sh
xattr -d com.apple.quarantine ./cljp
```

(or right-click the file → Open → confirm in the dialog that appears).

## Usage

```
cljp new <name> [--dir <path>]
```

Scaffolds a new project (`deps.edn`, `build.clj` wired to `tools.build`,
a starter namespace + test, `.gitignore`, `README.md`).

```
cljp add <lib>[@version]
```

Adds a dependency to `deps.edn` in the current directory, preserving its
comments and formatting. Without `@version`, the latest stable version is
looked up with `clojure -X:deps find-versions`.
Adding a lib that is already present updates its version.

## Dev

```sh
clj -M -m cljp.main new my-app
clj -M -m cljp.main help
```

```sh
clj -X:test
```

## Building the native binary

Requires a GraalVM JDK with `native-image` on `PATH`:

```sh
sdk install java 21.0.4-graalce   # or any recent GraalVM JDK
sdk use java 21.0.4-graalce
gu install native-image

script/native-image.sh
./cljp new my-app
```
