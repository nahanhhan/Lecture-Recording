"""Fetch pinned upstream build tools and sherpa-onnx Android runtime, with SHA-256 checks."""
from pathlib import Path
import argparse
import concurrent.futures
import hashlib
import json
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
VERSION = '1.12.27'
NATIVE_SHA256 = 'd88c7563cd1ba338ee2f42f69eb999ce09ac0d5f2ed3a951acda964e6fdbb051'

def download(url, target, checksum=None):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and (not checksum or hashlib.sha256(target.read_bytes()).hexdigest() == checksum):
        return target
    temporary = target.with_suffix(target.suffix + '.partial')
    with urllib.request.urlopen(url, timeout=120) as source, temporary.open('wb') as output:
        while chunk := source.read(1024 * 1024):
            output.write(chunk)
    if checksum and hashlib.sha256(temporary.read_bytes()).hexdigest() != checksum:
        raise RuntimeError(f'Checksum mismatch: {target.name}')
    temporary.replace(target)
    print(f'Downloaded {target.name}', flush=True)
    return target

def native(abi='arm64-v8a'):
    base = f'https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v{VERSION}'
    names = ['FeatureConfig.kt', 'HomophoneReplacerConfig.kt', 'QnnConfig.kt', 'OfflineRecognizer.kt', 'OfflineStream.kt']
    destination = ROOT / 'app/src/main/java/com/k2fsa/sherpa/onnx'
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        list(executor.map(lambda name: download(f'{base}/sherpa-onnx/kotlin-api/{name}', destination / name), names))
    archive = download(f'https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/sherpa-onnx-v{VERSION}-android.tar.bz2',
                       TOOLS / f'sherpa-onnx-v{VERSION}-android.tar.bz2', NATIVE_SHA256)
    libs = ROOT / f'app/src/main/jniLibs/{abi}'
    libs.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as bundle:
        for entry in bundle.getmembers():
            if entry.isfile() and f'/{abi}/' in entry.name and entry.name.endswith('.so'):
                with bundle.extractfile(entry) as source, (libs / Path(entry.name).name).open('wb') as output:
                    output.write(source.read())
                print(f'Installed {Path(entry.name).name}', flush=True)
    if not (libs / 'libsherpa-onnx-jni.so').exists():
        raise RuntimeError('JNI library missing from pinned release')
    download(f'{base}/LICENSE', ROOT / 'third_party/sherpa-onnx-LICENSE')

def gradle():
    base = 'https://raw.githubusercontent.com/gradle/gradle/v8.13.0'
    for remote, local in [('gradlew','gradlew'), ('gradlew.bat','gradlew.bat'),
                          ('gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.jar')]:
        download(f'{base}/{remote}', ROOT / local)
    checksum = urllib.request.urlopen('https://downloads.gradle.org/distributions/gradle-8.13-bin.zip.sha256', timeout=60).read().decode().strip()
    properties = ROOT / 'gradle/wrapper/gradle-wrapper.properties'
    text = properties.read_text(encoding='utf-8')
    if 'distributionSha256Sum=' not in text:
        properties.write_text(text + f'\ndistributionSha256Sum={checksum}\n', encoding='utf-8')
    archive = download('https://downloads.gradle.org/distributions/gradle-8.13-bin.zip', TOOLS / 'gradle-8.13-bin.zip', checksum)
    with zipfile.ZipFile(archive) as bundle:
        bundle.extractall(TOOLS)
    print('Gradle 8.13 ready', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--native', action='store_true')
    parser.add_argument('--gradle', action='store_true')
    parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], default='arm64-v8a')
    args = parser.parse_args()
    if args.native: native(args.abi)
    if args.gradle: gradle()
