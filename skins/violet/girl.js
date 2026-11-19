// The girl, live: each layer of her (the picture's own pixels, cut by tools/art/build_layers_sr.py) is a gridded mesh drawn by the GPU, skinned by the
// bone matrices baked from the Blender animation (tools/art/blender/p1_puppet.py --export-rig, 60 samples a second). Per frame the page only writes
// two small uniform arrays and draws six meshes, so it holds the screen's full frame rate (60 fps) and draws at the screen's own resolution.
const AW = 1977, AH = 1398;
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const smooth = (t) => { t = clamp(t); return t * t * (3 - 2 * t); };
const STEP = { hair: 9, ribbon: 7, face: 14, body: 22, pendant: 8, underhead: 14 };
const ORDER = ['body', 'pendant', 'underhead', 'face', 'ribbon', 'hair'];

function spread(chain, t, segs, a, out) {
  const raw = []; for (let k = 0; k < segs; k++) raw.push(Math.max(0, 1 - Math.abs(t * segs - (k + 0.5))));
  const s = raw.reduce((p, q) => p + q, 0) || 1; for (let k = 0; k < segs; k++) out[chain + (k + 1)] = a * raw[k] / s;
}
// the same skinning rules the rig was built with: which bones move a point of a layer, and how much
export function weightsFor(layer, x, y) {
  if (layer === 'body') return { Body: 1 };
  if (layer === 'pendant') return { Pend: 1 };
  if (layer === 'face' || layer === 'underhead') return { Head1: 1 };
  const w = {};
  if (layer === 'hair') {
    const sL = smooth((520 - x) / 150), sR = smooth((x - 440) / 150);
    const tL = clamp((y - 690) / (1120 - 690)), tR = clamp((y - 640) / (990 - 640)), tB = clamp((y - 440) / (770 - 440));
    const aL = smooth(tL / 0.30) * sL, aR = smooth(tR / 0.30) * sR;
    const face = smooth(1.5 - Math.hypot((x - 430) / 270, (y - 600) / 240));
    const aB = smooth(tB / 0.35) * face * (1 - Math.max(aL, aR));
    spread('CurtL', tL, 5, aL, w); spread('CurtR', tR, 4, aR, w); spread('Bangs', tB, 3, aB, w);
  } else if (layer === 'ribbon') {
    const mL = smooth(1 - (x - 230) / 60) * smooth((y - 470) / 60), mR = smooth((x - 700) / 50) * smooth((y - 380) / 40);
    const tL = clamp((y - 500) / (870 - 500)), tR = clamp((y - 380) / (580 - 380));
    spread('RibL', tL, 4, smooth(tL / 0.25) * mL, w); spread('RibR', tR, 3, smooth(tR / 0.30) * mR, w);
  }
  w.Head1 = Math.max(0, 1 - Object.values(w).reduce((p, q) => p + q, 0));
  return w;
}

const VS = `#version 300 es
in vec2 aPos; in vec2 aUV; in vec4 aIdx; in vec4 aW;
uniform vec4 uBone[64];          // two vec4 per bone: (a, b, c, d) and (e, f, -, -)
uniform vec3 uView;              // art units -> clip: scale x, scale y (art units per clip unit, inverse), unused
uniform vec2 uSize;              // the art's size
out vec2 vUV;
void main() {
  vec2 p = vec2(0.0); float sw = 0.0;
  for (int k = 0; k < 4; k++) {
    float w = aW[k]; if (w <= 0.0) continue;
    int b = int(aIdx[k]) * 2; vec4 A = uBone[b], B = uBone[b + 1];
    p += w * vec2(A.x * aPos.x + A.z * aPos.y + B.x, A.y * aPos.x + A.w * aPos.y + B.y); sw += w;
  }
  p += (1.0 - sw) * aPos;
  vUV = aUV;
  gl_Position = vec4(p.x / uSize.x * 2.0 - 1.0, 1.0 - p.y / uSize.y * 2.0, 0.0, 1.0);
}`;
const FS = `#version 300 es
precision highp float; in vec2 vUV; uniform sampler2D uTex; out vec4 o;
void main() { o = texture(uTex, vUV); }`;

const FVS = `#version 300 es
in vec2 aQ; uniform vec2 uC; uniform vec2 uS; uniform float uR; uniform vec2 uSize; out vec2 vQ;
void main() { vQ = aQ; float c = cos(uR), s = sin(uR); vec2 p = uC + vec2(aQ.x * uS.x * c - aQ.y * uS.y * s, aQ.x * uS.x * s + aQ.y * uS.y * c);
  gl_Position = vec4(p.x / uSize.x * 2.0 - 1.0, 1.0 - p.y / uSize.y * 2.0, 0.0, 1.0); }`;
