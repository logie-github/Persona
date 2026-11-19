"""Persona 1's menu girl as a Blender puppet made of her own painted layers (the reference picture's pixels), so that at rest she IS the picture.

    xvfb-run -a python3 p1_puppet.py LAYERS_DIR OUT_DIR [--frames 0,10,20 | --rest] [--width 1977]

LAYERS_DIR is tools/art/build_layers_sr.py's output (PNG layers at 2x and layers.json). Every layer becomes a gridded mesh textured with its
painting, hung on bones in the picture's own units (1 unit = 1 px of the 1977x1398 art, 1000 px = 1 Blender metre). Frame 0 is the rest pose
and equals the reference; the 96-frame loop is keyed from sines that start and end at rest, with each part a little behind the one it hangs from,
and the hair, fringe, ribbon tails and pendant baked from a spring simulation of the head's and body's motion (overlap and follow-through).
"""
import bpy, bmesh, math, sys, os, json
import numpy as np
from mathutils import Vector, Euler

AW, AH, FPS, LOOP = 1977, 1398, 24, 96
ORDER = ['body', 'pendant', 'underhead', 'face', 'ribbon', 'hair']
STEP = {'hair': 9, 'ribbon': 7, 'face': 14, 'body': 22, 'pendant': 8, 'underhead': 14}

def P(x, y, d=0.0):      # art pixels -> Blender world (the camera looks along +Y; X right, Z up)
    return Vector(((x - AW / 2) / 1000.0, d, -(y - AH / 2) / 1000.0))

def clamp(x, a=0.0, b=1.0): return max(a, min(b, x))
def smooth(t): t = clamp(t); return t * t * (3 - 2 * t)

# ------------------------------------------------------------ the bones: chains of segments in the picture's coordinates
CHAINS = {   # name: (parent bone, [points top -> bottom]); each chain's joints are springs, deeper ones lag and swing wider
    'Body': (None, [(580, 1398), (580, 1040)]),
    'Head': ('Body', [(430, 880), (430, 560), (430, 300)]),
    'CurtL': ('Head', [(300, 690), (270, 790), (240, 890), (218, 990), (235, 1075), (275, 1120)]),
    'CurtR': ('Head', [(690, 640), (730, 740), (762, 840), (760, 930), (722, 990)]),
    'Bangs': ('Head', [(440, 440), (432, 540), (424, 640), (418, 770)]),
    'RibL': ('Head', [(95, 500), (78, 600), (70, 700), (80, 790), (108, 870)]),
    'RibR': ('Head', [(780, 380), (796, 450), (784, 520), (762, 580)]),
    'Pend': ('Body', [(560, 992), (572, 1050)]),
}

def bone_names(chain, n):
    if chain in ('Body', 'Pend'): return [chain]
    return [f'{chain}{k + 1}' for k in range(n - 1)]

def make_armature():
    ad = bpy.data.armatures.new('PuppetRig'); ob = bpy.data.objects.new('PuppetRig', ad); bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob; bpy.ops.object.mode_set(mode='EDIT')
    names = {}
    for chain, (parent, pts) in CHAINS.items():
        ns = bone_names(chain, len(pts)); prev = None
        for k, n in enumerate(ns):
            b = ad.edit_bones.new(n); b.head = P(*pts[k]); b.tail = P(*pts[k + 1])
            if k == 0 and parent: b.parent = ad.edit_bones['Body' if parent == 'Body' else 'Head1']
            elif k > 0: b.parent = prev
            prev = b
        names[chain] = ns
    bpy.ops.object.mode_set(mode='OBJECT')
    return ob, names

