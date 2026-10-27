#!/bin/sh
# Install the latest cljp release.
#
#   curl -fsSL https://raw.githubusercontent.com/thiagooak/cljp/main/install.sh | sh
#
# Environment:
#   CLJP_VERSION      release tag to install (default: latest), e.g. v0.1.0
#   CLJP_INSTALL_DIR  where to put the binary (default: $HOME/.cljp/bin)
set -eu

REPO="thiagooak/cljp"
INSTALL_DIR="${CLJP_INSTALL_DIR:-$HOME/.cljp/bin}"
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

mkdir -p "$INSTALL_DIR"
chmod +x "$TMP/$ASSET"
mv "$TMP/$ASSET" "$INSTALL_DIR/cljp"
if [ "$(uname -s)" = "Darwin" ]; then
  xattr -d com.apple.quarantine "$INSTALL_DIR/cljp" 2>/dev/null || true
fi

echo "Installed cljp to $INSTALL_DIR/cljp"

# Put INSTALL_DIR on PATH via the user's shell profile, like deno/bun/rustup do.
case ":$PATH:" in
  *":$INSTALL_DIR:"*) ;;
  *)
    # Write $HOME rather than the expanded path so the profile line stays portable.
    case "$INSTALL_DIR" in
      "$HOME"/*) DIR_EXPR="\$HOME/${INSTALL_DIR#"$HOME"/}" ;;
      *)         DIR_EXPR="$INSTALL_DIR" ;;
    esac
    PATH_LINE="export PATH=\"$DIR_EXPR:\$PATH\""

    case "$(basename "${SHELL:-}")" in
      zsh)  PROFILE="${ZDOTDIR:-$HOME}/.zshrc" ;;
      bash) if [ "$(uname -s)" = "Darwin" ]; then PROFILE="$HOME/.bash_profile"; else PROFILE="$HOME/.bashrc"; fi ;;
      fish) PROFILE="$HOME/.config/fish/config.fish"
            PATH_LINE="fish_add_path \"$DIR_EXPR\"" ;;
      *)    PROFILE="" ;;
    esac

    if [ -z "$PROFILE" ]; then
      echo "Add $INSTALL_DIR to your PATH, e.g. by adding this to your shell profile:"
      echo "  $PATH_LINE"
    elif [ -f "$PROFILE" ] && grep -qF "$DIR_EXPR" "$PROFILE"; then
      echo "$PROFILE already mentions $DIR_EXPR; restart your shell to pick it up."
    else
      mkdir -p "$(dirname "$PROFILE")"
      printf '\n# cljp\n%s\n' "$PATH_LINE" >> "$PROFILE"
      echo "Added $INSTALL_DIR to your PATH in $PROFILE."
      echo "Restart your shell (or run: . \"$PROFILE\") to use cljp."
    fi
    ;;
esac

if ! command -v clojure >/dev/null 2>&1; then
  echo "note: cljp requires the Clojure CLI (https://clojure.org/guides/install_clojure), which was not found on PATH."
fi
