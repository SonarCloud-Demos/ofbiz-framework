#!/bin/sh
set -eu
route_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
output_dir="$route_dir/build"
mkdir -p "$output_dir"
cp "$route_dir/src/index.html" "$output_dir/index.html"