# the head bones: Head1 turns the whole head at the neck, Head2 the crown a little behind it (a free "lag" between neck and crown)
def weights_for(layer, x, y):
    """Which bones move this point of this layer, and how much (art pixels in, {bone: weight} out). Smooth everywhere: nothing parts."""
    def spread(chain, t, segs, a):
        raw = [max(0.0, 1 - abs(t * segs - (k + 0.5))) for k in range(segs)]; s_ = sum(raw) or 1.0
        return {f'{chain}{k + 1}': a * raw[k] / s_ for k in range(segs)}
    if layer == 'body': return {'Body': 1.0}
    if layer == 'pendant': return {'Pend': 1.0}
    if layer in ('face', 'underhead'): return {'Head1': 1.0}
    if layer == 'hair':
        sL = smooth((520 - x) / 150.0); sR = smooth((x - 440) / 150.0)              # which curtain, blended across the middle
        tL = clamp((y - 690) / (1120 - 690)); tR = clamp((y - 640) / (990 - 640)); tB = clamp((y - 440) / (770 - 440))
        aL = smooth(tL / 0.30) * sL; aR = smooth(tR / 0.30) * sR                    # a curtain is free of the head a third of the way down
        face = smooth(1.5 - math.hypot((x - 430) / 270.0, (y - 600) / 240.0))        # the fringe: the hair over the face
        aB = smooth(tB / 0.35) * face * (1 - max(aL, aR))
        w = {}; w.update(spread('CurtL', tL, 5, aL)); w.update(spread('CurtR', tR, 4, aR)); w.update(spread('Bangs', tB, 3, aB))
        w['Head1'] = max(0.0, 1 - sum(w.values())); return w
    if layer == 'ribbon':
        mL = smooth(1 - (x - 230) / 60.0) * smooth((y - 470) / 60.0); mR = smooth((x - 700) / 50.0) * smooth((y - 380) / 40.0)
        tL = clamp((y - 500) / (870 - 500)); tR = clamp((y - 380) / (580 - 380))
        w = {}; w.update(spread('RibL', tL, 4, smooth(tL / 0.25) * mL)); w.update(spread('RibR', tR, 3, smooth(tR / 0.30) * mR))
        w['Head1'] = max(0.0, 1 - sum(w.values())); return w
    return {'Head1': 1.0}

def build_layer(arm, name, box, png, depth):
    step = STEP.get(name, 22)
    x0, y0, w, h = box['x'], box['y'], box['w'], box['h']
    nx, ny = max(2, int(w / step)), max(2, int(h / step))
    bm = bmesh.new(); uvl = bm.loops.layers.uv.new('UV'); grid = []
    for j in range(ny + 1):
        row = []
        for i in range(nx + 1):
            row.append(bm.verts.new(P(x0 + w * i / nx, y0 + h * j / ny, depth)))
        grid.append(row)
    for j in range(ny):
        for i in range(nx):
            f = bm.faces.new([grid[j][i], grid[j][i + 1], grid[j + 1][i + 1], grid[j + 1][i]])
            for loop, (a, b) in zip(f.loops, [(i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)]):
                loop[uvl].uv = (a / nx, 1 - b / ny)
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me); bpy.context.scene.collection.objects.link(ob)
    # the painting, shown as it is (emission, straight alpha); sorted by depth
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree; nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial'); em = nt.nodes.new('ShaderNodeEmission'); tex = nt.nodes.new('ShaderNodeTexImage')
    tr = nt.nodes.new('ShaderNodeBsdfTransparent'); mix = nt.nodes.new('ShaderNodeMixShader')
    img = bpy.data.images.load(png); img.colorspace_settings.name = 'sRGB'; img.alpha_mode = 'STRAIGHT'; tex.image = img; tex.interpolation = 'Linear'; tex.extension = 'CLIP'
    nt.links.new(tex.outputs['Color'], em.inputs['Color']); nt.links.new(tex.outputs['Alpha'], mix.inputs['Fac'])
    nt.links.new(tr.outputs['BSDF'], mix.inputs[1]); nt.links.new(em.outputs['Emission'], mix.inputs[2]); nt.links.new(mix.outputs['Shader'], out.inputs['Surface'])
    try: m.surface_render_method = 'BLENDED'
    except Exception: m.blend_method = 'BLEND'
    me.materials.append(m)
    # skin weights from the layer's rules
    for v in me.vertices:
        pass
    groups = {}
    for v in me.vertices:
        px = v.co.x * 1000 + AW / 2; py = -v.co.z * 1000 + AH / 2
        for b, wt in weights_for(name, px, py).items():
            if wt > 1e-4:
                g = groups.get(b) or ob.vertex_groups.new(name=b); groups[b] = g; g.add([v.index], wt, 'REPLACE')
    ob.parent = arm; md = ob.modifiers.new('Armature', 'ARMATURE'); md.object = arm
    return ob

