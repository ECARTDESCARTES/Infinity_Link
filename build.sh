#!/usr/bin/env bash
# Build unique Windows : tests purs, javap, javac 25, jar, puis vrais mixins via Knot sans fen?tre.
# Les caches/temporaires restent sous link/build/l3 ; aucun jar n'est install?.
set -euo pipefail
cd "$(dirname "$0")"
exec python test/build_l3.py "$@"
