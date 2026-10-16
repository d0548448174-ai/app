#!/usr/bin/env python3
"""Add a paginated title-index endpoint to the official libzim worker bundle."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2]
worker = root / "zipedia" / "vendor" / "libzim-wasm.js"
if not worker.is_file():
    sys.exit(f"Could not find the downloaded libzim worker: {worker}")

source = worker.read_text(encoding="utf-8")
needle = "else if (action === 'init') {"
if source.count(needle) != 1:
    sys.exit("Unexpected libzim worker format: could not safely locate the init handler.")

handler = r"""
else if (action === 'listTitles') {
    try {
        var searcher = new Module.SuggestionSearcher();
        var suggestionSearch = searcher.suggest(String(e.data.query || ''));
        if (e.data.range === true) {
            suggestionSearch.forceRangeSuggestion();
        }
        var start = Math.max(0, Math.floor(Number(e.data.start) || 0));
        var count = Math.max(1, Math.min(500, Math.floor(Number(e.data.count) || 100)));
        var entries = suggestionSearch.getResults(start, count);
        var results = [];
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            results.push({ path: entry.getPath(), title: entry.getTitle() });
        }
        outgoingMessagePort.postMessage({ results: results, total: suggestionSearch.getEstimatedMatches() });
    } catch (error) {
        outgoingMessagePort.postMessage({ results: [], total: 0, error: String(error && error.message || error) });
    }
}
else if (action === 'init') {"""
source = source.replace(needle, handler, 1)
worker.write_text(source, encoding="utf-8")
print("Added listTitles action to libzim-wasm.js")
