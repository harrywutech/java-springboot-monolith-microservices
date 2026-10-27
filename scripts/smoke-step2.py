#!/usr/bin/env python3
"""Real HTTP -> Redis -> RocketMQ -> MySQL workflow. Leaves UUID demo rows for inspection."""
import concurrent.futures, datetime, json, os, time, urllib.request, urllib.error, uuid
BASE=os.environ.get('STEP2_URL','http://127.0.0.1:8071')
def request(method,path,body=None):
    req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=15) as r: return r.status,json.loads(r.read() or b'null')
    except urllib.error.HTTPError as e:return e.code,json.loads(e.read() or b'null')
def await_value(fn,check,seconds=45):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        value=fn()
        if check(value):return value
        time.sleep(.4)
    raise AssertionError('Timed out: '+repr(value))
now=datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None)
name='Step2 MQ verification '+str(uuid.uuid4())[:8]
code,p=request('POST','/promotion',dict(promotionName=name,commodityId='b859ee94-20f1-11ed-861d-0242ac120002',originalPrice=100,promotionalPrice=80,status=1,startTime=(now-datetime.timedelta(days=1)).isoformat(),endTime=(now+datetime.timedelta(days=1)).isoformat(),totalStock=20,availableStock=20,lockStock=0,imageUrl='Iphone13'))
assert code==200,(code,p)
pid=p['promotionId']
def buy(_):return request('POST','/order',dict(promotionId=pid,userId='1',orderAmount=1,promotionName='untrusted'))
with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool: results=list(pool.map(buy,range(50)))
accepted=[r for code,r in results if code==202]
assert len(accepted)==20,results
assert sum(code==400 for code,r in results)==30,results
assert all(o['orderStatus']==0 and o['orderAmount']==80 and isinstance(o['orderNumber'],str) for o in accepted)
# Reheating during asynchronous processing must not manufacture another batch of stock.
assert request('POST','/promotion/preheat/id/'+pid)[1] is False
assert request('GET','/promotion/id/'+pid)[1]['availableStock']==0
for order in accepted:
    await_value(lambda:request('GET','/order/id/'+order['orderNumber']),lambda r:r[0]==200 and r[1]['orderStatus']==1)
paid=accepted[0]['orderNumber']
for _ in range(2):
    code,o=request('PATCH','/order/pay',dict(orderNumber=paid,existStatus=1,expectStatus=2))
    assert code==200 and o['orderStatus']==2,(code,o)
print('Real MQ accepted 20/50, orders materialized; one paid twice. Waiting for delayed expiry…',flush=True)
for order in accepted[1:]:
    await_value(lambda:request('GET','/order/id/'+order['orderNumber']),lambda r:r[0]==200 and r[1]['orderStatus']==3,seconds=100)
state=await_value(lambda:request('GET','/promotion/id/'+pid)[1],lambda p:p['availableStock']==19,seconds=45)
assert request('GET','/order/id/'+paid)[1]['orderStatus']==2
assert request('PATCH','/order/pay/id/'+accepted[1]['orderNumber'])[1]['orderStatus']==3
report=dict(base=BASE,promotionId=pid,submitted=50,accepted=20,rejected=30,paidOrder=paid,expiredOrders=[o['orderNumber'] for o in accepted[1:]],redisAvailableStock=state['availableStock'],result='PASS')
path='.runtime/verification/api-smoke.json'
os.makedirs(os.path.dirname(path),exist_ok=True)
with open(path,'w') as f:json.dump(report,f,indent=2)
print(json.dumps(report,indent=2))
