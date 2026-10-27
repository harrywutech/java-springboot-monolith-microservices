#!/usr/bin/env python3
"""Controlled failure exercise: stop only this checkout's managed Step 3 JVMs, restore in finally."""
import json,time
import services
from smoke import request,wait,report_path,BASE

def healthy(name):return request('GET','/v1/health/service/'+name+'?passing=true',base='http://127.0.0.1:18500')[1]
def main():
 evidence=json.loads(report_path('api-smoke.json').read_text());paid=next(o for o in evidence['paidOrders'] if o['userId']=='1');out={}
 try:
  services.stop(['order1b'])
  wait(lambda:healthy('step3-order-1'),lambda v:len(v)==1,seconds=35)
  # Discovery convergence + the configured two-second LoadBalancer cache.
  wait(lambda:request('GET','/order/id/'+paid['orderNumber'],mark=1),lambda r:r[0]==200 and r[2].get('X-Service-Instance')=='step3-order-1-8911',seconds=15)
  replies=[request('GET','/order/id/'+paid['orderNumber'],mark=1) for _ in range(12)]
  assert all(r[0]==200 and r[2]['X-Service-Instance']=='step3-order-1-8911' for r in replies)
  out['replicaStopped']='order1b';out['successfulReadsAfterConvergence']=12
 finally:services.start(['order1b'])
 wait(lambda:healthy('step3-order-1'),lambda v:len(v)==2,seconds=35)
 try:
  services.stop(['promotion'])
  wait(lambda:healthy('step3-promotion'),lambda v:len(v)==0,seconds=35)
  time.sleep(3)
  code,body,_=request('POST','/order',{'promotionId':evidence['promotionId'],'userId':'1'},mark=1)
  assert code==503,(code,body)
  out['promotionUnavailableStatus']=code
 finally:services.start(['promotion'])
 wait(lambda:healthy('step3-promotion'),lambda v:len(v)==1,seconds=35)
 wait(lambda:request('GET','/promotion/id/'+evidence['promotionId']),lambda r:r[0]==200 and r[1]['availableStock']==18,seconds=20)
 out['stockAfterPromotionRestart']=18;out['result']='PASS'
 report_path('failover.json').write_text(json.dumps(out,indent=2));print(json.dumps(out,indent=2),flush=True)
if __name__=='__main__':main()
