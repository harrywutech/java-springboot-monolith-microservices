#!/usr/bin/env python3
"""Real Gateway/Consul/Feign/MQ/shard verification. Leaves a UUID demo promotion for inspection."""
import concurrent.futures,datetime,hashlib,json,os,subprocess,time,urllib.request,urllib.error,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=os.environ.get('STEP3_URL','http://127.0.0.1:8090')
def request(method,path,body=None,mark=None,base=BASE):
 headers={'Content-Type':'application/json'}
 if mark is not None:headers['mark']=str(mark)
 req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),method=method,headers=headers)
 try:
  with urllib.request.urlopen(req,timeout=15) as r:
   raw=r.read();return r.status,json.loads(raw or b'null'),dict(r.headers)
 except urllib.error.HTTPError as e:
  raw=e.read()
  try:body=json.loads(raw or b'null')
  except ValueError:body=raw.decode(errors='replace')
  return e.code,body,dict(e.headers)
def wait(fn,check,seconds=100):
 end=time.monotonic()+seconds;value=None
 while time.monotonic()<end:
  value=fn()
  if check(value):return value
  time.sleep(.5)
 raise AssertionError('Timed out: '+repr(value))
def sql(query):
 return subprocess.check_output(['docker','compose','-f',str(ROOT/'compose.yaml'),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B'],input=query,text=True).strip()
def report_path(name):
 p=ROOT/'.runtime/verification'/name;p.parent.mkdir(parents=True,exist_ok=True);return p

def main():
 logins={}
 for name,shard in [('test',1),('test2',2)]:
  code,body,headers=request('POST','/account/login',{'userName':name,'password':hashlib.md5(b'test').hexdigest()})
  assert code==200 and headers['mark']==str(shard),(code,body,headers)
  logins[str(shard)]=body['result']['userId']
 assert request('GET','/order/id/1')[0]==400
 assert request('POST','/internal/reservations',{})[0]==404
 assert request('POST','/order',{'promotionId':'unused','userId':'2'},mark=1)[0]==400
 now=datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None)
 code,p,_=request('POST','/promotion',dict(promotionName='Step3 verification '+str(uuid.uuid4())[:8],commodityId='b859ee94-20f1-11ed-861d-0242ac120002',originalPrice=100,promotionalPrice=80,status=1,startTime=(now-datetime.timedelta(days=1)).isoformat(),endTime=(now+datetime.timedelta(days=1)).isoformat(),totalStock=20,availableStock=20,lockStock=0,imageUrl='Iphone13'))
 assert code==200,(code,p)
 pid=p['promotionId']
 def buy(i):
  shard=i%2+1
  return request('POST','/order',dict(promotionId=pid,userId=logins[str(shard)],promotionName='tampered',orderAmount=1),mark=shard)
 with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:results=list(pool.map(buy,range(50)))
 accepted=[o for code,o,_ in results if code==202]
 assert len(accepted)==20,results
 assert sum(code==400 for code,_,_ in results)==30,results
 assert len({o['orderNumber'] for o in accepted})==20
 assert all(o['orderAmount']==80 and o['orderStatus']==0 for o in accepted)
 assert {o['userId'] for o in accepted}=={'1','2'}
 assert request('POST','/promotion/preheat/id/'+pid)[1] is False
 assert request('GET','/promotion/id/'+pid)[1]['availableStock']==0
 for o in accepted:
  wait(lambda:request('GET','/order/id/'+o['orderNumber'],mark=o['userId']),lambda r:r[0]==200 and r[1]['orderStatus']==1)
 paid=[next(o for o in accepted if o['userId']==user) for user in ['1','2']]
 for o in paid:
  for _ in range(2):assert request('PATCH','/order/pay/id/'+o['orderNumber'],mark=o['userId'])[1]['orderStatus']==2
  wrong=3-int(o['userId']);assert request('GET','/order/id/'+o['orderNumber'],mark=wrong)[0]==400
 # Repeating the same internal HTTP reservation must not reserve again or change the snapshot.
 o=paid[0]
 code,repeated,_=request('POST','/internal/reservations',dict(orderNumber=o['orderNumber'],userId=o['userId'],promotionId=pid,orderAmount=1),base='http://127.0.0.1:8831')
 assert code==200 and repeated['orderAmount']==80
 assert request('GET','/promotion/id/'+pid)[1]['availableStock']==0
 instances=[request('GET','/order/id/'+paid[0]['orderNumber'],mark=1)[2]['X-Service-Instance'] for _ in range(10)]
 assert set(instances)=={'step3-order-1-8911','step3-order-1-8912'},instances
 print('Gateway + Feign + MQ: accepted 20/50 across both shards; paid one per shard; both shard-1 replicas served requests.',flush=True)
 expired=[o for o in accepted if o not in paid]
 for o in expired:wait(lambda:request('GET','/order/id/'+o['orderNumber'],mark=o['userId']),lambda r:r[0]==200 and r[1]['orderStatus']==3)
 wait(lambda:request('GET','/promotion/id/'+pid)[1],lambda p:p['availableStock']==18)
 assert request('PATCH','/order/pay/id/'+expired[0]['orderNumber'],mark=expired[0]['userId'])[1]['orderStatus']==3
 wait(lambda:sql(f"SELECT available_stock,lock_stock FROM step3_promotion.promotion WHERE promotion_id='{pid}';"),lambda s:s=='18\t0')
 evidence={}
 for shard in [1,2]:
  expected=sum(o['userId']==str(shard) for o in accepted)
  counts=sql(f"SELECT COUNT(*), SUM(user_id<>'{shard}') FROM step3_order{shard}.`order` WHERE promotion_id='{pid}';")
  assert counts==f'{expected}\t0',counts
  evidence[f'order{shard}']=counts
 evidence['stock']=sql(f"SELECT total_stock,available_stock,lock_stock FROM step3_promotion.promotion WHERE promotion_id='{pid}';")
 evidence['logs']=sql(f"SELECT operation_name,count(*) FROM step3_promotion.promotion_log WHERE promotion_id='{pid}' GROUP BY operation_name ORDER BY operation_name;")
 assert evidence['logs']=='DEDUCT_STOCK\t2\nLOCK_STOCK\t20\nREVERT_STOCK\t18',evidence
 for shard in [1,2]:wait(lambda:sql(f'SELECT COUNT(*) FROM step3_order{shard}.mq_outbox WHERE sent_at IS NULL;'),lambda v:v=='0')
 result=dict(result='PASS',base=BASE,promotionId=pid,submitted=50,accepted=20,rejected=30,paidOrders=paid,expiredOrders=[o['orderNumber'] for o in expired],loadBalancedInstances=instances,databaseEvidence=evidence)
 report_path('api-smoke.json').write_text(json.dumps(result,indent=2))
 report_path('database-check.txt').write_text(json.dumps(evidence,indent=2)+'\n')
 print(json.dumps(result,indent=2),flush=True)
if __name__=='__main__':main()
