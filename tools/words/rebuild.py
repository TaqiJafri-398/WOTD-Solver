#!/usr/bin/env python3
"""Rebuild res/raw/words_N.txt with the new ranking tiers.

Tier 0: tools/words/past_answers.txt  (known past WODL answers, file order)
Tier 1: tools/words/curated_final.txt (hand-curated crypto/finance vocab, file order)
Tier 2: previous file contents          (old crypto terms, then frequency order)

Rules: ^[a-z]{3,8}$ only, deduplicated (first occurrence wins = highest rank).
"""
import re
import os

BASE = os.path.expanduser("~/workspace/wotdsolver")
RAW = os.path.join(BASE, "res/raw")
WORDS = os.path.join(BASE, "tools/words")
VALID = re.compile(r"^[a-z]{3,8}$")


def load_list(path):
    out = []
    with open(path) as f:
        for line in f:
            w = line.strip().lower()
            if not w or w.startswith("#"):
                continue
            if VALID.match(w):
                out.append(w)
    return out


def main():
    past = load_list(os.path.join(WORDS, "past_answers.txt"))
    curated = load_list(os.path.join(WORDS, "curated_final.txt"))
    print(f"past answers: {len(past)}, curated: {len(curated)}")
    for n in range(3, 9):
        seen = set()
        out = []

        def add(w):
            if len(w) == n and w not in seen:
                seen.add(w)
                out.append(w)

        for w in past:
            add(w)
        for w in curated:
            add(w)
        old_path = os.path.join(RAW, f"words_{n}.txt")
        with open(old_path) as f:
            for line in f:
                add(line.strip())
        with open(old_path, "w") as f:
            f.write("\n".join(out) + "\n")
        tier0 = sum(1 for w in out if w in set(past))
        print(f"words_{n}.txt: {len(out)} words ({tier0} past answers at top)")
    # sanity: no junk
    bad = 0
    for n in range(3, 9):
        for line in open(os.path.join(RAW, f"words_{n}.txt")):
            w = line.strip()
            if not VALID.match(w) or len(w) != n:
                print("BAD:", n, repr(w))
                bad += 1
    print("junk lines:", bad)


if __name__ == "__main__":
    main()
