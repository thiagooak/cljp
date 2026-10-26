# cljp

A proof of concept native-binary CLI for Clojure projects, wrapping the
[deps and CLI](https://clojure.org/guides/deps_and_cli) and
[tools.build](https://clojure.org/guides/tools_build) guides behind a single `cljp` (clojure project) command.

## Installation

Pre-built binaries are published on the [Releases page](https://github.com/thiagooak/cljp/releases/latest) for:

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

## Usage

```
cljp new <name> [--dir <path>]
```

Scaffolds a new project (`deps.edn`, `build.clj` wired to `tools.build`,
a starter namespace + test, `.gitignore`, `README.md`).

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
