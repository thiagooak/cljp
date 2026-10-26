#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if command -v native-image >/dev/null 2>&1; then
  NATIVE_IMAGE=native-image
elif command -v native-image.cmd >/dev/null 2>&1; then
  NATIVE_IMAGE=native-image.cmd # Windows
else
  echo "error: native-image not found on PATH (install a GraalVM JDK)" >&2
  exit 1
fi

echo "+ clj -T:build uber"
clojure -T:build uber

echo "+ $NATIVE_IMAGE ..."
"$NATIVE_IMAGE" \
  -jar target/cljp.jar \
  -H:Name=cljp \
  -H:+ReportExceptionStackTraces \
  --no-fallback \
  --features=clj_easy.graal_build_time.InitClojureClasses \
  -J-Dclojure.compiler.direct-linking=true \
  -cp "$(clojure -Spath -A:native)" \
  -o cljp

echo "Built ./cljp"
