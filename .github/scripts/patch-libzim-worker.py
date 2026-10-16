#!/usr/bin/env python3
"""Add paginated title-index support to either the worker source or its built file."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2]
if len(sys.argv) == 2:
    worker = Path(sys.argv[1])
elif len(sys.argv) == 1:
    worker = root / "zipedia" / "vendor" / "libzim-wasm.js"
else:
    sys.exit("Usage: patch-libzim-worker.py [PATH_TO_PREJS_OR_BUILT_WORKER]")

if not worker.is_file():
    sys.exit(f"Could not find the libzim worker file: {worker}")

source = worker.read_text(encoding="utf-8")
needle = "else if (action === 'init') {"
count = source.count(needle)
if count != 1:
    sys.exit(f"Unexpected worker source format: expected one init handler anchor, found {count} in {worker}.")

handler = r"""
else if (action === 'listTitles') {
    try {
        var prefix = String(e.data.query || '');
        var start = Math.max(0, Math.floor(Number(e.data.start) || 0));
        var count = Math.max(1, Math.min(500, Math.floor(Number(e.data.count) || 100)));
        var total = Module.getTitleCount(prefix);
        var entries = Module.listTitles(prefix, start, count);
        var results = [];
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            results.push({ path: entry.getPath(), title: entry.getTitle() });
        }
        outgoingMessagePort.postMessage({ results: results, total: total });
    } catch (error) {
        outgoingMessagePort.postMessage({ results: [], total: 0, error: String(error && error.message || error) });
    }
}
else if (action === 'init') {"""
source = source.replace(needle, handler, 1)
worker.write_text(source, encoding="utf-8")
print(f"Added listTitles action to {worker}")
