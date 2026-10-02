"""Exercise recording, pause, camera and recovery on a dedicated debug emulator."""
from pathlib import Path
import argparse
import re
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--device', default='emulator-5554')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
output = root / '.tools/smoke'
output.mkdir(parents=True, exist_ok=True)
package = 'io.github.nahanhhan.lecturerecording'

def adb(*command):
    return subprocess.check_output([args.adb, '-s', args.device, *command], stderr=subprocess.STDOUT)

def ui():
    adb('shell', 'uiautomator', 'dump', '/sdcard/lecture-smoke.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/lecture-smoke.xml'))

def click(label):
    for attempt in range(10):
        for node in ui().iter('node'):
            if label in [node.get('text'), node.get('content-desc')] and node.get('enabled') == 'true':
                x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                time.sleep(.5)
                return
        time.sleep(.5)
    raise RuntimeError(f'Control not found: {label}')

def screenshot(name):
    (output / name).write_bytes(adb('exec-out', 'screencap', '-p'))

def database():
    snapshot = output / f'snapshot_{time.time_ns()}'
    snapshot.mkdir()
    for suffix in ['', '-wal']:
        try:
            (snapshot / ('lectures.db' + suffix)).write_bytes(adb('exec-out', 'run-as', package, 'cat', 'databases/lectures.db' + suffix))
        except subprocess.CalledProcessError:
            pass
    return sqlite3.connect(snapshot / 'lectures.db')

adb('shell','am','force-stop',package)
adb('shell','am','start','-n',package+'/.MainActivity'); time.sleep(1)
click('开始课堂录音'); click('开始'); time.sleep(2); click('回到当前录音')
screenshot('recording.png')
click('暂停')
with database() as db:
    lesson = db.execute('select id,status,samples from lessons order by createdAt desc limit 1').fetchone()
assert lesson[1] == 'paused', lesson
time.sleep(2)
with database() as db:
    paused = db.execute('select status,samples from lessons where id=?', (lesson[0],)).fetchone()
assert paused == ('paused',lesson[2]), (lesson,paused)
print('PASS pause keeps audio sample clock fixed', flush=True)
click('拍照'); click('拍照'); time.sleep(2); screenshot('camera.png'); click('返回录音')
with database() as db:
    photo = db.execute('select filename,audioTimeMs,capturedAtEpochMs from photos where lessonId=?', (lesson[0],)).fetchone()
assert photo and photo[1] == lesson[2] * 1000 // 16000, (photo, lesson)
assert int(re.match(r'ast_(\d+)_',photo[0]).group(1)) == photo[1]
print('PASS paused photo uses frozen audio position and independent capture date', flush=True)
click('继续'); time.sleep(2)
adb('shell','input','keyevent','3'); time.sleep(2)
with database() as db:
    background = db.execute('select status,samples from lessons where id=?', (lesson[0],)).fetchone()
assert background[0] == 'recording' and background[1] > lesson[2], background
print('PASS background recording advances and persists samples',flush=True)
adb('shell','am','start','-n',package+'/.MainActivity'); time.sleep(1)
click('结束'); time.sleep(2)
with database() as db:
    final = db.execute('select status,samples from lessons where id=?', (lesson[0],)).fetchone()
    chunks = db.execute('select path,sampleCount from chunks where lessonId=?', (lesson[0],)).fetchall()
assert final[0]=='completed' and chunks, (final,chunks)
screenshot('completed.png')
for path,samples in chunks:
    audio = adb('exec-out','run-as',package,'cat',path)
    assert len(audio) == 44 + samples * 2, (len(audio),samples)
    assert audio[:4]==b'RIFF' and audio[8:12]==b'WAVE'
    assert int.from_bytes(audio[40:44],'little')==samples*2
print('PASS completed WAV headers and persisted sample counts match',flush=True)
adb('shell','am','force-stop',package)
adb('shell','am','start','-n',package+'/.MainActivity'); time.sleep(1)
with database() as db:
    restored=db.execute('select status,samples from lessons where id=?',(lesson[0],)).fetchone()
assert restored==final,(restored,final)
print('PASS completed recording survives cold restart',flush=True)
click('开始课堂录音'); click('开始'); time.sleep(2)
with database() as db:
    interrupted_id=db.execute('select id from lessons order by createdAt desc limit 1').fetchone()[0]
adb('shell','am','force-stop',package)
adb('shell','am','start','-n',package+'/.MainActivity'); time.sleep(1)
for attempt in range(10):
    with database() as db:
        interrupted=db.execute('select status,samples from lessons where id=?',(interrupted_id,)).fetchone()
        recovered_chunks=db.execute('select path,sampleCount from chunks where lessonId=?',(interrupted_id,)).fetchall()
    consistent=interrupted[0]=='interrupted' and interrupted[1]>0 and bool(recovered_chunks)
    for path,samples in recovered_chunks:
        data=adb('exec-out','run-as',package,'cat',path)
        consistent=consistent and len(data)==44+samples*2 and int.from_bytes(data[40:44],'little')==samples*2
    if consistent: break
    time.sleep(1)
assert consistent,(interrupted,recovered_chunks)
print('PASS forced-stop recovery marks interruption and repairs WAV headers',flush=True)
