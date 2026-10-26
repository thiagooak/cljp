# cljp

A proof of concept native-binary CLI for Clojure projects, wrapping the
[deps and CLI](https://clojure.org/guides/deps_and_cli) and
[tools.build](https://clojure.org/guides/tools_build) guides behind a single `cljp` (clojure project) command.

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
