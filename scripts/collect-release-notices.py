#!/usr/bin/env python3
"""Aggregate all notice texts present in resolved release artifacts, with attribution.
This is an inventory, not a conclusion of license compatibility/completeness.
"""
from pathlib import Path
import hashlib,io,json,re,zipfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
E=ROOT/'build/reports/release-inventory'
OUT=ROOT/'app/src/main/assets/legal'
rows=json.loads((E/'release-dependencies.json').read_text())
blocks={};licenses=[]
def inspect(data,source):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            for n in z.namelist():
                if n.endswith('/') : continue
                if re.search(r'(^|/)(LICENSE|LICENCE|NOTICE|COPYING)([._/-]|$)',n,re.I) and z.getinfo(n).file_size<500_000:
                    content=z.read(n).decode('utf-8',errors='replace').strip()
                    digest=hashlib.sha256(content.encode()).hexdigest()
                    block=blocks.setdefault(digest,{'text':content,'sources':[]})
                    block['sources'].append(source+'!'+n)
                elif n=='classes.jar': inspect(z.read(n),source+'!classes.jar')
    except zipfile.BadZipFile: pass
for row in rows:
    p=Path(row['file'])
    if p.suffix=='.pom':
        root=ET.parse(p).getroot();ns={'m':'http://maven.apache.org/POM/4.0.0'}
        names=[{'name':x.findtext('m:name',default='',namespaces=ns),'url':x.findtext('m:url',default='',namespaces=ns)} for x in root.findall('m:licenses/m:license',ns)]
        licenses.append({'coordinate':row['coordinate'],'declared_licenses':names,'pom_sha256':row['sha256']})
    else: inspect(p.read_bytes(),row['coordinate'])
text=['# Resolved release dependency notices\n\nGenerated from the cached artifacts listed in the release inventory. All matching texts are retained; duplicate texts share attribution. This does not resolve GPL/Meta SDK compatibility or missing native dependency provenance.\n']
for b in blocks.values():
    text.append('\n## '+', '.join(b['sources'])+'\n\n'+b['text']+'\n')
(OUT/'DEPENDENCY-NOTICES.txt').write_text('\n'.join(text))
(E/'license-inventory.json').write_text(json.dumps({'pom_declarations':licenses,'notice_texts':len(blocks),'limitations':['POM licenses are publisher declarations, not a legal conclusion','Native static dependencies and patched font glyph licenses require separate provenance review','BOMs may have no license and do not themselves ship code']},indent=2)+'\n')
print(f'{len(blocks)} distinct notice texts, {len(licenses)} POMs inventoried')
