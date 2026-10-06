const fs=require('fs'),vm=require('vm'),assert=require('assert');
const script=fs.readFileSync('client/android/res/raw/timetable_extract.js','utf8');
const cell=(text,span=1,rowSpan=1)=>({innerText:text,cloneNode:()=>({textContent:text,querySelectorAll:()=>[]}),colSpan:span,rowSpan,getClientRects:()=>[{}],querySelectorAll:()=>[],querySelector:()=>null});
const days=['星期一','星期二','星期三','星期四','星期五','星期六','星期日'];
const head={cells:[cell('节次'),...days.map(d=>cell(d))],innerText:days.join(' ')};
const course='测试\n(1-2节)1-8周\n场地:A1\n教师:甲';
function read(rows){rows.forEach(r=>{if(r.innerText===undefined)r.innerText=r.cells.map(c=>c.innerText).join(" ");});return JSON.parse(vm.runInNewContext(script,{document:{getElementById:()=>null,querySelectorAll:s=>s==='table'?[{rows}]:[]}}));}
let result=read([head,{cells:[cell('1'),cell(course,1,2),...Array.from({length:6},()=>cell(''))]},{cells:[cell('2'),cell(course),...Array.from({length:5},()=>cell(''))]}]);
assert.equal(result.error,'');assert.deepEqual(result.courses.map(c=>c.day),[0,1]);
result=read([head,{cells:[cell('1'),cell('无法解析的课程'),...Array.from({length:6},()=>cell(''))]}]);assert(result.error);assert.equal(result.courses.length,0);
assert(read([]).error);console.log('PASS: weekday mapping, rowspan, unknown-cell rejection and no-table rejection.');

const wrappedHead={cells:[cell('时间段'),cell('节次'),...days.map(d=>cell(d.split('').join('\n')))],innerText:days.map(d=>d.split('').join('\n')).join(' ')};
result=read([wrappedHead,{cells:[cell('上午'),cell('1'),cell(course.replace('(1-2节)','(1-\n2节)')),...Array.from({length:6},()=>cell(''))]}]);
assert.equal(result.error,'','Wrapped weekday and section must import');assert.equal(result.courses[0].day,0);
console.log('PASS: mobile wrapped weekday and section regression.');

const iconCell=cell(course);
iconCell.cloneNode=()=>{
  const room={textContent:'',getAttribute:k=>k==='title'?'上课地点':null};
  const teacher={textContent:'',getAttribute:k=>k==='aria-label'?'教师':null};
  return {querySelectorAll:s=>s==='[title], [aria-label]'?[room,teacher]:[],get textContent(){return '测试\n(1-2节)1-8周'+room.textContent+'A1'+teacher.textContent+'甲';}};
};
result=read([head,{cells:[cell('1'),iconCell,...Array.from({length:6},()=>cell(''))]}]);
assert(result.courses[0].text.includes('上课地点:A1'));assert(result.courses[0].text.includes('教师:甲'));
console.log('PASS: icon tooltip and accessibility labels.');

const listRows=[{cells:[cell('星期一')]},{cells:[cell('3-4'),cell('测试课程 周数：1-12周 校区:东校区 上课地点：A12-102 教师 ：测试教师 教学班：测试-001')]},{cells:[cell('星期六')]},{cells:[cell('1-4'),cell('测试课程二 周数：2-13周 校区:东校区 上课地点：A12-104 教师 ：甲,乙 教学班：测试-002')]}];
const listResult=JSON.parse(vm.runInNewContext(script,{document:{getElementById:id=>id==='kblist_table'?{rows:listRows}:null,querySelectorAll:()=>[]}}));
assert.equal(listResult.error,'');assert.deepEqual(listResult.courses.map(c=>c.day),[0,5]);assert.equal(listResult.courses[1].text,'(1-4节)2-13周/场地:A12-104/教师:甲,乙/教学班:测试课程二');
console.log('PASS: observed university list structure, explicit fields and weekday groups.');
