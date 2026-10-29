"""Exercise the actual JavaScript emitted by SubmissionPage with simulated page states."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/fr/streakkeeper/app/SubmissionPage.java'
with tempfile.TemporaryDirectory() as tmp:
    tmp = Path(tmp)
    printer = tmp / 'PrintSubmissionPage.java'
    printer.write_text('package fr.streakkeeper.app; public class PrintSubmissionPage { public static void main(String[] args) { System.out.print(SubmissionPage.SCRIPT); } }')
    subprocess.run(['javac', '-d', str(tmp), str(source), str(printer)], check=True)
    script = subprocess.check_output(['java', '-cp', str(tmp), 'fr.streakkeeper.app.PrintSubmissionPage'], text=True)
    test = tmp / 'test.js'
    test.write_text('''
function check(name, text, frame, expected) {
    globalThis.document = {body:{innerText:text},querySelectorAll:()=>frame ? [frame] : []};
    globalThis.getComputedStyle = e => e.css || {display:'block',visibility:'visible',opacity:'1'};
    let result = eval(SCRIPT);
    if (result !== expected) throw Error(name + ': ' + result);
    print('OK ' + name);
}
const frame = {src:'https://www.google.com/recaptcha/api2/bframe',nodeType:1,parentElement:null,
    getBoundingClientRect:()=>({width:300,height:200})};
check('Waiting on form', '', null, 0);
check('Manual CAPTCHA visible', '', frame, 1);
frame.css = {display:'none',visibility:'visible',opacity:'1'};
check('Hidden challenge is not active', '', frame, 0);
check('Received message after CAPTCHA', 'Nous avons bien reçu votre demande', null, 2);
check('Received with whitespace', 'Votre demande\\n a été prise en compte', null, 2);
check('Validation error is not success', 'Veuillez résoudre la vérification', null, 0);
'''.replace('eval(SCRIPT)', 'eval(' + __import__('json').dumps(script) + ')'))
    subprocess.run(['qjs', str(test)], check=True)
