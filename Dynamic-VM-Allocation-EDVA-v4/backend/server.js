'use strict';
const http = require('node:http');
const { URL } = require('node:url');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const ROOT = path.resolve(__dirname, '..');
const PORT = Number(process.env.PORT || 3000);
const MAX_BODY = 32 * 1024;
const ALLOWED_ORIGIN = process.env.ALLOWED_ORIGIN || '*';
const ADMIN_API_KEY = process.env.ADMIN_API_KEY || '';
let javaReady = false;
let javaCompileError = '';

function send(res, status, data) {
  const body = Buffer.from(JSON.stringify(data));
  res.writeHead(status, {'Content-Type':'application/json; charset=utf-8','Content-Length':body.length,'Cache-Control':'no-store'});
  res.end(body);
}
function cors(req, res) {
  const origin = req.headers.origin || '';
  res.setHeader('Access-Control-Allow-Origin', ALLOWED_ORIGIN === '*' ? '*' : (origin === ALLOWED_ORIGIN ? origin : ALLOWED_ORIGIN));
  res.setHeader('Vary','Origin');
  res.setHeader('Access-Control-Allow-Headers','Content-Type, X-Admin-Key');
  res.setHeader('Access-Control-Allow-Methods','GET, POST, DELETE, OPTIONS');
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = ''; req.on('data', c => { data += c; if (data.length > MAX_BODY) { reject(new Error('Request body too large')); req.destroy(); } });
    req.on('end', () => { if (!data) return resolve({}); try { resolve(JSON.parse(data)); } catch { reject(new Error('Body must be valid JSON')); } });
    req.on('error', reject);
  });
}
function requireAdmin(req, res) {
  if (!ADMIN_API_KEY) { send(res, 503, {error:'Set ADMIN_API_KEY on the backend before enabling cloud mutations.'}); return false; }
  if (req.headers['x-admin-key'] !== ADMIN_API_KEY) { send(res, 401, {error:'Missing or invalid X-Admin-Key.'}); return false; }
  return true;
}
function run(cmd, args, opts={}) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, {cwd:ROOT, env:process.env, ...opts}); let stdout='', stderr='';
    p.stdout?.on('data', d => stdout += d); p.stderr?.on('data', d => stderr += d);
    p.on('error', reject); p.on('close', code => code === 0 ? resolve({stdout,stderr}) : reject(new Error(`${cmd} exited ${code}: ${stderr || stdout}`)));
  });
}
async function compileJava() {
  try { await run('javac',['-d','out',...fs.readdirSync(path.join(ROOT,'src')).filter(f=>f.endsWith('.java')).map(f=>path.join('src',f))]); javaReady=true; javaCompileError=''; }
  catch(e) { javaReady=false; javaCompileError=e.message; console.error('Java compile failed:',e.message); }
}
function parseComparison(output) {
  const lines = output.split(/\r?\n/); const header = lines.findIndex(l=>l.includes('Metric') && l.includes('Paper') && l.includes('EDVA'));
  const metrics = [];
  if (header >= 0) for (const line of lines.slice(header+1)) {
    const m = line.match(/^\s*(.*?)\s{2,}([\d.]+)\s{2,}([\d.]+)\s*$/);
    if (m) metrics.push({metric:m[1].trim(), paper:Number(m[2]), edva:Number(m[3])});
  }
  return metrics;
}
const env = () => ({
  authUrl: process.env.OS_AUTH_URL, username: process.env.OS_USERNAME, password: process.env.OS_PASSWORD,
  projectName: process.env.OS_PROJECT_NAME, userDomain: process.env.OS_USER_DOMAIN_NAME || 'Default',
  projectDomain: process.env.OS_PROJECT_DOMAIN_NAME || 'Default', region: process.env.OS_REGION_NAME,
  imageId: process.env.OS_IMAGE_ID, flavorId: process.env.OS_FLAVOR_ID, networkId: process.env.OS_NETWORK_ID,
  keyName: process.env.OS_KEY_NAME
});
function openstackConfigured() { const e=env(); return !!(e.authUrl&&e.username&&e.password&&e.projectName&&e.imageId&&e.flavorId&&e.networkId); }
async function openstackToken() {
  const e=env(); if(!openstackConfigured()) throw new Error('OpenStack is not configured. Set OS_AUTH_URL, OS_USERNAME, OS_PASSWORD, OS_PROJECT_NAME, OS_IMAGE_ID, OS_FLAVOR_ID and OS_NETWORK_ID in backend environment variables.');
  const authUrl=e.authUrl.replace(/\/$/,'');
  const r=await fetch(authUrl.replace(/\/v3\/?$/,'')+'/v3/auth/tokens',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({auth:{identity:{methods:['password'],password:{user:{name:e.username,domain:{name:e.userDomain},password:e.password}}},scope:{project:{name:e.projectName,domain:{name:e.projectDomain}}}}})});
  if(!r.ok) throw new Error(`Keystone authentication failed (${r.status}): ${(await r.text()).slice(0,400)}`);
  const token=r.headers.get('x-subject-token'); const body=await r.json();
  if(!token) throw new Error('Keystone did not return X-Subject-Token. Check identity URL/API version.');
  const catalog=body.token?.catalog||[]; const svc=catalog.find(s=>s.type==='compute');
  if(!svc) throw new Error('No compute service found in Keystone service catalog.');
  let ep=(svc.endpoints||[]).find(x=>(!e.region||x.region===e.region)&&x.interface==='public') || (svc.endpoints||[]).find(x=>x.interface==='public') || (svc.endpoints||[])[0];
  if(!ep?.url) throw new Error('No Nova endpoint found in Keystone service catalog.');
  return {token,computeUrl:ep.url.replace(/\/$/,'')};
}
async function nova(method, route, body) {
  const {token,computeUrl}=await openstackToken();
  const r=await fetch(computeUrl+route,{method,headers:{'X-Auth-Token':token,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
  const text=await r.text(); let data={}; try{data=text?JSON.parse(text):{};}catch{data={raw:text};}
  if(!r.ok) throw new Error(`Nova API failed (${r.status}): ${JSON.stringify(data).slice(0,600)}`);
  return data;
}
const server=http.createServer(async(req,res)=>{
  cors(req,res);
  if(req.method==='OPTIONS'){res.writeHead(204);return res.end();}
  const url=new URL(req.url,`http://${req.headers.host||'localhost'}`);
  try {
    if(req.method==='GET' && url.pathname==='/api/health') return send(res,200,{ok:true,service:'EDVA API',javaReady,javaCompileError:javaCompileError||null,openstackConfigured:openstackConfigured(),provider:openstackConfigured()?'openstack':'not-configured'});
    if(req.method==='GET' && url.pathname==='/api/scheduler/status') return send(res,200,{javaReady,javaCompileError:javaCompileError||null});
    if(req.method==='POST' && url.pathname==='/api/scheduler/run') {
      if(!javaReady) return send(res,503,{error:'Java scheduler is not ready.',details:javaCompileError});
      const body=await readBody(req); const main=body.mode==='single'?'Main':'Comparison';
      const result=await run('java',['-cp','out',main],{timeout:undefined});
      return send(res,200,{ok:true,mode:main,metrics:parseComparison(result.stdout),output:result.stdout.slice(-18000)});
    }
    if(url.pathname.startsWith('/api/openstack/')) {
      if(!openstackConfigured()) return send(res,503,{error:'Real OpenStack is not configured on this backend. Configure the OS_* environment variables first.'});
      if(req.method==='GET' && url.pathname==='/api/openstack/servers') {
        const data=await nova('GET','/servers/detail'); return send(res,200,{ok:true,servers:data.servers||[]});
      }
      if(req.method==='POST' && url.pathname==='/api/openstack/servers') {
        if(!requireAdmin(req,res)) return;
        const body=await readBody(req); const name=String(body.name||'edva-vm').trim().slice(0,64);
        if(!/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(name)) return send(res,400,{error:'Name must use letters, numbers, dot, underscore, or hyphen.'});
        const e=env(); const payload={server:{name,imageRef:e.imageId,flavorRef:e.flavorId,networks:[{uuid:e.networkId}]}};
        if(e.keyName) payload.server.key_name=e.keyName;
        const data=await nova('POST','/servers',payload); return send(res,202,{ok:true,server:data.server,note:'Nova accepted the server request; BUILDING may take time before ACTIVE.'});
      }
      const del=url.pathname.match(/^\/api\/openstack\/servers\/([a-f0-9-]+)$/i);
      if(req.method==='DELETE'&&del){if(!requireAdmin(req,res))return;await nova('DELETE','/servers/'+del[1]);return send(res,200,{ok:true,deletedId:del[1]});}
    }
    send(res,404,{error:'Not found'});
  } catch(e) { console.error(e); send(res,500,{error:e.message||'Internal server error'}); }
});
server.listen(PORT,'0.0.0.0',async()=>{console.log(`EDVA API listening on ${PORT}`); await compileJava(); console.log('Java ready:',javaReady);});
