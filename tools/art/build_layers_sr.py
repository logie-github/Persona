#!/usr/bin/env python3
"""Cuts the high-resolution reference (the girl, the moon, the disc) into the layers the launcher animates, keeping the picture's own pixels.

    python3 -I tools/art/build_layers_sr.py reference_8000.jpg out_dir

The shapes are found by colour on a 1977x1398 copy (the art's own units), each part is cut from the full-size picture at 2x with a
smooth edge, what a part hides beneath the parts above it is painted in so it can move, and every part is written as a cropped PNG.
layers.json holds where each one sits (in art units) and the geometry of the rest. Needs opencv-python-headless and numpy.
"""
import json, os, sys
import cv2
import numpy as np

AW, AH, K = 1977, 1398, 2          # art units, and the pixels per unit of the layers
PAL = np.array([[0xfd,0xf8,0x9d],[0x85,0x48,0xa3],[0xc2,0xa9,0xda],[0x48,0x1d,0x6e],[0xe7,0xcf,0xea],[0x77,0x0c,0xa4],[0xd8,0xb0,0xfa],
                [0x20,0x1b,0x1e],[0x97,0x41,0xd6],[0xf2,0xc3,0x1a],[0xaa,0x83,0xc8],[0xde,0x13,0x95],[0x2a,0x10,0x41],[0xfc,0xfa,0xfc]], np.uint8)
YEL, MAUVE, GREY, HAIR, PALE, DEEP, LAV, BLACK, VIOLET, GOLD, SHARD, PINK, HAIR2, WHITE = range(14)
DISC = (177.7, 47.2, 1322.3)
RIM = 85

def quantise(img):
    img = cv2.medianBlur(cv2.bilateralFilter(img, 7, 30, 7), 5)
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB).reshape(-1, 3).astype(np.float32)
    pl = cv2.cvtColor(PAL[:, ::-1].reshape(1, -1, 3), cv2.COLOR_BGR2LAB).reshape(-1, 3).astype(np.float32)
    return ((lab[:, None, :] - pl[None]) ** 2).sum(-1).argmin(1).reshape(img.shape[:2]).astype(np.uint8)

def poly(h, w, pts):
    m = np.zeros((h, w), np.uint8); cv2.fillPoly(m, [np.array(pts, np.int32)], 1); return m.astype(bool)

def comps(mask, minarea):
    n, lab, st, cen = cv2.connectedComponentsWithStats(mask.astype(np.uint8), connectivity=8)
    return [(i, st[i], cen[i]) for i in range(1, n) if st[i, 4] >= minarea], lab

def nearest_owner(owner_masks, region):
    """Gives every pixel of `region` not in any mask to the nearest mask (by name)."""
    union = np.zeros_like(region)
    for m in owner_masks.values(): union |= m
    lab = np.zeros(region.shape, np.uint8)
    for k, m in enumerate(owner_masks.values(), 1): lab[m] = k
    _, near = cv2.distanceTransformWithLabels((~union).astype(np.uint8), cv2.DIST_L2, 5, labelType=cv2.DIST_LABEL_PIXEL)
    ys, xs = np.nonzero(union)
    lut = np.zeros(near.max() + 1, np.uint8); lut[near[ys, xs]] = lab[ys, xs]
    given = np.where(region & ~union, lut[near], 0)
    for k, name in enumerate(owner_masks, 1): owner_masks[name] = owner_masks[name] | (given == k)

CROPS = {}

