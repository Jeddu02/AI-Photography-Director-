#!/usr/bin/env python3
"""Static APK validation; this does not establish device/camera compatibility."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import zipfile

parser=argparse.ArgumentParser()
parser.add_argument('apk',type=Path)
parser.add_argument('--build-tools',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--require-camera', action='store_true', help='Verify that the in-app camera can request camera access.')
a=parser.parse_args();a.output.mkdir(parents=True,exist_ok=True)
for exe,args,name in [
    ('apksigner',['verify','--verbose','--print-certs',str(a.apk)],'apk-signature.txt'),
    ('aapt',['dump','badging',str(a.apk)],'apk-metadata.txt'),
    ('aapt',['dump','permissions',str(a.apk)],'apk-permissions.txt'),
    ('zipalign',['-c','-P','16','4',str(a.apk)],'apk-zip-alignment.txt'),
]:
    result=subprocess.run([str(a.build_tools/exe)]+args,capture_output=True,text=True)
    (a.output/name).write_text(result.stdout+result.stderr+f'\nExit code: {result.returncode}\n')
    if result.returncode:raise SystemExit(f'{exe} validation failed')
permissions=(a.output/'apk-permissions.txt').read_text()
for forbidden in ['INTERNET','RECORD_AUDIO','EXTERNAL_STORAGE','READ_PHONE_STATE','WAKE_LOCK','ACCESS_NETWORK_STATE']:
    if forbidden in permissions:raise SystemExit(f'Unexpected permission: {forbidden}')
if 'POST_NOTIFICATIONS' not in permissions:raise SystemExit('Missing notification permission declaration')
camera_declared = "name='android.permission.CAMERA'" in permissions
if camera_declared != a.require_camera:
    raise SystemExit('Camera permission differs from the expected release configuration')
metadata=(a.output/'apk-metadata.txt').read_text()
if "name='com.jade.ai_photography_director'" not in metadata:raise SystemExit('Unexpected package identity')
native=[]
with zipfile.ZipFile(a.apk) as z:
    if z.testzip() is not None:raise SystemExit('APK ZIP integrity failed')
    model=z.read('assets/efficientdet.tflite')
    if hashlib.sha256(model).hexdigest()!='40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58':raise SystemExit('Object model hash mismatch')
    names=z.namelist()
    for needle in ['libmediapipe_tasks_jni.so','libface_detector_v2_jni.so','assets/models_bundled/']:
        if not any(needle in n for n in names):raise SystemExit(f'Missing bundled component: {needle}')
    (a.output/'bundled-models.txt').write_text('\n'.join(n for n in names if n.startswith('assets/models_bundled/') or n=='assets/efficientdet.tflite' or 'mediapipe_tasks_jni' in n)+'\n')
    for name in names:
        if not name.endswith('.so'):continue
        data=z.read(name)
        if data[:4]!=b'\x7fELF':raise SystemExit(f'Invalid native ELF: {name}')
        bits=64 if data[4]==2 else 32
        endian='<' if data[5]==1 else '>'
        offset=struct.unpack_from(endian+('Q' if bits==64 else 'I'),data,32 if bits==64 else 28)[0]
        size=struct.unpack_from(endian+'H',data,54 if bits==64 else 42)[0]
        count=struct.unpack_from(endian+'H',data,56 if bits==64 else 44)[0]
        aligns=[]
        for i in range(count):
            pos=offset+i*size
            if struct.unpack_from(endian+'I',data,pos)[0]==1:
                aligns.append(struct.unpack_from(endian+('Q' if bits==64 else 'I'),data,pos+(48 if bits==64 else 28))[0])
        if bits==64 and (not aligns or min(aligns)<16384):raise SystemExit(f'64-bit ELF alignment failed: {name} {aligns}')
        native.append({'path':name,'bits':bits,'loadAlignments':aligns})
(a.output/'native-elf-alignment.json').write_text(json.dumps(native,indent=2)+'\n')
summary={'apkBytes':a.apk.stat().st_size,'apkSha256':hashlib.sha256(a.apk.read_bytes()).hexdigest(),
    'nativeLibraryCount':len(native),'signature':'verified','permissions':'verified','archiveIntegrity':'passed',
    'bundledModels':'verified','zip16KBAlignment':'passed','elf64PageAlignment':'passed'}
(a.output/'artifact-verification.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary))
