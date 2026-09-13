from pathlib import Path
import json, subprocess, time, hashlib

out=Path(__file__).parent
manifest=json.loads((out/'manifest.json').read_text())
result={'image':manifest['image'],'sourceRevision':manifest['sourceRevision'],'runs':[],
        'qualification':'Exact new image classes and JRE; overridden entrypoint runs bounded mock hosted lifecycle probe; not full HTTP/native qualification','limits':'Compose-equivalent 1GiB total,1CPU,256PIDs; image JRE/65% heap; network none; actual application classes/libraries extracted from this image. Mock publication/observation ports; no HTTP/native tracing.'}
for shape in ['small','large']:
 name=f'es-capacity-package-image-{shape}-20260912'
 command=['docker','run','--name',name,'--network','none','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges',
          '--memory','1g','--memory-swap','1g','--cpus','1.0','--pids-limit','256','--ulimit','core=0',
          '--tmpfs','/tmp:rw,noexec,nosuid,size=128m','--mount',f'type=bind,src={out}/bundle,dst=/probe,readonly',
          '--entrypoint','java',manifest['image'],'-XX:MaxRAMPercentage=65','-XX:+ExitOnOutOfMemoryError',
          '-Djava.io.tmpdir=/tmp','-Dorg.sqlite.lib.path=/opt/studio/native','-cp','/probe/probe:/probe/classes:/probe/lib/*',
          '-Des.probe.large='+str(shape=='large').lower(),'CapacityAssemblyShapeProbe']
 run={'shape':shape,'command':command,'timedOut':False};start=time.monotonic()
 try:
  with (out/f'{shape}.log').open('w') as log:
   process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
   while process.poll() is None:
    if time.monotonic()-start>300:
     run['timedOut']=True
     subprocess.run(['docker','stop','--time','5',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=15)
     process.wait(timeout=15);break
    time.sleep(1)
   run['exit']=process.wait()
  inspected=subprocess.run(['docker','inspect',name,'--format','{{json .State}}'],capture_output=True,text=True)
  run['inspectExit']=inspected.returncode
  if inspected.returncode==0:
   state=json.loads(inspected.stdout);run['containerExit']=state['ExitCode'];run['oomKilled']=state['OOMKilled'];run['running']=state['Running']
 finally:
  if 'process' in locals() and process.poll() is None:
   subprocess.run(['docker','stop','--time','5',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=15);process.wait(timeout=15)
  removed=subprocess.run(['docker','rm',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  run['containerRemoved']=removed.returncode==0
  run['seconds']=round(time.monotonic()-start,3)
  run['logSha256']=hashlib.sha256((out/f'{shape}.log').read_bytes()).hexdigest()
  result['runs'].append(run);(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
  print(json.dumps({k:v for k,v in run.items() if k!='command'}),flush=True)
 if run.get('exit')!=0:break
result['bundleUnchanged']=all(hashlib.sha256((out/'bundle'/p).read_bytes()).hexdigest()==h for p,h in manifest['bundleHashes'].items())
(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
def accepted(run):
 return (run.get('exit')==0 and run.get('containerExit')==0 and run.get('oomKilled') is False
         and run.get('running') is False and run.get('containerRemoved') is True
         and run.get('inspectExit')==0 and run.get('timedOut') is False)
assert result['bundleUnchanged']
assert len(result['runs'])==2 and all(accepted(r) for r in result['runs'])
