#!/usr/bin/env python3
"""Expose the archive's title-ordered range directly through libzim's JavaScript bindings."""
from pathlib import Path
import sys


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch-libzim-bindings.py PATH_TO_LIBZIM_BINDINGS_CPP")
    path = Path(sys.argv[1])
    source = path.read_text(encoding="utf-8")
    if "#include <algorithm>" not in source:
        source = source.replace("#include <iostream>", "#include <iostream>\n#include <algorithm>", 1)

    anchor = "// SearchIterator wrapper - Clean version relying on the fixed libzim implementation"
    if source.count(anchor) != 1:
        raise SystemExit("Could not safely locate the end of EntryWrapper in libzim_bindings.cpp.")

    functions = r"""
// List entries in the archive's native title order. Archive::findByTitle returns
// a stable title-ordered range, so callers can browse the entire index by prefix
// and request later pages without depending on an optional Xapian suggestions DB.
std::vector<EntryWrapper> listTitles(std::string prefix, int start, int numResults) {
    try {
        if (!g_archive) {
            return std::vector<EntryWrapper>();
        }
        auto range = g_archive->findByTitle(prefix);
        auto page = range.offset(std::max(0, start), std::max(0, numResults));
        std::vector<EntryWrapper> results;
        for (auto entry : page) {
            results.push_back(EntryWrapper(entry));
        }
        return results;
    } catch (const std::exception& e) {
        std::cout << "Title listing error: " << e.what() << std::endl;
        return std::vector<EntryWrapper>();
    }
}

int getTitleCount(std::string prefix) {
    try {
        if (!g_archive) {
            return 0;
        }
        return g_archive->findByTitle(prefix).size();
    } catch (const std::exception& e) {
        std::cout << "Title count error: " << e.what() << std::endl;
        return 0;
    }
}

"""
    if "std::vector<EntryWrapper> listTitles(" not in source:
        source = source.replace(anchor, functions + anchor, 1)

    source = source.replace(
        '    emscripten::function("getArticleCount", &getArticleCount);',
        '    emscripten::function("getArticleCount", &getArticleCount);\n'
        '    emscripten::function("listTitles", &listTitles);\n'
        '    emscripten::function("getTitleCount", &getTitleCount);',
        1,
    )
    if source.count('emscripten::function("listTitles", &listTitles);') != 1:
        raise SystemExit("Could not add the listTitles binding exactly once.")
    if source.count('emscripten::function("getTitleCount", &getTitleCount);') != 1:
        raise SystemExit("Could not add the getTitleCount binding exactly once.")
    path.write_text(source, encoding="utf-8")
    print("Added listTitles() and getTitleCount() using libzim's title-ordered range.")


if __name__ == "__main__":
    main()
