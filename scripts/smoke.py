#!/usr/bin/env python3
"""Local API walkthrough; leaves a uniquely named demo account/promotion/order for inspection."""
import hashlib,json,urllib.request,urllib.error,uuid
from datetime import datetime,timedelta,timezone
base='http://127.0.0.1:8070'
def request(method,path,data=None,status=200):
    raw=None if data is None else json.dumps(data).encode()
    req=urllib.request.Request(base+path,data=raw,method=method,headers={'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=15) as r: code,body=r.status,r.read()
    except urllib.error.HTTPError as e:code,body=e.code,e.read()
    assert code==status,(path,code,body.decode())
    return json.loads(body) if body else None
md5=lambda s:hashlib.md5(s.encode()).hexdigest()
name='restore-'+uuid.uuid4().hex[:8]
credentials={'userName':name,'password':md5('local-demo')}
user=request('POST','/account',credentials)['result']
assert 'password' not in user
request('POST','/account',credentials,400)
assert request('POST','/account/login',credentials)['result']==user
assert request('GET','/account/id/'+user['userId'])['result']==user
assert request('GET','/account/name/'+name)['result']==user
request('PUT','/account/password',{'userName':name,'oldPassword':credentials['password'],'newPassword':md5('local-demo-updated')})
request('POST','/account/login',credentials,400)
credentials['password']=md5('local-demo-updated')
request('POST','/account/login',credentials)
commodity=request('GET','/commodity/id/b859ee94-20f1-11ed-861d-0242ac120002')
now=datetime.now(timezone.utc).replace(tzinfo=None,microsecond=0)
p=request('POST','/promotion',{'promotionName':'Restore API verification','commodityId':commodity['commodityId'],'originalPrice':999,'promotionalPrice':899,'status':1,'startTime':(now-timedelta(days=1)).isoformat(),'endTime':(now+timedelta(days=1)).isoformat(),'totalStock':2,'availableStock':2,'lockStock':0,'imageUrl':'Iphone13'})
assert p['promotionName']=='Restore API verification' and p['promotionalPrice']==899
order=request('POST','/order',{'promotionId':p['promotionId'],'userId':user['userId'],'orderAmount':1,'promotionName':'client'})
assert order['orderStatus']==1 and order['orderAmount']==899
assert isinstance(order['orderNumber'],str) and int(order['orderNumber'])>2**53
assert request('GET','/order/id/'+order['orderNumber'])['orderNumber']==order['orderNumber']
stock=request('GET','/promotion/id/'+p['promotionId']);assert (stock['availableStock'],stock['lockStock'])==(1,1)
paid=request('PATCH','/order/pay',{'orderNumber':order['orderNumber'],'existStatus':1,'expectStatus':2})
assert paid['orderStatus']==2
request('PATCH','/order/pay',{'orderNumber':order['orderNumber'],'existStatus':1,'expectStatus':2})
stock=request('GET','/promotion/id/'+p['promotionId']);assert (stock['availableStock'],stock['lockStock'])==(1,0)
# Keeping test rows enables inspection; no course data is deleted.
print(json.dumps({'result':'PASS','checks':'registration, duplicate, login, lookups, password, promotion mapping, price, stock, order precision, payment idempotency','userName':name,'promotionId':p['promotionId'],'orderNumber':order['orderNumber'],'stock':[stock['availableStock'],stock['lockStock']]},indent=2))
