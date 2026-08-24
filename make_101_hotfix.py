# -*- coding: utf-8 -*-
# Hotfix 1.0.1: take the RELEASED heartfelt_connection-1.0.0.jar and
#   1) bump mods.toml version to 1.0.1 + append v1.0.1 changelog entry
#   2) bundle mixinextras-forge 0.5.4 via Jar-in-Jar (fixes startup crash
#      ClassMetadataNotFoundException: ...wrapoperation.Operation on clean
#      mod lists where no other mod provides MixinExtras)
# Class entries are copied byte-for-byte from the released 1.0.0 jar:
# out_heartfelt is 3 classes behind the release, a full rebuild would
# silently change behavior. Only META-INF differs.
import hashlib
import json
import os
import shutil
import zipfile

BASE = os.path.dirname(os.path.abspath(__file__))
SRC_TOML = os.path.join(BASE, 'heartfelt_src', 'META-INF', 'mods.toml')
OLD_JAR = r'C:\Users\Sketch\Downloads\heartfelt_connection-1.0.0.jar'
OUT_JAR = os.path.join(BASE, 'patched', 'heartfelt_connection-1.0.1.jar')
DL_COPY = r'C:\Users\Sketch\Downloads\heartfelt_connection-1.0.1.jar'
MX_D = r'D:\.minecraft\libraries\io\github\llamalad7\mixinextras-forge\0.5.4\mixinextras-forge-0.5.4.jar'
MX_LIB = os.path.join(BASE, 'lib', 'mixinextras-forge-0.5.4.jar')
MX_NAME = 'mixinextras-forge-0.5.4.jar'

CHANGELOG = ('；v1.0.1：修复纯净环境启动崩溃——本模组 Mixin 依赖 MixinExtras 库，'
             '1.0.0 未将其打包、依赖环境中其他模组顺带提供；纯净小整合（仅车万女仆+心契誓约+'
             '爱憎分明+promaid 等少量模组）无任何模组提供时，启动必报 '
             'ClassMetadataNotFoundException: com.llamalad7.mixinextras.injector.wrapoperation.Operation'
             '（MixinTransformerError）崩溃。现以 Jar-in-Jar 内置 mixinextras-forge 0.5.4，'
             '任意模组环境开箱即用；其余与 1.0.0 完全一致，无任何机制改动。')


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


# 0. cache the forge-variant MixinExtras into the project (self-contained builds)
os.makedirs(os.path.dirname(MX_LIB), exist_ok=True)
if not os.path.exists(MX_LIB):
    shutil.copy2(MX_D, MX_LIB)
with open(MX_LIB, 'rb') as f:
    mx_bytes = f.read()

# 1. mods.toml: bump version + append changelog (byte-level replace, keeps BOM/CRLF)
#    idempotent: skips edits already applied by a previous run
with open(SRC_TOML, 'rb') as f:
    toml = f.read()
v_old, v_new = b'version="1.0.0"', b'version="1.0.1"'
tail_old = '本就不触发。"'.encode('utf-8')
tail_new = ('本就不触发。' + CHANGELOG + '"').encode('utf-8')
mark = '；v1.0.1：'.encode('utf-8')
changed = []
if toml.count(v_old) == 1:
    toml = toml.replace(v_old, v_new, 1)
    changed.append('version')
elif v_new in toml:
    pass
else:
    raise AssertionError('version pattern not found')
if toml.count(tail_old) == 1:
    toml = toml.replace(tail_old, tail_new, 1)
    changed.append('changelog')
elif mark in toml:
    pass
else:
    raise AssertionError('description tail not found')
if changed:
    with open(SRC_TOML, 'wb') as f:
        f.write(toml)
print('mods.toml in src: %s (version 1.0.1 + changelog present)' % ('updated: ' + ', '.join(changed) if changed else 'already patched'))

# 2. assemble 1.0.1 from the released 1.0.0
os.makedirs(os.path.dirname(OUT_JAR), exist_ok=True)
with zipfile.ZipFile(OLD_JAR) as zin, \
        zipfile.ZipFile(OUT_JAR, 'w', zipfile.ZIP_DEFLATED) as zout:
    old_names = zin.namelist()
    for name in old_names:
        if name == 'META-INF/mods.toml':
            continue
        zout.writestr(name, zin.read(name))
    zout.writestr('META-INF/mods.toml', toml)
    zout.writestr('META-INF/jarjar/metadata.json', json.dumps({
        'jars': [{
            'identifier': {'group': 'io.github.llamalad7', 'artifact': 'mixinextras-forge'},
            'version': {'range': '[0.5.4,)', 'artifactVersion': '0.5.4'},
            'path': 'META-INF/jarjar/' + MX_NAME,
        }]
    }, indent=2).encode('utf-8'))
    zout.writestr('META-INF/jarjar/' + MX_NAME, mx_bytes)

# 3. verify
with zipfile.ZipFile(OUT_JAR) as z, zipfile.ZipFile(OLD_JAR) as zo:
    names = set(z.namelist())
    old_count = len(set(zo.namelist()))
    assert len(names) == old_count + 2, 'entry count mismatch: %d vs %d+2' % (len(names), old_count)
    for req in ('META-INF/mods.toml', 'META-INF/MANIFEST.MF',
                'META-INF/jarjar/metadata.json', 'META-INF/jarjar/' + MX_NAME,
                'mixins.heartfelt.json', 'mixins.heartfelt.opt.json'):
        assert req in names, 'missing ' + req
    newmod = z.read('META-INF/mods.toml')
    assert b'version="1.0.1"' in newmod and 'v1.0.1'.encode('utf-8') in newmod
    meta = json.loads(z.read('META-INF/jarjar/metadata.json'))
    assert meta['jars'][0]['version']['artifactVersion'] == '0.5.4'
    assert meta['jars'][0]['path'] == 'META-INF/jarjar/' + MX_NAME
    assert z.read('META-INF/jarjar/' + MX_NAME) == mx_bytes
    drift = [n for n in zo.namelist() if n != 'META-INF/mods.toml' and z.read(n) != zo.read(n)]
    assert not drift, 'content drift vs 1.0.0: %s' % drift
print('OK: %d entries (1.0.0 had %d), all original entries byte-identical' % (len(names), old_count))

shutil.copy2(OUT_JAR, DL_COPY)
print('BUILT :', OUT_JAR)
print('SIZE  :', os.path.getsize(OUT_JAR))
print('SHA256:', sha256(OUT_JAR))
print('COPY  :', DL_COPY)
