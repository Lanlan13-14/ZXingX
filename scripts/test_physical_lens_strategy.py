#!/usr/bin/env python3
"""Pure model tests for PhysicalLensStrategy."""

from __future__ import annotations

import math
from dataclasses import dataclass


@dataclass(frozen=True)
class Lens:
    id: str
    score: float
    ratio: float


def normalized(raw: list[tuple[str, float]], logical: float | None) -> list[Lens]:
    valid = sorted({i: s for i, s in raw if i and s > 0 and math.isfinite(s)}.items(), key=lambda x: x[1])
    if not valid:
        return []
    if logical and logical > 0 and math.isfinite(logical):
        main = min(valid, key=lambda x: abs(math.log(x[1] / logical)))[1]
    else:
        main = valid[len(valid) // 2][1]
    return [Lens(i, s, s / main) for i, s in valid]


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
    lenses = normalized([('uw', 0.45), ('main', 1.0), ('tele', 3.2)], 1.0)
    failed += check('ratios', [round(x.ratio, 2) for x in lenses] == [0.45, 1.0, 3.2])
    failed += check('0.5 selects ultra-wide', select(lenses, 0.5).id == 'uw')
    failed += check('1.0 selects main', select(lenses, 1.0).id == 'main')
    failed += check('2.0 remains main + digital', select(lenses, 2.0).id == 'main')
    failed += check('3.2 selects tele', select(lenses, 3.2).id == 'tele')
    failed += check('tele local 1x', abs(local(lenses[2], 3.2) - 1.0) < 1e-6)
    failed += check('tele local 2x at virtual 6.4', abs(local(lenses[2], 6.4) - 2.0) < 1e-6)
    fallback = normalized([('a', 0.6), ('b', 1.2), ('c', 4.8)], None)
    failed += check('median fallback is main', [round(x.ratio, 2) for x in fallback] == [0.5, 1.0, 4.0])
    failed += check('dedupe ids', len(normalized([('a', 1.0), ('a', 2.0)], 1.0)) == 1)
    print(f'\n{9 - failed}/9 passed')
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
                                                                             