# ------------------------------------------------------------ the motion
def rot_y(arm, bone, deg, frame, tx=0.0, tz=0.0):
    """Turn a bone about the axis pointing at the camera (the picture's own rotation), plus a slide in the picture's plane (art px)."""
    pb = arm.pose.bones[bone]; pb.rotation_mode = 'QUATERNION'
    C = arm.data.bones[bone].matrix_local.to_quaternion()
    R = Euler((0, math.radians(deg), 0), 'XYZ').to_quaternion()
    pb.rotation_quaternion = C.inverted() @ R @ C
    pb.location = C.inverted() @ Vector((tx / 1000.0, 0, -tz / 1000.0)) if (tx or tz) else Vector((0, 0, 0))
    pb.keyframe_insert('rotation_quaternion', frame=frame); pb.keyframe_insert('location', frame=frame)

def scale_bone(arm, bone, sx, sz, frame):
    pb = arm.pose.bones[bone]; pb.scale = (sx, 1.0, sz)    # a bone's own axes: Y runs along it; X and Z across
    pb.keyframe_insert('scale', frame=frame)

def spring(signal, hz, zeta, dt):
    """x'' + 2*zeta*w*x' + w^2*x = -s''  (what hangs from a moving thing lags and overshoots it)."""
    w = 2 * math.pi * hz; n = len(signal); x = np.zeros(n); v = 0.0
    s = np.asarray(signal, float); acc = np.gradient(np.gradient(s, dt), dt); sub = 8
    for i in range(1, n):
        for _ in range(sub):
            v += (-acc[i] - 2 * zeta * w * v - w * w * x[i - 1]) * dt / sub; x[i] = x[i - 1] + v * dt / sub
    return x

def animate(arm, names):
    sc = bpy.context.scene; sc.render.fps = FPS; sc.frame_start = 0; sc.frame_end = LOOP - 1
    bpy.context.view_layer.objects.active = arm; bpy.ops.object.mode_set(mode='POSE')
    ph = lambda f: 2 * math.pi * f / LOOP
    loop = lambda f, k, lag: math.sin(k * ph(f) - lag) - math.sin(-lag)           # periodic, and zero at frame 0 and again at the end
    dt = 1.0 / FPS; reps = 4; N = LOOP * reps
    # primary motion in the picture's units: a breath, a lean and settle of the head that trails the body, a slow sway of the shoulders
    breath = lambda f: loop(f, 2, 0.3)
    body_sway = lambda f: 0.8 * loop(f, 1, 0.2) + 0.25 * loop(f, 2, 0.9)
    head_roll = lambda f: 2.0 * loop(f, 1, 0.8) + 0.6 * loop(f, 2, 1.5) + 0.25 * loop(f, 3, 0.4)
    head_dx = lambda f: 5.5 * loop(f, 1, 1.1) + 1.2 * loop(f, 2, 0.5); head_dz = lambda f: 3.0 * breath(f) + 1.2 * loop(f, 1, 0.9)
    # what the hanging things feel: the head's own turning and sliding, and the body's sway beneath it
    sig_main = np.array([head_roll(f % LOOP) + body_sway(f % LOOP) + 0.10 * head_dx(f % LOOP) for f in range(N)])
    sig_fine = np.array([head_roll(f % LOOP) * 0.6 + 0.30 * head_dx(f % LOOP) for f in range(N)])
    for f in range(LOOP):
        scale_bone(arm, 'Body', 1.0, 1 + 0.0075 * breath(f), f)
        rot_y(arm, 'Body', body_sway(f), f)
        rot_y(arm, 'Head1', head_roll(f), f, tx=head_dx(f), tz=head_dz(f))
        scale_bone(arm, 'Head1', 1.0, 1 + 0.003 * breath(f), f)
        rot_y(arm, 'Head2', 0.7 * loop(f, 1, 1.6), f)
    # follow-through, baked: every joint of every chain is a spring driven by what its parent did; deeper joints are slower and swing wider,
    # and each chain has a little wind of its own at a different speed so no two hang in step
    drive = {  # chain: (driver, hz, zeta, total swing in degrees, wind degrees, wind harmonic, wind phase)
        'CurtL': (sig_main, 1.05, 0.17, 9.0, 1.0, 1, 0.3), 'CurtR': (sig_main, 0.95, 0.17, 9.0, 1.0, 2, 1.7),
        'Bangs': (sig_fine, 1.8, 0.26, 3.6, 0.5, 2, 0.9),
        'RibL': (sig_main, 1.45, 0.14, 13.0, 1.8, 2, 0.1), 'RibR': (sig_main, 1.65, 0.15, 9.0, 1.4, 1, 2.1),
        'Pend': (sig_main, 1.25, 0.16, 8.0, 0.8, 1, 1.2)}
    for chain, (sig, hz, zeta, amp, wind, wk, wph) in drive.items():
        segs = names[chain]; n = len(segs)
        for d, bone in enumerate(segs):
            r = spring(sig, hz * (0.93 ** d), zeta, dt)[-LOOP:]
            peak = max(1e-9, float(np.max(np.abs(r)))); r = r / peak * amp / n * (1 + 0.45 * d)      # degrees at this joint
            r = r - r[0]
            for f in range(LOOP):
                w = wind * loop(f, wk, wph - 0.55 * d)
                rot_y(arm, bone, float(r[f]) + w, f)
    bpy.ops.object.mode_set(mode='OBJECT')

