import {readFileSync} from 'node:fs';
export const clean = s => s.replace(/<[^>]+>/g, '').replace(/&nbsp;|\u00a0/g, ' ').replace(/&amp;/g, '&').replace(/&#39;/g, "'").replace(/&quot;/g, '"').replace(/\s+/g,' ').trim();
export function parse(subject) {
  return readFileSync(`data/${subject}-source.html`,'utf8').split('<div class="courseblock">').slice(1).map(block=>({
    code:clean(block.match(/detail-coursecode[^>]*>(.*?)<\/span>/s)?.[1]||''),
    title:clean(block.match(/detail-title[^>]*>(.*?)<\/span>/s)?.[1]||''),
    credits:Number(clean(block.match(/detail-hours_html[^>]*>(.*?)<\/span>/s)?.[1]||'').match(/\d+/)?.[0]),
    notes:[...block.matchAll(/<p class="courseblockextra noindent">(.*?)<\/p>/gs)].map(m=>clean(m[1]))
  }));
}
if (process.argv[2]) {
 const codes=process.argv.slice(3);
 console.log(JSON.stringify(parse(process.argv[2]).filter(c=>!codes.length||codes.includes(c.code)),null,2));
}
