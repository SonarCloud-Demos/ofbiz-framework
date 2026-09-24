#!/bin/sh
set -eu
route_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
test -f "$route_dir/src/index.html"
rg -q 'aria-label="Application generation: modern"' "$route_dir/src/index.html"
rg -q '<main>' "$route_dir/src/index.html"
