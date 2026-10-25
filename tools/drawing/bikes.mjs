// Service-manual plates for each silhouette (left side view, units ≈ 0.45 per mm, ground at y=640).
// Every maintenance zone is a <g data-z="…"> group; export.mjs turns these into app assets.
// Parts on the far side or hidden by bodywork use the "h" class (dashed), as in a drawing.
// ponytail: proportions by eye from typical bikes of each type; a 3D → line-art pass can replace any plate keeping the zone ids.

export const GROUND = 640;
const rad = d => d * Math.PI / 180, f = n => n.toFixed(1);
export const pol = (c, r, a) => [c[0] + r * Math.cos(rad(a)), c[1] + r * Math.sin(rad(a))];
const pt = p => f(p[0]) + ' ' + f(p[1]);
const lerp = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
const rotAbout = (p, c, deg) => { const a = rad(deg), dx = p[0] - c[0], dy = p[1] - c[1]; return [c[0] + dx * Math.cos(a) - dy * Math.sin(a), c[1] + dx * Math.sin(a) + dy * Math.cos(a)]; };
/** Steering-local coordinates: (dx, dy) from the front axle along the fork axis, tilted back by the rake. */
const steer = (F, rake) => (dx, dy) => rotAbout([F[0] + dx, F[1] + dy], F, -rake);
const circ = (c, r, cls = 'l') => `<circle class="${cls}" cx="${f(c[0])}" cy="${f(c[1])}" r="${f(r)}"/>`;
const ln = (a, b, cls = 'l') => `<path class="${cls}" d="M${pt(a)} L${pt(b)}"/>`;
const poly = (ps, cls = 'sf') => `<path class="${cls}" d="M${ps.map(pt).join(' L')} Z"/>`;
const path = (d, cls = 'l') => `<path class="${cls}" d="${d}"/>`;
const pipe = (d, w) => `<path class="po" d="${d}" stroke-width="${w}"/><path class="pi" d="${d}" stroke-width="${(w - 2.6).toFixed(1)}"/>`;
const hex = (c, r) => poly([0, 60, 120, 180, 240, 300].map(a => pol(c, r, a + 30)), 'sf');
const ring = (c, R1, r) => `M${f(c[0] - R1)} ${f(c[1])} a${R1} ${R1} 0 1 0 ${2 * R1} 0 a${R1} ${R1} 0 1 0 ${-2 * R1} 0 Z M${f(c[0] - r)} ${f(c[1])} a${r} ${r} 0 1 0 ${2 * r} 0 a${r} ${r} 0 1 0 ${-2 * r} 0 Z`;
const Z = (id, inner) => `<g data-z="${id}">${inner}</g>`;
const arcBand = (c, r1, r2, a1, a2) =>
  `M${pt(pol(c, r2, a1))} A${r2} ${r2} 0 0 1 ${pt(pol(c, r2, a2))} L${pt(pol(c, r1, a2))} A${r1} ${r1} 0 0 0 ${pt(pol(c, r1, a1))} Z`;

/** Cast wheel with Y spokes. */
function castWheel(c, Rt, Rr, spokes = 5) {
  const k = Rr / 97;
  let s = circ(c, Rt, 'sf') + circ(c, Rt - 5, 'l t') + circ(c, Rr + 9 * k, 'l t');
  for (let a = 0; a < 360; a += 7.5) s += ln(pol(c, Rt - 1.5, a), pol(c, Rt - 10 * Math.max(k, .7), a + 3.5), 'l t');
  s += circ(c, Rr, 'l') + circ(c, Rr - 5, 'l');
  const st = 360 / spokes;
  for (let i = 0; i < spokes; i++) {
    const a = i * st - 90, w = st / 72;
    s += poly([pol(c, 24 * k, a - 11 * w), pol(c, 56 * k, a - 4 * w), pol(c, Rr - 5, a - 14 * w), pol(c, Rr - 5, a - 5 * w), pol(c, 68 * k, a), pol(c, Rr - 5, a + 5 * w), pol(c, Rr - 5, a + 14 * w), pol(c, 56 * k, a + 4 * w), pol(c, 24 * k, a + 11 * w)]);
  }
  s += circ(c, 27 * k, 'sf') + circ(c, 15 * k, 'l t') + hex(c, Math.max(5, 8 * k)) + circ(c, 3, 'l t');
  s += ln(pol(c, Rr - 5, -20), pol(c, Rr - 15, -20), 'l');
  return s;
}

