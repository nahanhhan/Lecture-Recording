"""Create a persistent local signing identity; no passwords are printed or committed."""
from pathlib import Path
import os
import secrets
import subprocess
import shutil

root = Path(__file__).resolve().parents[1]
properties = root / 'keystore.properties'
if properties.exists():
    print('Existing signing configuration preserved.')
    raise SystemExit(0)
directory = root / '.tools/signing'
directory.mkdir(parents=True, exist_ok=True)
store = directory / 'lecture-release.jks'
if store.exists():
    raise RuntimeError('A signing key already exists; restore its configuration rather than replacing it.')
keytool = Path(os.environ['JAVA_HOME']) / 'bin' / ('keytool.exe' if os.name == 'nt' else 'keytool') if os.environ.get('JAVA_HOME') else shutil.which('keytool')
password = secrets.token_urlsafe(32)
environment = dict(os.environ, LECTURE_STORE_PASSWORD=password)
subprocess.run([str(keytool), '-genkeypair', '-keystore', str(store), '-storetype', 'JKS', '-alias', 'lecture',
                '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000', '-dname',
                'CN=Lecture Recording, OU=Personal, O=nahanhhan, C=CN',
                '-storepass:env', 'LECTURE_STORE_PASSWORD', '-keypass:env', 'LECTURE_STORE_PASSWORD'],
               env=environment, check=True, capture_output=True)
properties.write_text(f'storeFile={store.as_posix()}\nstorePassword={password}\nkeyAlias=lecture\nkeyPassword={password}\n', encoding='utf-8')
(directory / 'BACKUP.txt').write_text('Keep lecture-release.jks and the project-root keystore.properties together in a private backup.\nReuse this key for all future APK updates. Do not upload either file to GitHub.\n', encoding='utf-8')
print('Private signing identity created. Keep .tools/signing and keystore.properties in a private backup.')
