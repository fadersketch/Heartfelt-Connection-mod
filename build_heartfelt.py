# Build heartfelt_connection-1.5.116.jar from compiled classes + resources
# Usage: run fix_classpath.py + gen_compile.py + compile_heartfelt.bat first.
import os, shutil, zipfile, json, re, pathlib

BASE = os.path.dirname(os.path.abspath(__file__))
STAGING = os.path.join(BASE, 'staging_heartfelt')
OUT = os.path.join(BASE, 'out_heartfelt')
SRC = os.path.join(BASE, 'heartfelt_src')
VERSION = '1.5.116'
JAR_OUT = os.path.join(BASE, 'patched', 'heartfelt_connection-%s.jar' % VERSION)

MIXIN_CONFIGS = ['mixins.heartfelt.json', 'mixins.heartfelt.opt.json']

# 1. clean staging
for d in ['com', 'assets', 'data']:
    p = os.path.join(STAGING, d)
    if os.path.isdir(p):
        shutil.rmtree(p)

# 1a. purge stale .class files in out_heartfelt that have no corresponding .java source.
#     Prevents deleted/renamed classes from being silently packaged into the jar.
src_bases = set()
for p in pathlib.Path(SRC).rglob('*.java'):
    src_bases.add(str(p.relative_to(SRC)).replace('\\', '/')[:-len('.java')])
for p in pathlib.Path(OUT).rglob('*.class'):
    rel = str(p.relative_to(OUT)).replace('\\', '/')
    base = re.sub(r'\$.*$', '', rel[:-len('.class')])
    if base not in src_bases:
        p.unlink()
        print('purged stale class:', rel)

# 1b. META-INF with manifest (binary \r\n; MixinConfigs lists both configs) + mods.toml
meta = os.path.join(STAGING, 'META-INF')
if os.path.isdir(meta):
    shutil.rmtree(meta)
os.makedirs(meta)
manifest = ('Manifest-Version: 1.0\r\n'
            'MixinConfigs: %s\r\n' % ','.join(MIXIN_CONFIGS) +
            'Created-By: 21.0.7 (Microsoft)\r\n\r\n')
with open(os.path.join(meta, 'MANIFEST.MF'), 'wb') as f:
    f.write(manifest.encode('utf-8'))
shutil.copy2(os.path.join(SRC, 'META-INF', 'mods.toml'), os.path.join(meta, 'mods.toml'))

# 2. compiled classes
shutil.copytree(os.path.join(OUT, 'com'), os.path.join(STAGING, 'com'))

# 3. resources (mixins configs + pack.mcmeta; assets/data if present)
for mc in MIXIN_CONFIGS:
    shutil.copy2(os.path.join(SRC, mc), os.path.join(STAGING, mc))
shutil.copy2(os.path.join(SRC, 'pack.mcmeta'), os.path.join(STAGING, 'pack.mcmeta'))
for d in ['assets', 'data']:
    p = os.path.join(SRC, d)
    if os.path.isdir(p):
        shutil.copytree(p, os.path.join(STAGING, d))

# 4. zip everything (jar)
if os.path.exists(JAR_OUT):
    os.remove(JAR_OUT)
with zipfile.ZipFile(JAR_OUT, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(STAGING):
        for f in sorted(files):
            full = os.path.join(root, f)
            rel = os.path.relpath(full, STAGING).replace('\\', '/')
            if rel == 'mods.toml':
                continue  # only META-INF/mods.toml
            z.write(full, rel)

# 5. verify
with zipfile.ZipFile(JAR_OUT) as z:
    names = z.namelist()
    required = ['META-INF/mods.toml', 'META-INF/MANIFEST.MF'] + MIXIN_CONFIGS + [
        'com/heartfelt/connection/HeartfeltMod.class',
        'com/heartfelt/connection/HeartfeltExtension.class',
        'com/heartfelt/connection/combat/BetrayalRedemptionManager.class',
        'com/heartfelt/connection/debug/HeartfeltDebugApi.class',
        'com/heartfelt/connection/config/HeartfeltConfig.class',
        # v1.4.3:调整器物品资源
        'assets/heartfelt_connection/models/item/adjuster.json',
        'assets/heartfelt_connection/lang/zh_cn.json',
        'assets/heartfelt_connection/lang/en_us.json',
        'com/heartfelt/connection/item/AdjusterItem.class',
        'com/heartfelt/connection/item/AdjusterManager.class',
        'com/heartfelt/connection/item/HeartfeltItems.class',
        'com/heartfelt/connection/command/HeartfeltCommand.class',
    ]
    missing = [r for r in required if r not in names]
    if missing:
        raise SystemExit('FATAL: jar missing required entries: %s' % missing)
    # every mixin in both configs must have its class in the jar,
    # otherwise startup crashes with MixinApplyError (ChairNoDropMixin lesson)
    allmixins = []
    for mc in MIXIN_CONFIGS:
        data = json.loads(z.read(mc))
        allmixins += data['mixins'] + data.get('client', [])
    no_class = [m for m in allmixins if ('com/heartfelt/connection/mixin/' + m + '.class') not in names]
    if no_class:
        raise SystemExit('FATAL: jar missing mixin class: %s' % no_class)
    print('MISSING: none; mixins checked:', len(allmixins))
    print('TOTAL entries:', len(names))
print('BUILT:', JAR_OUT, os.path.getsize(JAR_OUT), 'bytes')
