#!/bin/bash
# Usage: ./zip.sh <folder> [output]
SRC="${1:?Usage: ./zip.sh <folder> [output]}"
OUT="${2:-$SRC}"
if [ ! -d "$SRC" ]; then echo "Folder not found: $SRC"; exit 1; fi
cd "$SRC"
zip -r "../$OUT.egg" . -x "*.DS_Store" -x "__MACOSX/*"
cd ..
echo "Created: $OUT.egg"
