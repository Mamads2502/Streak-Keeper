"""Run the navigation script against DOM fixtures with QuickJS (qjs)."""
import json
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/fr/streakkeeper/app/FormNavigation.java').read_text()
script = source.split('static final String SCRIPT = """', 1)[1].split('""";', 1)[0].replace('__STREAK_SCROLL_DONE__', json.dumps('done'))
harness = r'''
function run(options = {}) {
    let queue = [], calls = [], listeners = {}, inserted = [], signals = [];
    globalThis.console = {log: value => signals.push({value, afterScroll:calls.length})};
    const element = (name, invalid = false) => ({
        name, nodeType:1, parentElement:null, value:'', disabled:false, style:{},
        validity:{valid:!invalid},
        getAttribute:()=>null, closest:()=>null, setAttribute:()=>{},
        getClientRects:()=>[{}], getBoundingClientRect:()=>({top:1000,width:200,height:48}),
        matches:()=>name==='submit', scrollIntoView:o=>calls.push({name,...o})
    });
    const submit = element('submit'), field = element('field', options.invalid);
    const error = element('error'), captcha = element('captcha'), consent = element('consent');
    const form = {
        querySelector:()=>options.solved ? {value:'token'} : null,
        querySelectorAll:s=>s==='input, select, textarea' ? [field]
            : s.includes('aria-invalid') ? (options.error ? [error] : [])
            : s.includes('/anchor') ? (options.captcha ? [captcha] : [])
            : options.missing ? [] : [submit]
    };
    if (options.hiddenInvalid) field.getClientRects=()=>[];
    globalThis.location = {origin:options.external ? 'https://example.org' : 'https://help.snapchat.com',pathname:'/hc/fr-fr/requests/new'};
    globalThis.document = {
        querySelector:()=>form,
        querySelectorAll:s=>s.includes('onetrust') && options.consent ? [consent] : [],
        getElementById:()=>null, createElement:()=>element('space'),
        body:{appendChild:x=>inserted.push(x)},
        addEventListener:(e,f)=>listeners[e]=f, removeEventListener:e=>delete listeners[e]
    };
    globalThis.window = {scrollY:0, innerHeight:600, matchMedia:()=>({matches:!!options.reduced}),
        addEventListener:()=>{}, removeEventListener:()=>{}};
    globalThis.getComputedStyle=()=>({display:'block',visibility:'visible',opacity:'1'});
    globalThis.setTimeout=f=>{queue.push(f);return queue.length;};
    globalThis.clearTimeout=()=>{};
    eval(SCRIPT.replace("__STREAK_CHECK_FIELDS__", options.manualFill ? "false" : "true"));
    if (options.touch && listeners.touchstart) listeners.touchstart();
    let limit=30;
    while(queue.length && limit--) queue.shift()();
    if (limit<=0) throw Error('Polling did not stop');
    return {calls, inserted, listeners, signals};
}
function check(name, options, expected) {
    const r=run(options);
    if (r.calls.length !== (expected ? 1 : 0) || (expected && r.calls[0].name!==expected))
        throw Error(name+': '+JSON.stringify(r.calls));
    if (Object.keys(r.listeners).length) throw Error(name+': listeners not cleaned up');
    if (expected==='submit' && r.inserted.length!==1) throw Error('Missing centering space');
    if (expected && r.calls[0].behavior!=='instant') throw Error('Scroll must be immediate');
    const expectedSignals = options.touch || options.external ? 0 : 1;
    if (r.signals.length !== expectedSignals || (expected && r.signals[0].afterScroll !== 1))
        throw Error(name+': incorrect completion signal '+JSON.stringify(r.signals));
    print('OK '+name);
}
check('Submit centered once',{},'submit');
check('Invalid field before submit',{invalid:true},'field');
check('Manual fill centers submit despite empty required fields',{invalid:true,manualFill:true},'submit');
check('Manual fill still prioritizes errors',{invalid:true,manualFill:true,error:true},'error');
check('Visible error before invalid field',{invalid:true,error:true},'error');
check('Consent before submit',{consent:true},'consent');
check('Unsolved CAPTCHA',{captcha:true},'captcha');
check('Solved CAPTCHA',{captcha:true,solved:true},'submit');
check('Hidden invalid field ignored',{invalid:true,hiddenInvalid:true},'submit');
check('Missing button stops polling',{missing:true},null);
check('User interaction cancels',{touch:true},null);
check('External page untouched',{external:true},null);
check('Reduced motion respected',{reduced:true},'submit');
'''
with tempfile.NamedTemporaryFile(mode='w', suffix='.js') as test:
    test.write('const SCRIPT = ' + json.dumps(script) + ';\n' + harness)
    test.flush()
    subprocess.run(['qjs', test.name], check=True)
