"""Build Windows sans shell auxiliaire, caches et sorties exclusivement sous link/."""
from pathlib import Path
import os,json,subprocess,re,sys,zipfile,hashlib
ROOT=Path(__file__).resolve().parents[1]
JDK=Path(os.environ.get('JDK','C:/Users/<utilisateur>/.jdks/openjdk-25.0.1'))
LIBS=Path(os.environ.get('MC_LIBS','E:/multimc/MultiMC/libraries'))
CLIENT=LIBS/'com/mojang/minecraft/26.3/minecraft-26.3-client.jar'
META=Path(os.environ.get('MC_META','E:/multimc/MultiMC/meta/net.minecraft/26.3.json'))
BUILD=ROOT/'build/l3'
def run(args, **kw):
    temp=BUILD/'tmp';temp.mkdir(parents=True,exist_ok=True)
    env=os.environ.copy(); env['TEMP']=env['TMP']=str(temp)
    kw.setdefault('env',env)
    captured=kw.get('capture_output',False)
    kw.setdefault('capture_output',True); kw.setdefault('text',True); kw.setdefault('encoding','utf8'); kw.setdefault('errors','replace')
    with subprocess.Popen([str(x) for x in args],cwd=ROOT,creationflags=0x08000000,
                          stdout=subprocess.PIPE,stderr=subprocess.PIPE,**{k:v for k,v in kw.items() if k!='capture_output'}) as child:
        with (BUILD/'launched-pids.jsonl').open('a',encoding='utf8') as f:
            f.write(json.dumps({'pid':child.pid,'command':[str(x) for x in args]})+'\n')
        stdout,stderr=child.communicate()
        p=subprocess.CompletedProcess(args,child.returncode,stdout,stderr)
    if not captured or p.returncode:
        print(p.stdout, end='',flush=True); print(p.stderr,end='',flush=True)
        with (ROOT/'test/blocks-evidence/run.log').open('a',encoding='utf8') as log:
            log.write(p.stdout);log.write(p.stderr)
    if p.returncode: raise SystemExit(p.returncode)
    return p
def dependencies():
    paths=[CLIENT,LIBS/'net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar',LIBS/'net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar']
    for entry in json.loads(META.read_text())['libraries']:
        g,a,v=entry['name'].split(':')[:3]
        p=LIBS/g.replace('.','/')/a/v/(a+'-'+v+'.jar')
        if p.exists(): paths.append(p)
    paths+=list((LIBS/'org/ow2/asm').glob('*/9.10.1/*.jar'))
    return list(dict.fromkeys(paths))
def compile_java(files,out,cp):
    out.mkdir(parents=True,exist_ok=True)
    args=['--release','25','-proc:none','-encoding','UTF-8','-nowarn','-cp',cp,'-d',str(out)]+[str(p) for p in files]
    argfile=BUILD/'javac.args'
    argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf8')
    run([JDK/'bin/javac.exe','@'+str(argfile)])
def javap(cls):
    return run([JDK/'bin/javap.exe','-p','-s','-c','-classpath',CLIENT,cls],capture_output=True,text=True,encoding='utf8').stdout
