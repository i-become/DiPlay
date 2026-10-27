/** Prepare the supplied 2048x768 Sport artwork for native live composition.
 * Static framing is retained pixel-for-pixel; example data and the OEM speed card
 * are removed. The reference itself is test-only, never an APK fallback map.
 * Usage: node tools/cluster/extract-sport-art.cjs reference.png
 */
const sharp = require('sharp');
const fs = require('node:fs');
const path = require('node:path');
async function main() {
 const input=process.argv[2],root=path.resolve(__dirname,'../..');
 const dir=path.join(root,'common/src/main/res/drawable-nodpi');
 const {data,info}=await sharp(input).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 if(info.width!==2048||info.height!==768)throw Error('Expected 2048x768');
 const original=Buffer.from(data);
 const sample=(buf,x,y)=>{const i=(Math.max(0,Math.min(767,Math.round(y)))*2048+Math.max(0,Math.min(2047,Math.round(x))))*4;return buf.subarray(i,i+4);};
 const put=(x,y,c)=>data.set(c,(y*2048+x)*4);
 const smooth=t=>{t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);};
 // Delete the complete speed card, including its bevel, without a rectangular cover.
 // Continue the almost-black upper-left scene from its unobstructed boundary rows.
 for(let y=110;y<=289;y++)for(let x=55;x<=600;x++){
  const t=(y-110)/179;
  const edge=smooth(Math.min((x-55)/20,(600-x)/20,(y-110)/19,(289-y)/20));
  const existing=sample(original,x,y);
  const fill=[2+Math.pow(t,5)*3,1,2];
  // Smooth continuation, without a rectangular patch or residual card silhouette.
  put(x,y,fill.map((c,i)=>Math.round(existing[i]*(1-edge)+c*edge)).concat(255));
 }
 // Rebuild the scale wells, retaining both original luminous red rims.
 // The actual HS5 scale labels/marks are baked by the native renderer.
 for(let y=52;y<601;y++)for(let x=711;x<1338;x++){
  const dx=x-1024,dy=y-358,r=Math.hypot(dx,dy),a=Math.atan2(dy,dx);
  if(r>=208&&r<298){
   const radial=(r-215)/83, stripe=(1+Math.sin(a*510))*.7;
   const light=Math.pow((1+Math.cos(a+2.2))/2,4);
   put(x,y,[Math.round(5+3*radial+stripe+8*light),Math.round(5+3*radial+stripe),Math.round(6+3*radial+stripe),255]);
  }
  if(r<195){
   const v=Math.round(3+(r-164)*.08);
   put(x,y,[v+1,v,v,255]);
  }
  // Empty media well: preserve its circular lower hairline, remove demo artwork/text.
  if(r<149)put(x,y,[2,2,3,255]);
 }
 // Clear the illustrative outer unit below the dial.
 for(let y=566;y<=600;y++)for(let x=930;x<1120;x++){
  const edge=smooth(Math.min((y-566)/5,(600-y)/4,(x-930)/18,(1120-x)/20));
  const c=sample(data,x,y),v=2;
  put(x,y,[0,1,2].map(i=>Math.round(c[i]*(1-edge)+v*edge)).concat(255));
 }
 // No example tire readings: retain the slim red separator below each reading.
 for(const [x0,x1,y0,y1]of [[880,984,620,652],[1064,1176,620,652],[880,984,664,696],[1064,1176,664,696]]){
  for(let y=y0;y<=y1;y++)for(let x=x0;x<x1;x++){
   const t=(x-x0)/(x1-x0),a=sample(original,x0-2,y),b=sample(original,x1+2,y);
   put(x,y,[0,1,2].map(c=>Math.round(a[c]*(1-t)+b[c]*t)).concat(255));
  }
 }
 // Lead vehicle appears only on a real detection. Its sample road is reconstructed
 // from neighbouring lane interior pixels. The fixed own-car artwork remains.
 for(let y=350;y<407;y++)for(let x=320;x<395;x++){
  const t=(x-320)/75,a=sample(original,319,y),b=sample(original,396,y);
  put(x,y,[0,1,2].map(c=>Math.round(a[c]*(1-t)+b[c]*t)).concat(255));
 }
 // A 3:2 right-side video aperture, with the original outer red frame preserved.
 // Match the surrounding black/red frame at each edge, then feather to live video.
 for(let y=140;y<632;y++)for(let x=1310;x<2048;x++){
  if(Math.hypot(x-1024,y-358)<328)continue;
  const curve=Math.pow(Math.max(0,(x-1900)/148),2);
  const top=y-(140+37*curve),bottom=(627-83*curve)-y;
  if(top<0||bottom<0)continue;
  const alpha=Math.max(1-smooth(top/64),1-smooth(bottom/56),1-smooth((2047-x)/28));
  put(x,y,[2,3,5,Math.round(255*alpha)]);
 }
 // Erase compass/example-map leftovers outside the rectangular aperture as well.
 // Top boundary was sampled before clearing; it contains only the map's dark rim.
 for(let i=0;i<data.length;i+=4)if(data[i+3]===0)data.fill(0,i,i+3);
 await sharp(data,{raw:info}).png().toFile(path.join(dir,'cluster_sport_face.png'));
 const mask=Buffer.from('<svg width="75" height="57"><path d="M14 3 L57 3 L65 16 L73 22 L72 43 L62 51 L12 51 L3 43 L3 22 L10 16Z" fill="white"/></svg>');
 await sharp(input).extract({left:320,top:350,width:75,height:57}).ensureAlpha().composite([{input:mask,blend:'dest-in'}]).png().toFile(path.join(dir,'cluster_sport_car_lead.png'));
 const testDir=path.join(root,'common/src/test/resources/cluster');fs.mkdirSync(testDir,{recursive:true});
 await sharp(input).png().toFile(path.join(testDir,'sport-reference.png'));
}
main().catch(e=>{console.error(e);process.exitCode=1;});