/** Wire-spoke wheel with a knobby tyre. */
function spokeWheel(c, Rt, Rr, n = 36) {
  let s = circ(c, Rt - 7, 'sf') + circ(c, Rt - 12, 'l t');
  for (let a = 0; a < 360; a += 9) s += poly([pol(c, Rt - 7, a - 2.6), pol(c, Rt, a - 2.2), pol(c, Rt, a + 2.2), pol(c, Rt - 7, a + 2.6)]);
  s += circ(c, Rr + 8, 'l t') + circ(c, Rr, 'l') + circ(c, Rr - 4, 'l t');
  for (let i = 0; i < n; i++) {
    const a = i * 360 / n, off = i % 2 ? 24 : -24;
    s += ln(pol(c, 14, a), pol(c, Rr - 4, a + off), 'l t');
  }
  s += circ(c, 22, 'sf') + circ(c, 14, 'l t') + hex(c, 6) + circ(c, 2.5, 'l t');
  for (let i = 0; i < n; i++) s += circ(pol(c, Rr - 2, i * 360 / n + (i % 2 ? 24 : -24)), 1, 'l t');
  return s;
}

function disc(c, Rd, hid) {
  const k = hid ? 'l h' : 'l';
  let s = hid ? '' : `<path class="sf" fill-rule="evenodd" d="${ring(c, Rd, Rd - 15)}"/>`;
  s += circ(c, Rd, k) + circ(c, Rd - 15, k);
  if (!hid) for (let a = 0; a < 360; a += 10) s += circ(pol(c, Rd - 5, a), 1.4, 'l t') + circ(pol(c, Rd - 10, a + 5), 1.4, 'l t');
  for (let a = 0; a < 360; a += 45) s += circ(pol(c, Rd - 18, a), 2.6, k);
  for (let a = 22.5; a < 360; a += 45) s += ln(pol(c, 22, a), pol(c, Rd - 18, a + 12), k + ' t');
  return s;
}

function drum(c, r) {
  let s = circ(c, r, 'sf h') + circ(c, r - 6, 'l h t');
  for (let a = 0; a < 360; a += 30) s += ln(pol(c, r - 6, a), pol(c, r, a), 'l h t');
  return s;
}

function sprocket(c, r, n, holes) {
  const ps = [], st = 360 / n;
  for (let i = 0; i < n; i++) { const a = i * st; ps.push(pol(c, r + 3.6, a - st * .16), pol(c, r + 3.6, a + st * .16), pol(c, r - 1.6, a + st * .5)); }
  let s = poly(ps) + circ(c, r - 6, 'l t');
  if (holes) for (let k = 0; k < 5; k++) s += circ(pol(c, r * .56, k * 72 + 36), r * .17, 'l');
  for (let k = 0; k < 5; k++) s += circ(pol(c, Math.min(19, r * .45), k * 72), 1.8, 'l');
  return s;
}

function loopPath(c1, r1, c2, r2) {
  const dx = c2[0] - c1[0], dy = c2[1] - c1[1], d = Math.hypot(dx, dy), th = Math.atan2(dy, dx), al = Math.acos((r1 - r2) / d);
  const t = (c, r, a) => [c[0] + r * Math.cos(a), c[1] + r * Math.sin(a)];
  const p1 = t(c1, r1, th - al), p2 = t(c2, r2, th - al), p3 = t(c2, r2, th + al), p4 = t(c1, r1, th + al);
  return `M${pt(p1)} L${pt(p2)} A${r2} ${r2} 0 0 1 ${pt(p3)} L${pt(p4)} A${r1} ${r1} 0 1 1 ${pt(p1)} Z`;
}

function chain(R, rr, CS, rf) {
  const d = loopPath(R, rr + 1.5, CS, rf + 1.5);
  return sprocket(R, rr, Math.round(rr / 48 * 43), true) + sprocket(CS, rf, Math.round(rf / 18 * 16), false) + pipe(d, 8.5) + `<path class="rollers" d="${d}"/>`;
}

