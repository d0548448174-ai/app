# Open-source notices

Zipedia uses the **javascript-libzim** WebAssembly reader from OpenZIM to read the ZIM archive format.

- Upstream source: https://github.com/openzim/javascript-libzim/tree/v0.95
- Upstream license: GNU General Public License version 3 or later (GPL-3.0-or-later)
- Build modifications: this repository applies \`.github/scripts/patch-libzim-bindings.py\` and \`.github/scripts/patch-libzim-worker.py\` so the reader can enumerate title ranges alphabetically from the local archive.
- Reproducible build: see \`.github/workflows/deploy-zipedia.yml\`. The build downloads the tagged source, applies the documented patches, compiles the WebAssembly runtime, and deploys the static app.

The application UI and styling are separate from the upstream reader. The upstream libzim code and license remain those of their original authors.
