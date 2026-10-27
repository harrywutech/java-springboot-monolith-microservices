#!/usr/bin/env python3
"""Start/stop only this checkout's JVMs; PID records and logs stay in .runtime/."""
import argparse,json,os,signal,socket,subprocess,time,urllib.request,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RUNTIME=ROOT/'.runtime';RUNTIME.mkdir(exist_ok=True)
SERVICES={
 'user':('user-service',8811,{}), 'commodity':('commodity-service',8821,{}),
 'promotion':('promotion-service',8831,{}),
 'order1a':('order-service',8911,{'SHARD':'1','MACHINE':'1'}),
 'order1b':('order-service',8912,{'SHARD':'1','MACHINE':'2'}),
 'order2':('order-service',8921,{'SHARD':'2','MACHINE':'1'}),
 'frontend':('frontend-service',8804,{}), 'gateway':('gateway-service',8090,{})}

def jar(name):return ROOT/SERVICES[name][0]/'target'/(SERVICES[name][0]+'-1.0-SNAPSHOT.jar')
def alive(name):
 p=RUNTIME/(name+'.pid')
 if not p.exists():return False
 pid=int(p.read_text());result=subprocess.run(['ps','-p',str(pid),'-o','command='],text=True,capture_output=True)
 return str(jar(name)) in result.stdout

def health(port):
 try:
  with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=2) as response:return json.load(response).get('status')=='UP'
 except Exception:return False

def start(names):
 for name in names:
  if alive(name):continue
  path=jar(name)
  if not path.exists():raise SystemExit(f'Build first: {path}')
  _,port,extra=SERVICES[name]
  with socket.socket() as sock:
   if sock.connect_ex(('127.0.0.1',port))==0:raise SystemExit(f'Port {port} already occupied; no process was stopped')
  java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
  if not java:raise SystemExit('Java not found: set JAVA_HOME or add Java to PATH')
  env=dict(os.environ,PORT=str(port),**extra)
  with (RUNTIME/(name+'.log')).open('ab') as log:
   p=subprocess.Popen([str(java),'-Xms64m','-Xmx192m','-XX:ActiveProcessorCount=2','-Duser.timezone=UTC','-jar',str(path)],env=env,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  (RUNTIME/(name+'.pid')).write_text(str(p.pid))
  print(f'Started {name}: PID {p.pid}, port {port}',flush=True)
 deadline=time.monotonic()+120
 pending=set(names)
 while pending and time.monotonic()<deadline:
  for name in list(pending):
   if not alive(name):raise SystemExit(f'{name} exited; inspect {RUNTIME/(name+".log")}')
   if health(SERVICES[name][1]):pending.remove(name);print(f'Healthy: {name}',flush=True)
  if pending:time.sleep(1)
 if pending:raise SystemExit(f'Health timeout: {pending}; inspect .runtime logs')
 print('Gateway: http://127.0.0.1:8090/login.html | Consul: http://127.0.0.1:18500',flush=True)

def stop(names):
 for name in names:
  if alive(name):os.kill(int((RUNTIME/(name+'.pid')).read_text()),signal.SIGTERM)
 deadline=time.monotonic()+35
 while any(alive(n) for n in names) and time.monotonic()<deadline:time.sleep(.5)
 remaining=[n for n in names if alive(n)]
 if remaining:raise SystemExit(f'Still stopping {remaining}; no forced kill was issued')
 for name in names:(RUNTIME/(name+'.pid')).unlink(missing_ok=True)
 print('Stopped: '+', '.join(names))

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=['start','stop','status','assert-stopped']);parser.add_argument('names',nargs='*');args=parser.parse_args()
 names=args.names or list(SERVICES)
 if not set(names)<=set(SERVICES):parser.error('Unknown service')
 if args.action=='start':start(names)
 elif args.action=='stop':stop(names)
 elif args.action=='assert-stopped':
  if any(alive(n) for n in names):raise SystemExit('Step 3 JVMs are running. Use scripts/stop.sh before rebuilding jars, or services.py start to reuse the build.')
 else:
  for name in names:print(name,'running' if alive(name) else 'stopped',SERVICES[name][1])