function shock(a, b, coils = 8, w = 12) {
  const v = [b[0] - a[0], b[1] - a[1]], Lh = Math.hypot(...v), n = [-v[1] / Lh, v[0] / Lh];
  const at = (t, o = 0) => [a[0] + v[0] * t + n[0] * o, a[1] + v[1] * t + n[1] * o];
  let s = pipe(`M${pt(at(0))} L${pt(at(1))}`, 9);
  s += poly([at(.5, -8), at(.95, -8), at(.95, 8), at(.5, 8)]);
  let d = `M${pt(at(.14, 0))}`;
  for (let i = 1; i <= coils * 2; i++) d += ` L${pt(at(.14 + (.7 - .14) * i / (coils * 2), i % 2 ? w : -w))}`;
  s += path(d, 'l spring');
  s += poly([at(.70, -w - 1), at(.74, -w - 1), at(.74, w + 1), at(.70, w + 1)]) + poly([at(.10, -w - 1), at(.14, -w - 1), at(.14, w + 1), at(.10, w + 1)]);
  s += circ(at(0), 6, 'sf') + circ(at(0), 2.4, 'l') + circ(at(1), 6, 'sf') + circ(at(1), 2.4, 'l');
  return s;
}

function arm(PV, R, w1 = 19, w2 = 9) {
  const v = [R[0] - PV[0], R[1] - PV[1]], Lh = Math.hypot(...v), n = [-v[1] / Lh, v[0] / Lh];
  const at = (t, o) => [PV[0] + v[0] * t + n[0] * o, PV[1] + v[1] * t + n[1] * o];
  let s = poly([at(-.04, -w1), at(.9, -w2 - 3), at(1.04, -w2), at(1.04, w2), at(.9, w2 + 3), at(-.04, w1)]);
  s += poly([at(.12, -w1 + 7), at(.8, -w2 + 2), at(.8, w2 - 2), at(.12, w1 - 7)], 'l t');
  return { s, at };
}

function adjuster(at) {
  let s = poly([at(.86, -8), at(1.02, -8), at(1.02, 8), at(.86, 8)]);
  for (let i = 0; i < 5; i++) s += ln(at(.88 + i * .025, -8), at(.88 + i * .025, -3), 'l t');
  return s + ln(at(.78, 0), at(.86, 0), 'l') + hex(at(.8, 0), 3.2);
}

/** Front end in steering coordinates: tubes, clamps, axle. Returns pieces so each plate can place them in zones. */
function frontEnd(F, rake, len, usd = false) {
  const L = steer(F, rake);
  const lp = (ps, cls = 'sf') => poly(ps.map(p => L(p[0], p[1])), cls);
  const top = -len;
  const fork = usd
    ? lp([[-13, top + 18], [13, top + 18], [13, -150], [-13, -150]]) + lp([[-9, -150], [9, -150], [9, 8], [-9, 8]]) + lp([[-15, -158], [15, -158], [15, -146], [-15, -146]]) + ln(L(-4, top + 30), L(-4, -160), 'l t')
    : lp([[-9, top + 18], [9, top + 18], [9, -165], [-9, -165]]) + ln(L(-4, top + 26), L(-4, -172), 'l t') + lp([[-14, -172], [14, -172], [16, 0], [10, 16], [-10, 16], [-16, 0]]) + lp([[-16, -178], [16, -178], [16, -166], [-16, -166]]);
  const clamps = lp([[-40, top + 64], [-24, top + 64], [-24, top + 4], [-40, top + 4]]) + lp([[-46, top + 52], [20, top + 52], [20, top + 70], [-46, top + 70]]) + lp([[-44, top - 10], [18, top - 10], [18, top + 6], [-44, top + 6]]);
  const cap = circ(L(0, top), 5, 'sf');
  return { L, lp, fork, clamps, cap, head: [L(-32, top + 4), L(-32, top + 70)], clamp: L(-14, top - 12) };
}

const hugger = (c, r, a1, a2) => path(arcBand(c, r, r + 6, a1, a2), 'sf');