def write_layer(out, name, mask1, pixels2, covered1):
    """mask1/covered1 at art resolution; pixels2 is the 2x picture. Writes name.png cropped."""
    m2 = cv2.resize(mask1.astype(np.float32), (AW * K, AH * K), interpolation=cv2.INTER_CUBIC)
    m2 = cv2.GaussianBlur(m2, (0, 0), 1.1)
    cov2 = cv2.resize((covered1 & ~mask1).astype(np.uint8) * 255, (AW * K, AH * K), interpolation=cv2.INTER_NEAREST) > 0
    own = (m2 > 0.5)
    # beneath the parts above: the layer's own colours carried inwards
    img = pixels2.copy(); img[~own] = 0
    hide = (cov2 & ~own).astype(np.uint8) * 255
    hide = cv2.dilate(hide, np.ones((5, 5), np.uint8))
    # a small copy of the part's own colours (averaged over its own pixels only) is spread into what is hidden, then scaled back up
    sm = 4; sw, sh = img.shape[1] // sm, img.shape[0] // sm
    wgt = own.astype(np.float32)
    num = cv2.resize(pixels2.astype(np.float32) * wgt[..., None], (sw, sh), interpolation=cv2.INTER_AREA)
    den = cv2.resize(wgt, (sw, sh), interpolation=cv2.INTER_AREA)
    img_s = (num / np.maximum(den, 1e-3)[..., None]).astype(np.uint8)
    unk = (den < 0.6).astype(np.uint8) * 255
    fill_s = cv2.inpaint(img_s, unk, 5, cv2.INPAINT_TELEA) if hide.any() else img_s
    filled = np.where(own[..., None], pixels2, cv2.resize(fill_s, (img.shape[1], img.shape[0]), interpolation=cv2.INTER_CUBIC))
    alpha = np.clip((m2 - 0.08) / 0.55, 0, 1)
    full = np.maximum(alpha, (hide > 0).astype(np.float32))
    full = cv2.GaussianBlur(full, (0, 0), 0.7)
    ys, xs = np.nonzero(full > 0.02)
    x0, x1, y0, y1 = max(xs.min() - 6, 0), xs.max() + 7, max(ys.min() - 6, 0), ys.max() + 7
    x0 -= x0 % K; y0 -= y0 % K; x1 += (-x1) % K; y1 += (-y1) % K
    core = cv2.erode(own.astype(np.uint8), np.ones((3, 3), np.uint8), iterations=2) > 0      # the part without its edge pixels (which carry the old background's colour)
    far = cv2.distanceTransform((~core).astype(np.uint8), cv2.DIST_L2, 3) > 3.5                 # slivers too thin to have an inside keep their own colours
    core = core | (own & far)
    base = np.where(core[..., None], pixels2, filled)[y0:y1, x0:x1]
    known = (core | (hide > 0))[y0:y1, x0:x1]
    # every pixel that is not the part's own (or its hidden fill) takes the colour of the nearest one that is, so that nothing dark or foreign
    # lies under the soft edge and smooth texture filtering shows no outline
    _, near = cv2.distanceTransformWithLabels((~known).astype(np.uint8), cv2.DIST_L2, 5, labelType=cv2.DIST_LABEL_PIXEL)
    kys, kxs = np.nonzero(known)
    lut = np.zeros((near.max() + 1, 3), np.uint8); lut[near[kys, kxs]] = base[kys, kxs]
    rgb = np.where(known[..., None], base, lut[near])
    rgba = np.dstack([rgb, (full[y0:y1, x0:x1] * 255).astype(np.uint8)])
    CROPS[name] = dict(x=int(x0 // K), y=int(y0 // K), w=int((x1 - x0) // K), h=int((y1 - y0) // K))
    cv2.imwrite(os.path.join(out, name + '.png'), rgba)

def main(src, out):
    os.makedirs(out, exist_ok=True)
    big = cv2.imread(src)
    img2 = cv2.resize(big, (AW * K, AH * K), interpolation=cv2.INTER_AREA)
    img1 = cv2.resize(big, (AW, AH), interpolation=cv2.INTER_AREA)
    idx = quantise(img1); h, w = idx.shape
    yy, xx = np.mgrid[0:h, 0:w]
    inside = np.hypot(xx - DISC[0], yy - DISC[1]) < DISC[2] - 3

    # the signature and the small figure are not hers: painted out from what is around them (at both sizes)
    sig = poly(h, w, [(0, 1150), (640, 1150), (640, h), (0, h)]) & np.isin(idx, [BLACK, WHITE, GOLD, HAIR2])
    kill = cv2.dilate((sig | poly(h, w, [(652, 1300), (652, 1228), (690, 1192), (745, 1160), (800, 1148), (905, 1148), (905, h), (640, h)])).astype(np.uint8), np.ones((21, 21), np.uint8))
    # what was under them is spread in from round about (on a quarter-size copy, which keeps it smooth), the tie's lower end redrawn
    sm = 4
    small = cv2.resize(img2, (AW * K // sm, AH * K // sm), interpolation=cv2.INTER_AREA)
    unk = cv2.dilate(cv2.resize(kill.astype(np.uint8), (AW * K // sm, AH * K // sm), interpolation=cv2.INTER_NEAREST), np.ones((9, 9), np.uint8)) * 255
    spread = cv2.resize(cv2.inpaint(small, unk, 7, cv2.INPAINT_TELEA), (AW * K, AH * K), interpolation=cv2.INTER_CUBIC)
    k2 = cv2.GaussianBlur(cv2.resize(cv2.dilate(kill.astype(np.uint8), np.ones((7, 7), np.uint8)).astype(np.float32), (AW * K, AH * K), interpolation=cv2.INTER_LINEAR), (0, 0), 3.0)[..., None]
    img2 = (img2 * (1 - k2) + spread * k2).astype(np.uint8)
    img1 = cv2.resize(img2, (AW, AH), interpolation=cv2.INTER_AREA); idx = quantise(img1)

    left = (xx < 1160) & inside
    dark_all = np.isin(idx, [HAIR, HAIR2]) & left
    dcs, _ = comps(dark_all, 3000)
    _, lm = cv2.connectedComponents(dark_all.astype(np.uint8), connectivity=8)
    hair_all = lm == max(dcs, key=lambda c: c[1][4])[0]
    tie_poly = poly(h, w, [(495, 790), (610, 790), (640, 900), (650, 990), (700, 1060), (745, 1150), (745, 1300), (560, 1300), (545, 1150), (520, 1000), (500, 900)])
    tie = tie_poly & (hair_all | ((yy > 1000) & np.isin(idx, [HAIR, HAIR2, DEEP, WHITE, PALE])) | (yy > 1000))
    hair = hair_all & ~tie
    hair_all_early = hair_all
    # the ribbon is every pink pixel (it has a lighter and a darker pink; by colour rather than by the palette class)
    b_, g_, r_ = [img1[..., i].astype(int) for i in range(3)]
    ribbon = ((r_ - g_ > 80) & (r_ > b_ + 30) & (r_ > 150)) & left
    ribbon = cv2.morphologyEx(ribbon.astype(np.uint8), cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8)) > 0
    # the long tail on the left has a darker outline: it belongs to the tail (or it would be left behind when the tail swings)
    outline = np.isin(idx, [DEEP, MAUVE, VIOLET, BLACK]) & ~hair_all_early
    ribbon |= (cv2.dilate(ribbon.astype(np.uint8), np.ones((15, 15), np.uint8)) > 0) & outline & (xx < 330) & (yy > 480)
    ncomp, rl, rs, _ = cv2.connectedComponentsWithStats(ribbon.astype(np.uint8), connectivity=8)
    ribbon = np.isin(rl, [i for i in range(1, ncomp) if rs[i, 4] >= 150])                       # no loose specks
    zone = poly(h, w, [(190, 380), (720, 380), (720, 960), (650, 1010), (224, 1015), (190, 700)])
    light = np.isin(idx, [YEL, GREY, LAV, PALE, SHARD, WHITE]) & zone & ~hair_all
    light = cv2.morphologyEx(light.astype(np.uint8), cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    _, fl = cv2.connectedComponents(light, connectivity=4)
    face = fl == fl[620, 450]
    purples = np.isin(idx, [VIOLET, MAUVE, DEEP, GOLD, BLACK]) | tie | (np.isin(idx, [WHITE, PALE]) & ~face & (yy > 900) & left)
    body = purples & inside & (xx < 1320) & ~face & ~hair & ~ribbon
    body |= tie
    nb, lb, sb, _ = cv2.connectedComponentsWithStats(cv2.morphologyEx(body.astype(np.uint8), cv2.MORPH_OPEN, np.ones((5, 5), np.uint8)), connectivity=8)
    body = lb == (1 + int(np.argmax(sb[1:, 4])))
    pend_c, pend_r = (572, 1050), 54
    pendant = ((xx - pend_c[0]) ** 2 + (yy - pend_c[1]) ** 2 < pend_r ** 2)
    body_full = body | pendant

    # the hair in three parts, as a puppet has it: the crown, the fringe and its frame, and the two curtains below
    hull = cv2.convexHull(np.argwhere(face & (yy < 800))[:, ::-1].astype(np.int32))
    hullm = np.zeros((h, w), np.uint8); cv2.fillConvexPoly(hullm, hull, 1)
    frame = cv2.dilate(hullm, np.ones((101, 101), np.uint8)) > 0
    bangs = hair & frame & (yy < 830)
    rest = hair & ~bangs
    curtainL = rest & (yy > 760) & (xx < 450)
    curtainR = rest & (yy > 700) & (xx >= 450)
    crown = rest & ~curtainL & ~curtainR
    parts = {'crown': crown, 'curtainL': curtainL, 'curtainR': curtainR, 'bangs': bangs}

    # parts of the same hair overlap a little where they meet, so no straight seam can show between them
    grow = lambda m, n: cv2.dilate(m.astype(np.uint8), np.ones((n, n), np.uint8)) > 0
    parts['curtainL'] = parts['curtainL'] | (parts['crown'] & grow(parts['curtainL'], 61))
    parts['curtainR'] = parts['curtainR'] | (parts['crown'] & grow(parts['curtainR'], 61))
    parts['bangs'] = parts['bangs'] | (parts['crown'] & grow(parts['bangs'], 41))
    # mid-tone edge pixels go to the nearest part, inside her outline
    owners = {'body': body_full, 'face': face, 'ribbon': ribbon, **parts}
    union = np.zeros((h, w), bool)
    for m in owners.values(): union |= m
    sil = cv2.morphologyEx(union.astype(np.uint8), cv2.MORPH_CLOSE, np.ones((11, 11), np.uint8)) > 0
    nearest_owner(owners, sil)
    body_full, face, ribbon = owners['body'], owners['face'], owners['ribbon']
    for k in parts: parts[k] = owners[k]
    body = body_full & ~pendant

    # only what seams inside her outline could show through: never wider than the outline itself
    union_all = (face | ribbon | parts['crown'] | parts['bangs'] | parts['curtainL'] | parts['curtainR'])
    head = (cv2.morphologyEx(union_all.astype(np.uint8), cv2.MORPH_CLOSE, np.ones((7, 7), np.uint8)) > 0) & (cv2.dilate(union_all.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0)
    head = cv2.erode(head.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0
    head &= cv2.dilate(face.astype(np.uint8), np.ones((41, 41), np.uint8)) > 0
    head_px = np.zeros_like(img2); head_px[:] = PAL[HAIR][::-1]
    above_all = parts['crown'] | parts['bangs'] | parts['curtainL'] | parts['curtainR'] | face | ribbon
    # the jacket carries on under the hair only where there is a jacket: not out past her left shoulder
    write_layer(out, 'body', body, img2, (above_all | pendant) & (yy > 560) & (xx < 1160) & inside & ((yy > 900) | (xx > 470)))
    write_layer(out, 'pendant', pendant, img2, np.zeros_like(pendant))
    write_layer(out, 'underhead', head, head_px, np.zeros_like(head))
    hair_mask = parts['crown'] | parts['bangs'] | parts['curtainL'] | parts['curtainR']
    write_layer(out, 'face', face, img2, (hair_mask | ribbon) & (cv2.dilate(face.astype(np.uint8), np.ones((31, 31), np.uint8)) > 0))
    # the ribbon runs on under the hair only along the band over her head and into the loop on the right, never down the tail
    write_layer(out, 'ribbon', ribbon, img2, hair_mask & (cv2.dilate(ribbon.astype(np.uint8), np.ones((41, 41), np.uint8)) > 0) & (yy < 640))
    write_layer(out, 'hair', hair_mask, img2, np.zeros_like(pendant))

    # the moon and the squares, as before
    yellow = (idx == YEL) & inside
    ycs, ylab = comps(yellow, 20000)
    ymask = np.zeros((h, w), np.uint8)
    for i, st, cen in ycs: ymask[ylab == i] = 1
    cnts, _ = cv2.findContours(ymask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    pts = np.vstack([c.reshape(-1, 2) for c in cnts]).astype(np.float64)
    gk = cv2.dilate((body_full | face | ribbon | hair_all).astype(np.uint8), np.ones((25, 25), np.uint8))
    P = np.array([(x, y) for x, y in pts if gk[int(y), int(x)] == 0 and 3 < y < h - 3])
    A = np.c_[2 * P[:, 0], 2 * P[:, 1], np.ones(len(P))]; b = (P ** 2).sum(1)
    mx, my, c = np.linalg.lstsq(A, b, rcond=None)[0]; mr = float(np.sqrt(c + mx * mx + my * my))
    ring = (idx == PALE) & inside
    rcs, rlab = comps(ring, 30000)
    rm = np.zeros((h, w), np.uint8)
    for i, st, cen in rcs: rm[rlab == i] = 1
    d = np.hypot(xx - mx, yy - my); rr = d[(rm > 0) & (gk == 0)]
    ring_r = float(np.percentile(rr, 98)) if len(rr) else mr * 1.12
    sq, slab = comps((idx == SHARD) & inside & (xx < 1480), 700)
    shards = []
    for i, st, cen in sq:
        if st[4] > 25000: continue
        cnt, _ = cv2.findContours((slab == i).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        (cx, cy), (rw, rh), ang = cv2.minAreaRect(max(cnt, key=cv2.contourArea))
        shards.append(dict(x=float(cx / w), y=float(cy / h), w=float(rw / h), h=float(rh / h), a=float(ang)))
    info = dict(w=w, h=h, px=K, layers=CROPS, disc=dict(x=DISC[0], y=DISC[1], r=DISC[2], rim=RIM), moon=dict(x=float(mx), y=float(my), r=float(mr), ring=float(ring_r)), shards=shards,
                pendant=dict(x=pend_c[0], y=pend_c[1], r=pend_r), neck=dict(x=430, y=860))
    with open(os.path.join(out, 'layers.json'), 'w') as f: json.dump(info, f, indent=1)
    print('layers', list(CROPS), 'moon', round(mx), round(my), round(mr), 'shards', len(shards))

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