# ------------------------------------------------------------ camera and render
def setup(width, rest):
    sc = bpy.context.scene; sc.render.engine = 'BLENDER_EEVEE'
    sc.render.resolution_x = width; sc.render.resolution_y = int(round(width * AH / AW)); sc.render.resolution_percentage = 100
    sc.render.film_transparent = True; sc.view_settings.view_transform = 'Standard'; sc.view_settings.look = 'None'
    sc.render.image_settings.file_format = 'PNG'; sc.render.image_settings.color_mode = 'RGBA'
    try: sc.eevee.taa_render_samples = 16
    except Exception: pass
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam')); sc.collection.objects.link(cam); sc.camera = cam
    cam.data.type = 'ORTHO'; cam.data.ortho_scale = AW / 1000.0; cam.data.sensor_fit = 'HORIZONTAL'
    cam.location = (0, -5, 0); cam.rotation_euler = (math.radians(90), 0, 0)

def export_rig(arm, path, fps=60):
    """The baked animation as data, sampled at [fps]: for each sample, each bone's skin matrix as a 2D affine in the art's own units
    (x' = a x + c y + e, y' = b x + d y + f). The page skins the layers with these on the GPU, so it plays exactly what was keyed here,
    at the screen's own frame rate, at any size."""
    sc = bpy.context.scene; names = [b.name for b in arm.pose.bones]
    def to_world(x, y): return Vector(((x - AW / 2) / 1000.0, 0.0, -(y - AH / 2) / 1000.0))
    def to_art(v): return (v.x * 1000.0 + AW / 2, -v.z * 1000.0 + AH / 2)
    n = int(round(LOOP / FPS * fps)); frames = []
    for i in range(n):
        t = i * FPS / fps; f0 = int(math.floor(t)); sc.frame_set(f0, subframe=t - f0); bpy.context.view_layer.update(); row = []
        for pb in arm.pose.bones:
            M = arm.matrix_world @ pb.matrix @ (arm.matrix_world @ pb.bone.matrix_local).inverted()
            p0 = to_art(M @ to_world(0, 0)); px = to_art(M @ to_world(1, 0)); py = to_art(M @ to_world(0, 1))
            row.append([round(px[0] - p0[0], 5), round(px[1] - p0[1], 5), round(py[0] - p0[0], 5), round(py[1] - p0[1], 5), round(p0[0], 3), round(p0[1], 3)])
        frames.append(row)
    json.dump({'fps': fps, 'loop': n, 'bones': names, 'frames': frames}, open(path, 'w'), separators=(',', ':'))

def main():
    a = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    src, out = a[0], a[1]
    rest = '--rest' in a
    frames = [int(x) for x in a[a.index('--frames') + 1].split(',')] if '--frames' in a else None
    width = int(a[a.index('--width') + 1]) if '--width' in a else AW
    os.makedirs(out, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    info = json.load(open(os.path.join(src, 'layers.json')))
    arm, names = make_armature()
    for k, name in enumerate(ORDER): build_layer(arm, name, info['layers'][name], os.path.join(src, name + '.png'), 0.30 - 0.01 * k)
    animate(arm, names)
    setup(width, rest)
    sc = bpy.context.scene
    if '--export-rig' in a:
        export_rig(arm, a[a.index('--export-rig') + 1]); return
    todo = [0] if rest else (frames if frames is not None else list(range(LOOP)))
    for f in todo:
        sc.frame_set(f); sc.render.filepath = os.path.join(out, f'frame_{f:03d}.png'); bpy.ops.render.render(write_still=True)

if __name__ == '__main__':
    main()