const FFS = `#version 300 es
precision highp float; in vec2 vQ; uniform vec4 uCol; uniform float uCircle; out vec4 o;
void main() { float a = 1.0; if (uCircle > 0.5) { float d = length(vQ); float w = fwidth(d); a = 1.0 - smoothstep(1.0 - w, 1.0, d); } a *= uCol.a; o = vec4(uCol.rgb * a, a); }`;
const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);

export class Girl {
  constructor(canvas, layersInfo, rig) {
    const gl = this.gl = canvas.getContext('webgl2', { alpha: true, premultipliedAlpha: true, antialias: true, powerPreference: 'high-performance' });
    if (!gl) throw new Error('WebGL2 is needed');
    this.canvas = canvas; this.rig = rig; this.nudgeAngle = 0;
    this.idx = Object.fromEntries(rig.bones.map((n, i) => [n, i]));
    this.headSet = rig.bones.map((n, i) => /^(Head|Curt|Bangs|Rib)/.test(n) ? i : -1).filter((i) => i >= 0);
    const sh = (type, src) => { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s)); return s; };
    const pr = this.prog = gl.createProgram(); gl.attachShader(pr, sh(gl.VERTEX_SHADER, VS)); gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, FS)); gl.linkProgram(pr);
    if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(pr));
    this.uBone = gl.getUniformLocation(pr, 'uBone'); this.uSize = gl.getUniformLocation(pr, 'uSize'); this.uTex = gl.getUniformLocation(pr, 'uTex');
    this.bones = new Float32Array(64 * 4); this.mats = new Float32Array(rig.bones.length * 6); this.layers = [];
    this.info = layersInfo;
    // the moon and the little squares, drawn here too so the page never repaints a full-screen vector layer 60 times a second
    const fp = this.fprog = gl.createProgram(); gl.attachShader(fp, sh(gl.VERTEX_SHADER, FVS)); gl.attachShader(fp, sh(gl.FRAGMENT_SHADER, FFS)); gl.linkProgram(fp);
    this.fu = Object.fromEntries(['uC', 'uS', 'uR', 'uSize', 'uCol', 'uCircle'].map((n) => [n, gl.getUniformLocation(fp, n)]));
    this.qvao = gl.createVertexArray(); gl.bindVertexArray(this.qvao);
    const qb = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, qb); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const ql = gl.getAttribLocation(fp, 'aQ'); gl.enableVertexAttribArray(ql); gl.vertexAttribPointer(ql, 2, gl.FLOAT, false, 0, 0); gl.bindVertexArray(null);
    this.scene = null;
  }
  setScene(sc) { this.scene = sc; }
  prim(cx, cy, w, h, rot, col, alpha, circle) {
    const gl = this.gl, u = this.fu; gl.uniform2f(u.uC, cx, cy); gl.uniform2f(u.uS, w, h); gl.uniform1f(u.uR, rot); gl.uniform4f(u.uCol, col[0], col[1], col[2], alpha); gl.uniform1f(u.uCircle, circle ? 1 : 0);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }
  drawScene(t) {
    const sc = this.scene; if (!sc) return; const gl = this.gl;
    gl.useProgram(this.fprog); gl.bindVertexArray(this.qvao); gl.uniform2f(this.fu.uSize, AW, AH);
    const k = 1 + 0.010 * Math.sin(t * 2 * Math.PI / 7.5), m = sc.moon;
    this.prim(m.x, m.y, m.ring * 1.1, m.ring * 1.1, 0, hex(sc.colors.moon), 0.18, true);
    this.prim(m.x, m.y, m.ring * k, m.ring * k, 0, hex(sc.colors.ring), 1, true);
    this.prim(m.x, m.y, m.r * k, m.r * k, 0, hex(sc.colors.moon), 1, true);
    for (const [cx, cy, r] of sc.craters) this.prim(cx, cy, r, r, 0, hex('#e9dc7a'), 0.55, true);
    sc.shards.forEach((sh, i) => {
      const dx = 14 * Math.sin(t * 2 * Math.PI / (11 + i) + i * 1.7), dy = 22 * Math.sin(t * 2 * Math.PI / (14 + i * 0.7) + i * 0.9), a = (sh.a + 6 * Math.sin(t * 2 * Math.PI / (16 + i) + i * 2.3)) * Math.PI / 180;
      this.prim(sh.cx + dx, sh.cy + dy, sh.w / 2, sh.h / 2, a, hex(sc.colors.shard), 0.9, false);
    });
    gl.bindVertexArray(null);
  }
  async load(base) {
    const gl = this.gl;
    for (const name of ORDER) {
      const o = this.info.layers[name], step = STEP[name] || 16, K = this.info.px || 2;
      const img = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = base + name + '.png'; });
      const nx = Math.max(2, Math.ceil(o.w / step)), ny = Math.max(2, Math.ceil(o.h / step)), n = (nx + 1) * (ny + 1);
      const pos = new Float32Array(n * 2), uv = new Float32Array(n * 2), bi = new Float32Array(n * 4), bw = new Float32Array(n * 4);
      for (let j = 0, v = 0; j <= ny; j++) for (let i = 0; i <= nx; i++, v++) {
        const x = o.x + o.w * i / nx, y = o.y + o.h * j / ny;
        pos[2 * v] = x; pos[2 * v + 1] = y; uv[2 * v] = i / nx; uv[2 * v + 1] = j / ny;
        const w = weightsFor(name, x, y); let k = 0;
        for (const [b, wt] of Object.entries(w).sort((a, c) => c[1] - a[1])) if (wt > 1e-3 && k < 4) { bi[4 * v + k] = this.idx[b]; bw[4 * v + k] = wt; k++; }
      }
      const ind = new Uint32Array(nx * ny * 6); let q = 0;
      for (let j = 0; j < ny; j++) for (let i = 0; i < nx; i++) { const a = j * (nx + 1) + i, b = a + 1, c = a + nx + 1, d = c + 1; ind.set([a, c, b, b, c, d], q); q += 6; }
      const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
      const buf = (data, loc, size) => { const b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, size, gl.FLOAT, false, 0, 0); };
      const L = (nme) => gl.getAttribLocation(this.prog, nme);
      buf(pos, L('aPos'), 2); buf(uv, L('aUV'), 2); buf(bi, L('aIdx'), 4); buf(bw, L('aW'), 4);
      const ib = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, ind, gl.STATIC_DRAW);
      const tex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, true); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img); gl.generateMipmap(gl.TEXTURE_2D);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      this.layers.push({ name, vao, tex, count: ind.length });
    }
    gl.bindVertexArray(null);
  }
  // the bone matrices at time t (seconds): the two baked samples either side, blended; plus the push on the head when a word is chosen
  pose(t) {
    const R = this.rig, L = R.loop, f = ((t * R.fps) % L + L) % L, i = Math.floor(f), j = (i + 1) % L, u = f - i, A = R.frames[i], B = R.frames[j], m = this.mats;
    for (let b = 0; b < A.length; b++) for (let k = 0; k < 6; k++) m[6 * b + k] = A[b][k] + (B[b][k] - A[b][k]) * u;
    if (this.nudgeAngle) {
      const a = this.nudgeAngle * Math.PI / 180, c = Math.cos(a), s = Math.sin(a), px = 430, py = 880, re = px - c * px + s * py, rf = py - s * px - c * py;
      for (const b of this.headSet) {
        const o = 6 * b, A0 = m[o], B0 = m[o + 1], C0 = m[o + 2], D0 = m[o + 3], E0 = m[o + 4], F0 = m[o + 5];
        m[o] = c * A0 - s * B0; m[o + 1] = s * A0 + c * B0; m[o + 2] = c * C0 - s * D0; m[o + 3] = s * C0 + c * D0; m[o + 4] = c * E0 - s * F0 + re; m[o + 5] = s * E0 + c * F0 + rf;
      }
    }
    for (let b = 0; b < A.length; b++) { const o = 6 * b; this.bones.set([m[o], m[o + 1], m[o + 2], m[o + 3], m[o + 4], m[o + 5], 0, 0], 8 * b); }
  }
  resize(cssScale, dpr) {
    const w = Math.round(AW * cssScale * dpr), h = Math.round(AH * cssScale * dpr);
    if (this.canvas.width !== w || this.canvas.height !== h) { this.canvas.width = w; this.canvas.height = h; }
  }
  draw(t) {
    const gl = this.gl; this.pose(t);
    gl.viewport(0, 0, this.canvas.width, this.canvas.height); gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
    gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA); this.drawScene(t); gl.useProgram(this.prog);
    gl.uniform4fv(this.uBone, this.bones); gl.uniform2f(this.uSize, AW, AH); gl.uniform1i(this.uTex, 0);
    for (const l of this.layers) { gl.bindVertexArray(l.vao); gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, l.tex); gl.drawElements(gl.TRIANGLES, l.count, gl.UNSIGNED_INT, 0); }
    gl.bindVertexArray(null);
  }
}
