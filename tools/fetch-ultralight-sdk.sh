#!/usr/bin/env bash
# Downloads the Ultralight SDK revision used by ultralight-java 0.4.12.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/natives/ultralight-legacy"
VERSION="b8daecd"
OS="$(uname -s)"
case "$OS" in
  Darwin) PLAT="mac-x64" ;;
  Linux) PLAT="linux-x64" ;;
  MINGW*|MSYS*|CYGWIN*|Windows_NT) PLAT="win-x64" ;;
  *)
    echo "Unsupported OS: $OS" >&2
    exit 1
    ;;
esac

URL="https://ultralight-sdk.sfo2.cdn.digitaloceanspaces.com/ultralight-sdk-${VERSION}-${PLAT}.7z"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading Ultralight SDK ${VERSION} (${PLAT})..."
curl -fL --progress-bar -o "$TMP/ultralight.7z" "$URL"

if ! command -v 7z >/dev/null 2>&1; then
  echo "p7zip (7z) is required to extract the SDK." >&2
  exit 1
fi

echo "Extracting..."
7z x "$TMP/ultralight.7z" -o"$TMP/extracted" >/dev/null

rm -rf "$DEST"
mkdir -p "$DEST"
cp -R "$TMP/extracted/bin" "$DEST/"
cp -R "$TMP/extracted/resources" "$DEST/"
if [[ -d "$TMP/extracted/license" ]]; then
  cp -R "$TMP/extracted/license" "$DEST/"
fi
echo "$VERSION" >"$DEST/.version"

# macOS SDK links glib/gstreamer against Homebrew absolute paths; vendor the deps.
if [[ "$PLAT" == "mac-x64" ]]; then
  echo "Vendoring macOS companion libraries (pcre, xz)..."
  AUTH=(-H "Authorization: Bearer QQ==")
  curl -fsSL "${AUTH[@]}" -H "Accept: application/vnd.oci.image.layer.v1.tar+gzip" \
    -o "$TMP/pcre.tar.gz" \
    "https://ghcr.io/v2/homebrew/core/pcre/blobs/sha256:df17d29f345822247b05d23c6faeddbac0d0a16805e6080fd67f7cffe2c54e32"
  mkdir -p "$TMP/pcre" && tar -xzf "$TMP/pcre.tar.gz" -C "$TMP/pcre"
  cp -f "$TMP"/pcre/pcre/*/lib/libpcre.1.dylib "$DEST/bin/"

  # Resolve an Intel macOS xz bottle layer via the formula index.
  curl -fsSL "${AUTH[@]}" -H "Accept: application/vnd.oci.image.index.v1+json" \
    "https://ghcr.io/v2/homebrew/core/xz/manifests/5.8.1" -o "$TMP/xz-index.json"
  XZ_MANIFEST="$(
    python3 - "$TMP/xz-index.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
for m in d.get("manifests", []):
  plat = m.get("platform", {})
  if plat.get("os") == "darwin" and plat.get("architecture") == "amd64":
    print(m["digest"])
    break
PY
  )"
  curl -fsSL "${AUTH[@]}" -H "Accept: application/vnd.oci.image.manifest.v1+json" \
    "https://ghcr.io/v2/homebrew/core/xz/manifests/${XZ_MANIFEST}" -o "$TMP/xz-manifest.json"
  XZ_LAYER="$(python3 -c 'import json; print(json.load(open("'"$TMP/xz-manifest.json"'"))["layers"][0]["digest"])')"
  curl -fsSL "${AUTH[@]}" -H "Accept: application/vnd.oci.image.layer.v1.tar+gzip" \
    -o "$TMP/xz.tar.gz" \
    "https://ghcr.io/v2/homebrew/core/xz/blobs/${XZ_LAYER}"
  mkdir -p "$TMP/xz" && tar -xzf "$TMP/xz.tar.gz" -C "$TMP/xz"
  cp -f "$TMP"/xz/xz/*/lib/liblzma.5.dylib "$DEST/bin/"

  (
    cd "$DEST/bin"
    install_name_tool -id @loader_path/libpcre.1.dylib libpcre.1.dylib
    install_name_tool -id @loader_path/liblzma.5.dylib liblzma.5.dylib
    for glib in libglib-2.0.0.dylib libglib-2.0.dylib; do
      [[ -f "$glib" ]] || continue
      install_name_tool -change /usr/local/opt/pcre/lib/libpcre.1.dylib \
        @loader_path/libpcre.1.dylib "$glib"
    done
    install_name_tool -change /usr/local/opt/xz/lib/liblzma.5.dylib \
      @loader_path/liblzma.5.dylib libgstreamer-full-1.0.dylib
  )
fi

echo "Installed to $DEST"
