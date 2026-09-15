#!/usr/bin/env python3
"""Inspect a candidate offline; store mode requires a pinned release certificate.
No device operations, uploads, signing, credentials or network calls.
"""
import argparse, hashlib, json, os, re, struct, subprocess, sys, zipfile
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('apk',type=Path)
p.add_argument('--build-tools',type=Path,required=True)
p.add_argument('--expected-cert-sha256',help='Public release signing certificate fingerprint, not a password')
p.add_argument('--inspection-only',action='store_true',help='Record debug/unsigned facts without approving a release')
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
def run(tool,*args):
    r=subprocess.run([str(a.build_tools/tool),*map(str,args)],capture_output=True,text=True)
    return r.returncode,r.stdout+r.stderr
sig_rc,sig=run('apksigner','verify','--verbose','--print-certs',a.apk)
meta_rc,meta=run('aapt','dump','badging',a.apk)
_,permissions=run('aapt','dump','permissions',a.apk)
errors=[]
if meta_rc: errors.append('aapt inspection failed')
if sig_rc: errors.append('APK signature does not verify')
v2='Verified using v2 scheme (APK Signature Scheme v2): true' in sig
if not v2: errors.append('APK v2 signature missing')
certs=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)',sig)
if not a.inspection_only:
    if not a.expected_cert_sha256: errors.append('Pinned release certificate fingerprint is required')
    elif certs != [a.expected_cert_sha256.lower().replace(':','')]: errors.append('Release certificate does not match')
if 'application-debuggable' in meta: errors.append('Debuggable APK is not a store artifact')
if 'Android Debug' in sig: errors.append('Debug certificate is not a release signing identity')
if "package: name='dev.neyham.moshvr'" not in meta: errors.append('Unexpected package identity')
if "sdkVersion:'34'" not in meta or "targetSdkVersion:'34'" not in meta: errors.append('Unexpected SDK floor/target for this candidate')
if "install-location:'auto'" not in meta: errors.append('installLocation must be auto')
if "uses-feature: name='android.hardware.vr.headtracking' version='1'" not in meta: errors.append('Headtracking v1 requirement missing')
if a.apk.stat().st_size >= 1_000_000_000: errors.append('APK size must be below 1 GB')
libs=[]
with zipfile.ZipFile(a.apk) as z:
    for name in sorted(z.namelist()):
        if name.startswith('lib/') and name.endswith('.so'):
            b=z.read(name);elf64=b[:5]==b'\x7fELF\x02'
            machine=struct.unpack_from('<H',b,18)[0] if elf64 else None
            alignments=[]
            if elf64:
                phoff=struct.unpack_from('<Q',b,32)[0]
                phsize,phnum=struct.unpack_from('<HH',b,54)
                alignments=[struct.unpack_from('<Q',b,phoff+i*phsize+48)[0]
                    for i in range(phnum) if struct.unpack_from('<I',b,phoff+i*phsize)[0]==1]
            libs.append({'path':name,'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest(),
                'elf64':elf64,'machine':machine,'load_segment_alignments':alignments})
            if not elf64 or machine != 183 or not name.startswith('lib/arm64-v8a/'):
                errors.append('Unexpected native ABI: '+name)
    if not any(x['path']=='lib/arm64-v8a/libmosh_client.so' for x in libs):errors.append('mosh executable missing')
    if not any(x['path']=='lib/arm64-v8a/libtermux.so' for x in libs):errors.append('Termux JNI missing')
    legal=[n for n in z.namelist() if n.startswith('assets/legal/')]
    if 'assets/legal/THIRD_PARTY.md' not in legal:errors.append('Third-party notice missing')
report={'apk':str(a.apk.resolve()),'sha256':hashlib.sha256(a.apk.read_bytes()).hexdigest(),'bytes':a.apk.stat().st_size,
        'v2_verified':v2,'signer_cert_sha256':certs,'inspection_only':a.inspection_only,
        'store_gate_passed':not errors and not a.inspection_only,'errors':errors,'native_libraries':libs,
        'legal_assets':legal,'badging':meta,'permissions':permissions,'signature_verification':sig}
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:report[k] for k in ['sha256','bytes','v2_verified','inspection_only','store_gate_passed','errors']},indent=2))
sys.exit(0 if a.inspection_only else bool(errors))