// ---------------- Sport ----------------
export function sport() {
  const R = [300, 502], F = [915, 505], PV = [532, 432], CS = [562, 444];
  const fe = frontEnd(F, 23.5, 360, true);
  const { L } = fe;
  const sa = arm(PV, R, 22, 10);
  let s = '';
  s += Z('rear_wheel', castWheel(R, 138, 97, 3));
  s += Z('rear_brake', disc(R, 50, true) + poly([pol(R, 46, 118), pol(R, 62, 112), pol(R, 68, 140), pol(R, 54, 146)], 'l h'));
  s += hugger(R, 146, -150, -60);
  s += sa.s;
  s += Z('drivetrain', chain(R, 46, CS, 17) + adjuster(sa.at));
  s += Z('rear_shock', shock(lerp(PV, R, .12).map((v, i) => v + (i ? -22 : 0)), [520, 318], 7, 11));
  // Twin-spar aluminium beam, mostly under the fairing
  s += poly([fe.head[0], [690, 238], [585, 300], [560, 420], [520, 446], [500, 400], [530, 300], [640, 238], fe.head[1]], 'sf');
  s += path('M 660 252 L 580 318', 'l t');
  s += circ(PV, 10, 'sf') + hex(PV, 6);
  // Front wheel, brakes and fork go first: the fairing covers the upper fork
  s += Z('front_wheel', castWheel(F, 135, 97, 3));
  s += hugger(F, 146, -160, -40);
  s += Z('front_brake', disc(F, 72, false) + fe.lp([[-34, -30], [-14, -42], [-6, -6], [-26, 6]]) + circ(L(-8, -36), 3, 'sf') + circ(L(-8, -4), 3, 'sf') + path(`M ${pt(L(-24, -40))} C ${pt(L(-30, -120))} ${pt(L(-30, -220))} ${pt(L(-26, -300))}`, 'l'));
  s += Z('fork', fe.fork + fe.cap);
  s += circ(F, 8, 'sf') + hex(F, 5);
  // Fairing, then the mechanics it hides as a cut-away
  s += path('M 760 214 C 850 196 955 214 1004 266 L 990 300 C 955 310 912 318 892 336 L 872 420 C 860 474 824 524 762 548 L 606 552 C 580 545 568 524 574 500 L 620 470 C 650 420 690 360 720 318 Z', 'sf');
  s += path('M 840 300 C 870 330 868 380 850 420', 'l t') + path('M 610 520 L 760 528', 'l t') + path('M 700 330 C 740 330 780 350 800 380', 'l t');
  s += Z('engine', poly([[598, 352], [742, 344], [770, 430], [742, 498], [612, 504], [586, 470]], 'sf h') + poly([[640, 350], [736, 344], [776, 268], [682, 272]], 'sf h') + circ([660, 440], 34, 'l h') + poly([[742, 476], [768, 470], [774, 500], [748, 506]], 'l h') + ln([700, 268], [700, 252], 'l h') + ln([726, 268], [728, 252], 'l h'));
  s += Z('cooling', poly([[790, 262], [834, 270], [818, 430], [776, 422]], 'sf h') + [...Array(12)].map((_, i) => ln(lerp([794, 272], [780, 412], i / 11), lerp([828, 280], [814, 420], i / 11), 'l h t')).join(''));
  s += Z('intake', path('M 620 246 L 700 236 C 712 256 712 276 700 290 L 630 298 C 618 282 616 262 620 246 Z', 'sf h'));
  s += Z('battery', poly([[452, 262], [500, 260], [502, 292], [454, 294]], 'sf h'));
  // Exhaust: under-belly collector into a short side can
  s += path('M 600 546 L 744 552 L 738 576 L 606 570 Z', 'sf') + ln([620, 556], [730, 562], 'l t') + pipe('M 606 562 L 584 566', 9);
  // Tank, seat, tail
  s += path('M 588 254 C 600 214 650 188 708 182 C 742 180 770 188 786 204 L 760 232 C 700 238 640 248 600 262 Z', 'sf') + path('M 640 214 C 680 204 720 204 750 210', 'l t');
  s += path('M 588 258 C 540 256 480 262 440 262 L 430 276 C 480 280 540 278 600 274 Z', 'sf');
  s += path('M 440 262 L 380 246 L 236 206 L 226 216 L 290 244 L 330 270 L 430 278 Z', 'sf') + path('M 360 250 C 340 244 320 238 300 232', 'l t');
  s += path('M 300 262 C 286 300 262 330 248 352', 'l') + poly([[226, 352], [270, 352], [270, 384], [226, 384]]);
  // Rearsets and clip-ons
  s += pipe('M 540 450 L 520 474', 7) + pipe('M 504 476 L 536 476', 7);
  s += fe.clamps + pipe(`M ${pt(fe.clamp)} L ${pt([fe.clamp[0] - 62, fe.clamp[1] + 22])}`, 7) + poly([[fe.clamp[0] - 64, fe.clamp[1] + 14], [fe.clamp[0] - 30, fe.clamp[1] + 6], [fe.clamp[0] - 28, fe.clamp[1] + 18], [fe.clamp[0] - 62, fe.clamp[1] + 28]]);
  // Windscreen
  s += path('M 786 206 C 820 160 870 140 912 150 L 940 214 C 900 206 850 204 806 214 Z', 'sf') + path('M 812 196 C 850 170 880 162 910 166', 'l t');
  s += Z('lights', poly([[948, 258], [996, 270], [990, 284], [944, 274]]) + ln([954, 266], [988, 276], 'l t') + poly([[222, 206], [238, 210], [234, 222], [220, 220]]) + poly([[978, 300], [996, 302], [994, 310], [976, 308]]));
  return { svg: s, viewBox: [150, 60, 940, 600] };
}

