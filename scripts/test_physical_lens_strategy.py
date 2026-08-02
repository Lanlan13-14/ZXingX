#!/usr/bin/env python3
"""Pure tests for generic CameraX lens selection/binding strategy."""

from __future__ import annotations

import math
from dataclasses import dataclass


@dataclass(frozen=True)
class Lens:
    id: str
    score: float
    ratio: float
    bindings: tuple[str, ...]


def normalized(raw: list[tuple[str, float, list[str]]], logical: float | None) -> list[Lens]:
    grouped: dict[str, list[tuple[float, list[str]]]] = {}
    for stable_id, score, bindings in raw:
        if stable_id and score > 0 and math.isfinite(score) and bindings:
            grouped.setdefault(stable_id, []).append((score, bindings))
    valid = []
    for stable_id, rows in grouped.items():
        score = sum(r[0] for r in rows) / len(rows)
        bindings = tuple(dict.fromkeys(x for row in rows for x in row[1]))
        valid.append((stable_id, score, bindings))
    valid.sort(key=lambda x: x[1])
    if not valid:
        return []
    if logical and logical > 0 and math.isfinite(logical):
        main = min(valid, key=lambda x: abs(math.log(x[1] / logical)))[1]
    else:
        main = valid[len(valid) // 2][1]
    return [Lens(i, s, s / main, b) for i, s, b in valid]


def select(lenses: list[Lens], zoom: float) -> Lens | None:
    if not lenses:
        return None
    z = max(zoom, lenses[0].ratio)
    out = lenses[0]
    for lens in lenses:
        if lens.ratio <= z + 0.015:
            out = lens
    return out


def local(lens: Lens, zoom: float) -> float:
    return max(1.0, zoom / lens.ratio)


def check(name: str, ok: bool) -> int:
    print(('PASS' if ok else 'FAIL'), name)
    return 0 if ok else 1


def main() -> int:
    failed = 0
    lenses = normalized([
        ('lens:uw', 0.45, ['physical:logical0/uw']),
        ('lens:uw', 0.45, ['exact:uw']),
        ('lens:main', 1.0, ['physical:logical0/main']),
        ('lens:tele', 3.2, ['physical:logical0/tele', 'exact:tele']),
    ], 1.0)
    failed += check('ratios', [round(x.ratio, 2) for x in lenses] == [0.45, 1.0, 3.2])
    failed += check('merges physical and exact bindings', lenses[0].bindings == ('physical:logical0/uw', 'exact:uw'))
    failed += check('0.5 selects ultra-wide', select(lenses, 0.5).id == 'lens:uw')
    failed += check('1.0 selects main', select(lenses, 1.0).id == 'lens:main')
    failed += check('2.0 remains main + digital', select(lenses, 2.0).id == 'lens:main')
    failed += check('3.2 selects tele', select(lenses, 3.2).id == 'lens:tele')
    failed += check('tele local 1x', abs(local(lenses[2], 3.2) - 1.0) < 1e-6)
    failed += check('tele local 2x', abs(local(lenses[2], 6.4) - 2.0) < 1e-6)
    fallback = normalized([('a', .6, ['a']), ('b', 1.2, ['b']), ('c', 4.8, ['c'])], None)
    failed += check('median fallback main', [round(x.ratio, 2) for x in fallback] == [.5, 1.0, 4.0])
    print(f'\n{9 - failed}/9 passed')
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
