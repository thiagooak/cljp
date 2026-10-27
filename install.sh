#!/bin/sh
# Install the latest cljp release.
#
#   curl --proto '=https' --tlsv1.2 -fsSL https://raw.githubusercontent.com/thiagooak/cljp/main/install.sh | sh
#
# Environment:
#   CLJP_VERSION  release tag to install (default: latest), e.g. v0.1.0
#   CLJP_HOME     install root (default: $HOME/.cljp); the binary goes in
#                 $CLJP_HOME/bin and shell setup in $CLJP_HOME/env
set -eu

REPO="thiagooak/cljp"
CLJP_HOME="${CLJP_HOME:-$HOME/.cljp}"
BIN_DIR="$CLJP_HOME/bin"
VERSION="${CLJP_VERSION:-latest}"

if [ "$VERSION" = "latest" ]; then
  BASE_URL="https://github.com/$REPO/releases/latest/download"
else
  BASE_URL="https://github.com/$REPO/releases/download/$VERSION"
fi

err() { echo "error: $*" >&2; exit 1; }

case "$(uname -s)-$(uname -m)" in
  Linux-x86_64)                 ASSET="cljp-linux-x86_64" ;;
  Darwin-arm64|Darwin-aarch64)  ASSET="cljp-macos-arm64" ;;
  *) err "no pre-built binary for $(uname -s)/$(uname -m); see https://github.com/$REPO#installation" ;;
esac

if command -v curl >/dev/null 2>&1; then
  fetch() { curl -fsSL -o "$2" "$1"; }
elif command -v wget >/dev/null 2>&1; then
  fetch() { wget -qO "$2" "$1"; }
else
  err "curl or wget is required"
fi

if command -v sha256sum >/dev/null 2>&1; then
  sha256() { sha256sum "$1" | cut -d' ' -f1; }
elif command -v shasum >/dev/null 2>&1; then
  sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
else
  err "sha256sum or shasum is required to verify the download"
fi

# Remember any cljp that is already reachable so we can warn about it later.
OTHER="$(command -v cljp 2>/dev/null || true)"
[ "$OTHER" = "$BIN_DIR/cljp" ] && OTHER=""

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading $ASSET ($VERSION)..."
fetch "$BASE_URL/$ASSET" "$TMP/$ASSET" || err "could not download $BASE_URL/$ASSET"
fetch "$BASE_URL/SHA256SUMS" "$TMP/SHA256SUMS" || err "could not download $BASE_URL/SHA256SUMS"

expected="$(awk -v f="$ASSET" '$2 == f { print $1 }' "$TMP/SHA256SUMS")"
[ -n "$expected" ] || err "$ASSET not listed in SHA256SUMS"
actual="$(sha256 "$TMP/$ASSET")"
[ "$expected" = "$actual" ] || err "checksum mismatch for $ASSET (expected $expected, got $actual)"
echo "Checksum OK."

mkdir -p "$BIN_DIR"
chmod +x "$TMP/$ASSET"
mv "$TMP/$ASSET" "$BIN_DIR/cljp"
if [ "$(uname -s)" = "Darwin" ]; then
  xattr -d com.apple.quarantine "$BIN_DIR/cljp" 2>/dev/null || true
fi

echo "Installed cljp to $BIN_DIR/cljp"

# Shell setup lives in $CLJP_HOME/env (and env.fish), like rustup's ~/.cargo/env.
# Profiles only source that file, so changing PATH setup later never means
# editing dotfiles again, and uninstalling is just deleting $CLJP_HOME.
# Write $HOME rather than the expanded path so the generated lines stay portable.
case "$CLJP_HOME" in
  "$HOME"/*) HOME_EXPR="\$HOME/${CLJP_HOME#"$HOME"/}" ;;
  *)         HOME_EXPR="$CLJP_HOME" ;;
esac

cat > "$CLJP_HOME/env" <<EOF
#!/bin/sh
# cljp shell setup: put cljp on PATH (safe to source more than once).
case ":\${PATH}:" in
  *:"$HOME_EXPR/bin":*) ;;
  *) export PATH="$HOME_EXPR/bin:\$PATH" ;;
esac
EOF

cat > "$CLJP_HOME/env.fish" <<EOF
# cljp shell setup: put cljp on PATH (safe to source more than once).
if not contains "$HOME_EXPR/bin" \$PATH
    set -x PATH "$HOME_EXPR/bin" \$PATH
end
EOF

# Warn about another cljp if it would win over (or be left behind by) this one.
on_path=0
case ":$PATH:" in *":$BIN_DIR:"*) on_path=1 ;; esac

if [ "$on_path" = 1 ]; then
  active="$(command -v cljp 2>/dev/null || true)"
  if [ -n "$active" ] && [ "$active" != "$BIN_DIR/cljp" ]; then
    echo "warning: $active comes before $BIN_DIR on your PATH and will shadow this install."
    echo "         Remove it, or move $BIN_DIR earlier on your PATH."
  fi
else
  case "$(basename "${SHELL:-}")" in
    zsh)  PROFILE="${ZDOTDIR:-$HOME}/.zshrc";    SOURCE_LINE=". \"$HOME_EXPR/env\"" ;;
    bash) if [ "$(uname -s)" = "Darwin" ]; then PROFILE="$HOME/.bash_profile"; else PROFILE="$HOME/.bashrc"; fi
          SOURCE_LINE=". \"$HOME_EXPR/env\"" ;;
    fish) PROFILE="$HOME/.config/fish/config.fish"; SOURCE_LINE="source \"$HOME_EXPR/env.fish\"" ;;
    *)    PROFILE=""; SOURCE_LINE=". \"$HOME_EXPR/env\"" ;;
  esac

  if [ -z "$PROFILE" ]; then
    echo "Add cljp to your PATH by adding this line to your shell profile:"
    echo "  $SOURCE_LINE"
  elif [ -f "$PROFILE" ] && grep -qF "$HOME_EXPR/env" "$PROFILE"; then
    echo "$PROFILE already sets up cljp; restart your shell to pick it up."
  else
    mkdir -p "$(dirname "$PROFILE")"
    printf '\n# cljp\n%s\n' "$SOURCE_LINE" >> "$PROFILE"
    echo "Added cljp to your PATH in $PROFILE."
    echo "Restart your shell (or run: $SOURCE_LINE) to use cljp."
  fi

  if [ -n "$OTHER" ]; then
    echo "note: another cljp exists at $OTHER; new shells will use the one in $BIN_DIR instead."
  fi
fi

if ! command -v clojure >/dev/null 2>&1; then
  echo "note: cljp requires the Clojure CLI (https://clojure.org/guides/install_clojure), which was not found on PATH."
fi
