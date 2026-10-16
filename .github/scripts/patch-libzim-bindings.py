#!/usr/bin/env python3
"""Expose libzim's alphabetical title-range mode to the JavaScript worker."""
from pathlib import Path
import sys


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch-libzim-bindings.py PATH_TO_LIBZIM_BINDINGS_CPP")
    path = Path(sys.argv[1])
    source = path.read_text(encoding="utf-8")

    method_anchor = "    unsigned int getEstimatedMatches() const {"
    if source.count(method_anchor) != 1:
        raise SystemExit("Could not safely find SuggestionSearchWrapper::getEstimatedMatches.")
    if "void forceRangeSuggestion()" not in source:
        source = source.replace(
            method_anchor,
            "    void forceRangeSuggestion() { search_.forceRangeSuggestion(); }\n\n" + method_anchor,
            1,
        )

    binding_anchor = 'class_<SuggestionSearchWrapper>("SuggestionSearch")\n      .constructor<>()'
    binding_replacement = (
        binding_anchor
        + '\n      .function("forceRangeSuggestion", &SuggestionSearchWrapper::forceRangeSuggestion)'
    )
    if '.function("forceRangeSuggestion", &SuggestionSearchWrapper::forceRangeSuggestion)' not in source:
        if source.count(binding_anchor) != 1:
            raise SystemExit("Could not safely find the SuggestionSearch binding block.")
        source = source.replace(binding_anchor, binding_replacement, 1)

    path.write_text(source, encoding="utf-8")
    print("Added SuggestionSearchWrapper.forceRangeSuggestion() and its Embind binding.")


if __name__ == "__main__":
    main()
