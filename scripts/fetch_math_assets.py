"""Install pinned KaTeX distribution for offline formulas in reading and PDF export."""
from pathlib import Path
import tarfile
import urllib.request
import hashlib
import base64
import json

root = Path(__file__).resolve().parents[1]
version = '0.16.22'
with urllib.request.urlopen(f'https://registry.npmjs.org/katex/{version}', timeout=60) as source:
    distribution = json.load(source)['dist']
archive = root / '.tools/katex.tgz'
archive.parent.mkdir(exist_ok=True)
with urllib.request.urlopen(distribution['tarball'], timeout=120) as source:
    data = source.read()
algorithm, encoded = distribution['integrity'].split('-', 1)
if base64.b64encode(hashlib.new(algorithm, data).digest()).decode() != encoded:
    raise RuntimeError('KaTeX integrity mismatch')
archive.write_bytes(data)
target = root / 'app/src/main/assets/vendor/katex'
target.mkdir(parents=True, exist_ok=True)
with tarfile.open(archive) as bundle:
    for entry in bundle.getmembers():
        name = entry.name
        if name in ['package/dist/katex.min.js', 'package/dist/katex.min.css', 'package/dist/contrib/auto-render.min.js']:
            output = target / Path(name).name
        elif name.startswith('package/dist/fonts/') and entry.isfile():
            output = target / 'fonts' / Path(name).name
        elif name == 'package/LICENSE':
            output = root / 'third_party/katex-LICENSE'
        else:
            continue
        output.parent.mkdir(parents=True, exist_ok=True)
        with bundle.extractfile(entry) as source:
            output.write_bytes(source.read())
(target / 'INTEGRITY.txt').write_text(f'katex@{version}\n{distribution["integrity"]}\n')
print(f'Installed KaTeX {version} for offline rendering')