// ---------------- Trail / adventure ----------------
export function trail() {
  const R = [285, 492], F = [955, 476], PV = [520, 420], CS = [548, 432];
  const fe = frontEnd(F, 27, 385, false);
  const { L } = fe;
  const sa = arm(PV, R, 19, 9);
  let s = '';
  s += Z('rear_wheel', spokeWheel(R, 148, 104));
  s += Z('rear_brake', disc(R, 48, true) + poly([pol(R, 44, 120), pol(R, 60, 114), pol(R, 66, 142), pol(R, 52, 148)], 'l h'));
  s += sa.s;
  s += Z('drivetrain', chain(R, 50, CS, 17) + adjuster(sa.at));
  s += Z('rear_shock', shock(lerp(PV, R, .2), [498, 270], 9, 12));
  // Subframe, rack, tubular frame
  s += pipe('M 560 252 L 250 226', 9) + pipe('M 540 340 L 320 246', 8) + poly([[214, 218], [330, 222], [330, 232], [214, 230]]) + [236, 262, 288, 314].map(x => ln([x, 219], [x, 231], 'l t')).join('');
  s += poly([[510, 322], [560, 318], [572, 392], [550, 450], [500, 448], [488, 392]]) + circ(PV, 10, 'sf') + hex(PV, 6);
  s += Z('engine', `<g transform="translate(0,-14)">${poly([[560, 336], [712, 326], [744, 410], [718, 462], [592, 468], [562, 444]])}${poly([[630, 330], [716, 324], [736, 262], [650, 264]])}${poly([[646, 264], [742, 260], [754, 226], [656, 222]])}${path('M 654 222 L 756 224 C 762 208 754 200 742 200 L 670 198 C 658 198 652 208 654 222 Z', 'sf')}${circ([640, 398], 38, 'sf')}${circ([640, 398], 29, 'l t')}${[...Array(10)].map((_, i) => circ(pol([640, 398], 34, i * 36), 1.8, 'l')).join('')}${poly([[718, 428], [742, 422], [750, 454], [726, 460]])}${poly([[690, 198], [702, 198], [700, 182], [692, 182]])}</g>`);
  // Skid plate and crash bars
  s += path('M 572 470 L 726 462 L 770 420 L 776 432 L 734 484 L 584 492 Z', 'sf') + [600, 640, 680, 720].map(x => circ([x, 478], 2.4, 'l t')).join('');
  s += pipe('M 760 250 C 800 300 800 400 760 440', 7) + pipe('M 760 330 L 700 340', 6);
  s += Z('cooling', poly([[766, 256], [790, 260], [782, 380], [758, 376]]) + [...Array(11)].map((_, i) => ln(lerp([768, 264], [760, 368], i / 10), lerp([786, 268], [778, 372], i / 10), 'l t')).join(''));
  s += Z('intake', path('M 566 262 L 646 254 C 654 272 656 288 650 298 L 576 304 C 566 290 562 276 566 262 Z', 'sf'));
  s += Z('battery', poly([[420, 246], [470, 244], [472, 280], [422, 282]], 'sf h'));
  // High exhaust
  s += pipe('M 700 466 C 650 496 560 480 500 430 L 430 370', 10) + path('M 440 350 L 330 300 L 320 326 L 428 378 Z', 'sf') + ln([350, 306], [342, 326], 'l t');
  // Tank, seat, tail
  s += path('M 560 250 C 568 196 640 160 722 160 C 760 160 784 172 790 190 L 784 236 C 740 248 660 256 600 262 L 560 268 Z', 'sf') + path('M 620 190 C 680 176 740 176 780 190', 'l t') + path('M 760 196 L 792 236', 'l t');
  s += path('M 562 252 C 520 246 440 240 380 238 L 300 230 L 296 246 C 380 256 480 264 566 268 Z', 'sf');
  s += pipe('M 520 440 L 508 466', 7) + pipe('M 490 468 L 526 468', 7) + pipe('M 548 448 L 600 462', 5);
  // Cockpit: windscreen, bars with handguards, mirrors
  s += fe.clamps;
  const c = fe.clamp, g = [c[0] - 92, c[1] - 24];
  s += pipe(`M ${pt(c)} L ${pt([c[0] - 6, c[1] - 26])} C ${pt([c[0] - 30, c[1] - 34])} ${pt([c[0] - 70, c[1] - 28])} ${pt(g)}`, 7) + path(`M ${pt([g[0] - 6, g[1] - 14])} C ${pt([g[0] + 20, g[1] - 26])} ${pt([g[0] + 44, g[1] - 14])} ${pt([g[0] + 40, g[1] + 6])} L ${pt([g[0] + 2, g[1] + 8])} Z`, 'sf');
  s += path(`M ${pt(L(30, -352))} C ${pt(L(50, -392))} ${pt(L(44, -424))} ${pt(L(24, -436))} L ${pt(L(8, -392))} Z`, 'sf');
  // Front: beak, wheel, fender, brakes, fork, lights
  s += Z('front_wheel', spokeWheel(F, 165, 120));
  s += hugger(F, 174, -150, -50);
  s += Z('front_brake', disc(F, 68, false) + fe.lp([[-36, -26], [-16, -38], [-8, -2], [-28, 10]]) + circ(L(-10, -32), 3, 'sf') + circ(L(-10, 0), 3, 'sf') + path(`M ${pt(L(-26, -36))} C ${pt(L(-32, -140))} ${pt(L(-30, -260))} ${pt(L(-24, -360))}`, 'l'));
  s += Z('fork', fe.fork + fe.cap);
  s += circ(F, 8, 'sf') + hex(F, 5);
  s += Z('lights', fe.lp([[18, -350], [58, -346], [56, -300], [18, -304]]) + fe.lp([[22, -344], [52, -340], [50, -324], [22, -328]], 'l t') + poly([[204, 226], [218, 222], [216, 236], [204, 238]]));
  return { svg: s, viewBox: [150, 40, 960, 620] };
}

