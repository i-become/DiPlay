/** Build static artwork from the user-supplied 2048x768 design.
 * Usage: node tools/cluster/extract-basic-art.cjs reference.jpg
 * No sample map, navigation, music, pressure, or speed is retained in production artwork.
 */
const sharp = require('sharp');
const fs = require('node:fs');
const path = require('node:path');
async function main() {
 const input = process.argv[2];
 const root = path.resolve(__dirname, '../..');
 const dir = path.join(root, 'common/src/main/res/drawable-nodpi');
 const {data, info} = await sharp(input).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 if(info.width!==2048||info.height!==768) throw Error('Expected the approved 2048x768 reference');
 const source=Buffer.from(data);
 function rgba(x,y){const i=(Math.max(0,Math.min(767,Math.round(y)))*2048+Math.max(0,Math.min(2047,Math.round(x))))*4;return source.subarray(i,i+4);}
 function set(x,y,p){data.set(p,(y*2048+x)*4);}
 // Remove the sample needle, including its intersection with the zero glyph.
 // Sample the same radial band on either side to preserve the dial's illumination.
 for(let y=443;y<523;y++)for(let x=1365;x<1476;x++){
  const r=Math.hypot(x-1591,y-373),a=Math.atan2(y-373,x-1591);
  if(r<254&&(Math.abs(y-(450+(1470-x)*.72))<5||(x>=1399&&x<=1418&&y>=485&&y<=511))){
   const before=rgba(1591+r*Math.cos(a-.17),373+r*Math.sin(a-.17));
   const after=rgba(1591+r*Math.cos(a+.17),373+r*Math.sin(a+.17));
   set(x,y,[0,1,2].map(c=>Math.round((before[c]+after[c])/2)).concat(255));
  }
 }
 // Strip illustrative scales. Production marks are rendered from the OEM ranges,
 // sharing the exact angle functions used by the live needles.
 for(const cx of [434,1591])for(let y=100;y<650;y++)for(let x=cx-264;x<=cx+264;x++){
  const r=Math.hypot(x-cx,y-373),a=Math.atan2(y-373,x-cx);
  const inner=cx===434?160:175, outer=268;
  if(r<inner||r>outer)continue;
  // A smooth navy/blue illumination replaces the entire illustrative scale band,
  // avoiding ghost digits or leftover ticks from the JPEG's anti-aliased edges.
  const highlight=Math.pow((1+Math.cos(a+Math.PI/3))/2,2);
  const radial=Math.pow((r-inner)/(outer-inner),2);
  const right=cx===1591;
  set(x,y,[Math.round(highlight*(right?5:0)),
   Math.round(14+highlight*(right?40:12)+radial*5),
   Math.round(34+highlight*(right?75:27)+radial*12),255]);
 }
 // The video aperture is cut after rearranging the artwork below.
 // Replace demo media without a rectangular patch behind the playback controls.
 // Below the circular well, continue the same radial dial shading used above.
 for(let y=204;y<615;y++)for(let x=1350;x<1832;x++){
  const r=Math.hypot(x-1591,y-373), a=Math.atan2(y-373,x-1591);
  if(r<171){
   set(x,y,[0,Math.round(16+8*r/200),Math.round(34+13*r/200),255]);
  }else if(y>=493&&r<240){
   const highlight=Math.pow((1+Math.cos(a+Math.PI/3))/2,2);
   const radial=Math.pow((r-175)/(268-175),2);
   set(x,y,[Math.round(highlight*5),Math.round(14+highlight*40+radial*5),
    Math.round(34+highlight*75+radial*12),255]);
  }
 }
 // Remove four example pressure readings and the fixed top-down car; keep the frame.
 for(let y=597;y<687;y++)for(let x=798;x<1210;x++){
  const l=rgba(770,y),r=rgba(1240,y),t=(x-770)/470;
  set(x,y,[0,1,2].map(c=>Math.round(l[c]*(1-t)+r[c]*t)).concat(255));
 }
 // Rebuild the empty ADAS road inside the reference's circular scene.
 for(let y=226;y<=522;y++)for(let x=279;x<=585;x++)if(Math.hypot(x-432,y-374)<147){
  const glow=Math.exp(-((x-432)**2/5000+(y-350)**2/13000));
  set(x,y,[0,Math.round(14+30*glow),Math.round(31+49*glow),255]);
 }
 // Move complete dials (including the lower light trails) 100px outward.
 // Stretch only the empty centre of the lower frame, never the circular instruments.
 const arranged=Buffer.from(data);
 for(let y=0;y<768;y++)for(let x=0;x<2048;x++){
  const sx=x<=630?x+100:x>=1395?x-100:730+(x-630)*(1295-730)/(1395-630);
  // Raise the footer boundary 8px, feathering the shift across the empty band.
  const sy=y<686?y:Math.min(767,y+Math.min(8,(y-686)*.5));
  const i=(Math.round(sy)*2048+Math.max(0,Math.min(2047,Math.round(sx))))*4;
  set(x,y,arranged.subarray(i,i+4));
 }
 // Video fills the middle behind opaque foreground dials. Only a four-pixel
 // top/bottom transition meets the frame; no broad tint covers the map sides.
 const backdrop=Buffer.from(data);
 const sample=(x,y)=>backdrop.subarray((y*2048+x)*4,(y*2048+x)*4+3);
 const smooth=t=>{t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);};
 for(let y=99;y<590;y++)for(let x=334;x<1691;x++){
  if(Math.hypot(x-334,y-373)<=277||Math.hypot(x-1691,y-373)<=277)continue;
  const top=1-smooth((y-99)/4),bottom=1-smooth((589-y)/4);
  const rgb=sample(x,top>bottom?98:590);
  set(x,y,[...rgb,Math.round(255*Math.max(top,bottom))]);
 }
 // Transparent RGB is cleared as well, so no example content survives asset inspection.
 for(let i=0;i<data.length;i+=4)if(data[i+3]===0)data.fill(0,i,i+3);
 await sharp(data,{raw:info}).webp({lossless:true}).toFile(path.join(dir,'cluster_basic_face.webp'));
 const cars=[['own',377,391,104,102,[[19,0],[78,0],[80,10],[96,18],[101,55],[95,94],[10,95],[2,55],[8,20],[20,12]]],
 ['lead',406,303,54,51,[[10,0],[44,0],[48,9],[53,25],[47,48],[4,48],[0,25],[5,9]]],
 ['top',975,598,57,90,[[12,0],[40,0],[47,10],[48,68],[40,87],[12,87],[5,68],[6,10]]]];
 for(const [name,left,top,width,height,poly] of cars){
  const mask=Buffer.from('<svg width="'+width+'" height="'+height+'"><polygon points="'+poly.map(p=>p.join(',')).join(' ')+'" fill="white"/></svg>');
  await sharp(input).extract({left,top,width,height}).ensureAlpha().composite([{input:mask,blend:'dest-in'}]).png().toFile(path.join(dir,'cluster_basic_car_'+name+'.png'));
 }
 const testDir=path.join(root,'common/src/test/resources/cluster');fs.mkdirSync(testDir,{recursive:true});
 await sharp(input).png().toFile(path.join(testDir,'basic-reference.png'));
}
main().catch(e=>{console.error(e);process.exitCode=1;});