def targets():
    cache={}; n=0
    for line in (ROOT/'test/mixin_targets.txt').read_text(encoding='utf8').splitlines():
        if not line or line.startswith('#'): continue
        kind,cls,name,desc=line.split('|'); n+=1
        if cls not in cache: cache[cls]=javap(cls)
        out=cache[cls]; good=False
        if kind in ('method','field'):
            target=cls if name=='<init>' else name
            if name=='<clinit>': pattern=r'static \{\};\s+descriptor: '+re.escape(desc)
            elif kind=='method': pattern=r' '+re.escape(target)+r'\([^\n]*\)[^\n]*\n\s+descriptor: '+re.escape(desc)
            else: pattern=r' '+re.escape(name)+r';\s+descriptor: '+re.escape(desc)
            good=bool(re.search(pattern,out))
        elif kind=='putfield':
            for block in re.split(r'\n(?=  \S)',out):
                if ' '+name+'(' in block and 'putfield' in block and 'Field '+desc in block: good=True
        elif kind=='invoke':
            target=cls if name=='<init>' else name
            for block in re.split(r'\n(?=  \S)',out):
                if (' '+target+'(' in block or name=='<clinit>' and 'static {};' in block) and desc in block: good=True
        if not good: raise RuntimeError('Cible absente : '+line)
    evidence=ROOT/'test/blocks-evidence'; evidence.mkdir(exist_ok=True)
    for cls,out in cache.items(): (evidence/(cls+'.txt')).write_text(out,encoding='utf8')
    print('Cibles javap : %d/%d'% (n,n),flush=True)
    return n
def main():
    os.chdir(ROOT); BUILD.mkdir(parents=True,exist_ok=True)
    (ROOT/'test/blocks-evidence').mkdir(exist_ok=True)
    (ROOT/'test/blocks-evidence/run.log').write_text('',encoding='utf8')
    version=json.loads((ROOT/'src/main/resources/fabric.mod.json').read_text())['version']
    cp=os.pathsep.join(str(p) for p in dependencies())
    classes=BUILD/'classes'; tests=BUILD/'tests'
    for d in [classes,tests]:
        (d/'infinitylink/core').mkdir(parents=True,exist_ok=True)
        (d/'infinitylink/core/version.txt').write_text(version.split('+')[0])
    quick='--quick' in sys.argv
    pure=['CodecTest','TabsCodecTest','AssetsCodecTest','AssetsClientTest','LodMesherTest','LodPoolTest','BlocksViewTest','Armures3dTest','FormesTest']
    compile_java(list((ROOT/'src/main/java/infinitylink/core').rglob('*.java'))+[ROOT/'test'/ (n+'.java') for n in pure],tests,cp)
    for name in (['BlocksViewTest'] if quick else pure): run([JDK/'bin/java.exe','-Dstdout.encoding=UTF-8','-cp',tests,name])
    if os.environ.get('LOD_REPLAY'):
        compile_java([ROOT/'test/LodReplayTest.java'],tests,cp+os.pathsep+str(tests))
        run([JDK/'bin/java.exe','-Xmx2g','-Dstdout.encoding=UTF-8','-cp',tests,'LodReplayTest',os.environ['LOD_REPLAY']])
    if not quick: targets()
    compile_java(list((ROOT/'src/main/java').rglob('*.java')),classes,cp)
    full=cp+os.pathsep+str(classes)+os.pathsep+str(tests)
    compile_java([ROOT/'test/TabsE2E.java',ROOT/'test/BlocksMinecraftCodecTest.java',ROOT/'test/BlocksMixinLauncher.java'],tests,full)
    for name,args in ([] if quick else [('TabsE2E',['test/ref'])]):
        run([JDK/'bin/java.exe','-Xmx2g','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',full,name]+args)
    jar=ROOT/'dist'/('infinitylink-'+version+'.jar'); jar.parent.mkdir(exist_ok=True)
    run([JDK/'bin/jar.exe','--create','--file',jar,'-C',classes,'.','-C',ROOT/'src/main/resources','.'])
    game=BUILD/'headless';game.mkdir(exist_ok=True)
    for flags in [[],['-Dinfinitylink.blocks=off']]:
        run([JDK/'bin/java.exe','-Xmx2g','-Djava.awt.headless=true','-Dfabric.skipMcProvider=false',
            '-Dfabric.addMods='+str(jar),'-Dfabric.log.disableAnsi=true','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8']+flags+
            ['-cp',cp+os.pathsep+str(tests),'BlocksMixinLauncher',game,tests])
    print(str(jar),jar.stat().st_size,'octets SHA256',hashlib.sha256(jar.read_bytes()).hexdigest())
if __name__=='__main__': main()