// ---------------- Custom / cruiser ----------------
export function custom() {
  const R = [275, 520], F = [1000, 512], PV = [470, 470];
  const fe = frontEnd(F, 32, 400, false);
  const { L } = fe;
  let s = '';
  s += Z('rear_wheel', castWheel(R, 120, 82, 9));
  s += Z('rear_brake', disc(R, 44, true) + poly([pol(R, 40, 118), pol(R, 54, 112), pol(R, 60, 140), pol(R, 48, 146)], 'l h'));
  s += path(arcBand(R, 126, 134, -170, -80), 'sf') + poly([[200, 428], [214, 424], [216, 440], [202, 442]]);
  const sa = arm(PV, R, 14, 9);
  s += sa.s;
  // Belt final drive: big pulley at the wheel, small one at the gearbox
  const P = [494, 474];
  const belt = loopPath(R, 62, P, 22);
  s += Z('drivetrain', circ(R, 60, 'sf') + circ(R, 48, 'l t') + [...Array(10)].map((_, i) => circ(pol(R, 36, i * 36), 6, 'l')).join('') + circ(P, 20, 'sf') + circ(P, 10, 'l t') + pipe(belt, 7) + path(belt, 'l t'));
  // Shocks hidden under the seat (softail)
  s += Z('rear_shock', shock([470, 420], [330, 400], 7, 9).replace(/class="(l|sf|po)/g, 'class="$1 h').replace(/class="pi/g, 'class="pi h'));
  // Cradle frame
  s += pipe(`M ${pt(fe.head[0])} L 640 290 L 470 330 L 330 380`, 12) + pipe(`M ${pt(fe.head[1])} L 820 420 L 790 540 L 560 548 L 470 500`, 11) + pipe('M 470 330 L 470 470', 10) + circ(PV, 9, 'sf') + hex(PV, 5);
  // 45° V-twin with fins
  const finned = (base, ang, len, w) => {
    const v = [Math.cos(rad(ang)), Math.sin(rad(ang))], n = [-v[1], v[0]];
    const at = (t, o) => [base[0] + v[0] * t + n[0] * o, base[1] + v[1] * t + n[1] * o];
    let g = poly([at(0, -w), at(len, -w), at(len, w), at(0, w)]);
    for (let t = 8; t < len - 4; t += 7) g += ln(at(t, -w - 5), at(t, w + 5), 'l t');
    g += poly([at(len, -w - 6), at(len + 26, -w - 4), at(len + 26, w + 4), at(len, w + 6)]) + circ(at(len + 13, w + 10), 4, 'sf');
    return g;
  };
  s += Z('engine', path('M 560 470 C 560 430 600 404 660 404 C 720 404 770 430 770 470 L 760 520 L 580 524 Z', 'sf') + finned([630, 412], -112, 92, 24) + finned([700, 412], -68, 92, 24) + circ([600, 476], 30, 'sf') + circ([600, 476], 20, 'l t') + circ([726, 500], 9, 'l'));
  s += Z('intake', circ([666, 330], 30, 'sf') + circ([666, 330], 22, 'l t') + circ([666, 330], 4, 'l'));
  s += Z('cooling', poly([[836, 434], [880, 430], [882, 470], [838, 474]]) + [0, 1, 2, 3, 4].map(i => ln([840, 438 + i * 8], [878, 434 + i * 8], 'l t')).join(''));
  s += Z('battery', poly([[420, 396], [470, 394], [472, 446], [422, 448]]) + circ([446, 420], 8, 'l t'));
  // Shotgun pipes
  s += pipe('M 640 520 C 600 560 520 566 300 560', 10) + pipe('M 714 512 C 690 548 600 548 290 540', 10) + poly([[290, 532], [250, 536], [250, 546], [290, 548]], 'sf') + poly([[300, 552], [256, 556], [256, 566], [300, 568]], 'sf');
  // Teardrop tank, solo seat, forward controls
  s += path('M 620 300 C 640 256 720 236 790 248 C 820 254 836 270 834 290 C 800 304 700 312 640 316 Z', 'sf') + path('M 680 272 C 720 262 770 262 810 272', 'l t') + circ([728, 248], 7, 'sf');
  s += path('M 470 350 C 470 320 520 306 600 310 L 628 312 L 620 334 C 560 338 510 348 476 362 Z', 'sf') + pipe('M 510 360 L 480 330', 5);
  s += pipe('M 800 540 L 860 520', 7) + pipe('M 846 512 L 882 512', 7);
  // Wide bars, mirror, round light
  s += fe.clamps;
  const c = fe.clamp, g = [c[0] - 140, c[1] - 26];
  s += pipe(`M ${pt(c)} L ${pt([c[0] - 4, c[1] - 30])} C ${pt([c[0] - 60, c[1] - 50])} ${pt([c[0] - 120, c[1] - 30])} ${pt(g)}`, 7) + poly([[g[0] - 4, g[1] - 6], [g[0] + 32, g[1] - 10], [g[0] + 34, g[1] + 2], [g[0] - 2, g[1] + 6]]);
  s += Z('front_wheel', castWheel(F, 128, 98, 9));
  s += Z('front_brake', disc(F, 64, false) + fe.lp([[-34, -26], [-14, -38], [-6, -2], [-26, 10]]) + circ(L(-8, -32), 3, 'sf') + path(`M ${pt(L(-24, -34))} C ${pt(L(-30, -140))} ${pt(L(-30, -260))} ${pt(L(-24, -330))}`, 'l'));
  s += Z('fork', fe.fork + fe.cap);
  s += circ(F, 8, 'sf') + hex(F, 5);
  s += Z('lights', circ(L(44, -340), 32, 'sf') + circ(L(44, -340), 24, 'l t') + circ(L(44, -340), 10, 'l') + ln(L(12, -340), L(24, -340), 'l') + poly([[198, 444], [212, 440], [212, 452], [198, 454]]));
  return { svg: s, viewBox: [150, 50, 960, 610] };
}

// ---------------- Scooter ----------------
export function scooter() {
  const R = [340, 560], F = [880, 560];
  const fe = frontEnd(F, 27, 300, false);
  const { L } = fe;
  let s = '';
  s += Z('rear_wheel', castWheel(R, 80, 58, 6));
  s += Z('rear_brake', drum(R, 34));
  // CVT unit / swing arm: the variator housing is the transmission zone
  s += Z('drivetrain', path('M 330 524 C 360 500 520 496 568 516 C 590 530 588 566 560 580 C 500 596 380 598 330 594 C 300 590 296 540 330 524 Z', 'sf') + circ([520, 548], 26, 'l') + circ([520, 548], 18, 'l t') + [...Array(8)].map((_, i) => ln(pol([520, 548], 6, i * 45), pol([520, 548], 16, i * 45), 'l t')).join('') + [360, 400, 440, 480].map(x => ln([x, 512], [x + 10, 588], 'l t')).join('') + [0, 1, 2, 3, 4, 5].map(i => circ([350 + i * 40, i % 2 ? 586 : 514], 2.4, 'l')).join(''));
  s += Z('engine', poly([[566, 500], [640, 494], [660, 540], [610, 566], [570, 556]], 'sf h') + poly([[600, 496], [650, 492], [664, 456], [612, 458]], 'sf h') + ln([630, 456], [632, 440], 'l h'));
  s += Z('intake', path('M 400 470 L 520 464 C 530 476 530 490 520 498 L 404 504 C 394 494 392 480 400 470 Z', 'sf'));
  s += Z('rear_shock', shock([360, 524], [380, 420], 6, 10));
  // Body: rear cowl, seat, floorboard, leg shield
  s += path('M 252 380 C 260 350 330 336 420 340 L 600 352 C 640 360 660 400 660 440 L 640 500 L 560 506 C 520 470 460 450 380 456 C 320 462 280 470 262 460 C 246 440 244 410 252 380 Z', 'sf') + path('M 300 400 C 380 390 480 392 600 404', 'l t') + path('M 330 430 L 560 432', 'l t');
  s += path('M 300 344 C 320 318 420 312 520 318 L 610 326 C 626 330 630 346 618 352 L 420 352 C 360 352 316 352 300 344 Z', 'sf') + path('M 470 318 C 480 326 482 340 478 350', 'l t');
  s += path('M 640 500 L 760 500 L 770 520 L 640 524 Z', 'sf') + [664, 688, 712, 736].map(x => ln([x, 504], [x, 518], 'l t')).join('');
  s += Z('front_wheel', castWheel(F, 80, 58, 6));
  s += hugger(F, 88, -170, -20);
  s += Z('front_brake', disc(F, 44, false) + fe.lp([[-30, -20], [-12, -30], [-6, -2], [-24, 8]]) + circ(L(-8, -26), 2.6, 'sf'));
  s += Z('fork', fe.fork + fe.cap);
  s += circ(F, 7, 'sf') + hex(F, 4.5);
  s += path('M 744 522 C 752 430 772 350 804 296 L 872 290 C 884 356 880 420 856 466 C 834 496 806 514 792 524 Z', 'sf') + path('M 764 480 C 772 410 790 350 812 312', 'l t') + path('M 846 300 C 856 360 852 420 836 456', 'l t');
  s += Z('cooling', poly([[778, 420], [804, 424], [798, 470], [772, 466]]) + [0, 1, 2, 3].map(i => ln([778, 430 + i * 10], [800, 434 + i * 10], 'l t')).join(''));
  s += Z('battery', poly([[672, 470], [716, 468], [718, 494], [674, 496]], 'sf h'));
  s += pipe('M 640 560 C 610 600 520 612 460 604 L 420 600', 9) + path('M 440 586 L 330 590 L 334 616 L 444 612 Z', 'sf');
  // Steering: headset cover, bars, mirrors
  s += fe.clamps;
  const c = fe.clamp;
  s += path(`M ${pt([c[0] - 60, c[1] - 6])} L ${pt([c[0] + 30, c[1] - 14])} L ${pt([c[0] + 34, c[1] + 10])} L ${pt([c[0] - 58, c[1] + 16])} Z`, 'sf') + pipe(`M ${pt([c[0] - 50, c[1] - 4])} L ${pt([c[0] - 70, c[1] - 60])}`, 4) + `<ellipse class="sf" cx="${f(c[0] - 74)}" cy="${f(c[1] - 68)}" rx="16" ry="8"/>`;
  s += Z('lights', path(`M 840 306 L 874 302 L 878 336 L 846 340 Z`, 'sf') + ln([846, 314], [872, 310], 'l t') + poly([[244, 380], [258, 376], [260, 392], [246, 396]]));
  return { svg: s, viewBox: [180, 100, 900, 560] };
}

export const PLATES = { sport, trail, custom, scooter };
