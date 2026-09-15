#!/bin/sh
# Cross-compile the usage-limits hook for the three supported platforms.
# Output binaries land in scripts/, one per OS, and are committed to the repo, so
# end users need no Go toolchain — only the sh launcher in .claude/hooks/.
#
# Run from anywhere:  sh scripts/usage-limits-src/build.sh
set -eu

SRC_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT_DIR=$(dirname -- "$SRC_DIR")   # scripts/

LDFLAGS="-s -w"

build() {
	os=$1
	arch=$2
	out=$3
	echo "building $out ($os/$arch)"
	( cd "$SRC_DIR" && GOOS="$os" GOARCH="$arch" CGO_ENABLED=0 \
		go build -trimpath -ldflags "$LDFLAGS" -o "$OUT_DIR/$out" . )
}

build darwin  arm64 usage-limits-darwin-arm64
build linux   amd64 usage-limits-linux-amd64
build windows amd64 usage-limits-windows-amd64.exe

echo "done -> $OUT_DIR"
