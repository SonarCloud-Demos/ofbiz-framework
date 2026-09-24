#!/bin/sh
set -eu
shell_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
output_dir="$shell_dir/build"
mkdir -p "$output_dir"
cp "$shell_dir"/src/* "$output_dir"/
