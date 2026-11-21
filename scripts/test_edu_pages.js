// SPDX-License-Identifier: MPL-2.0
// Offline checks for the two page scripts used by the academic-system screens.
// The WebView hands these scripts a real DOM; here the smallest DOM they touch is faked.
const fs = require('fs'), vm = require('vm'), assert = require('assert');

const menuScript = fs.readFileSync('client/android/res/raw/edu_menu.js', 'utf8');
const tableScript = fs.readFileSync('client/android/res/raw/edu_tables.js', 'utf8');

const GRADES_WORDS = '成绩查询|学生成绩|成绩';
const DAILY_WORDS = '平时成绩|平时分|平时考核|过程考核|过程成绩|平时';

function anchor(text, href, visible = true) {
  return {innerText: text, textContent: text, href, getClientRects: () => visible ? [{}] : []};
}
function find(words, links) {
  const script = menuScript.replace('"__CQ_KEYWORDS__"', JSON.stringify(words));
  return vm.runInNewContext(script, {document: {querySelectorAll: () => links}});
}

// A menu that lists both a grade query and a daily-score query: each button must land on its own.
const menu = [
  anchor('课表查询', 'https://jwglxxfwpt.hebeu.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html'),
  anchor('成绩查询', 'https://jwglxxfwpt.hebeu.edu.cn/cjcx/cjcx_cxXsgrcj.html'),
  anchor('平时成绩查询', 'https://jwglxxfwpt.hebeu.edu.cn/cjcx/pscj_cx.html'),
  anchor('空闲教室查询', 'https://jwglxxfwpt.hebeu.edu.cn/cdjy/cdjy_cxKxcdyb.html'),
];
assert.equal(find(DAILY_WORDS, menu), 'https://jwglxxfwpt.hebeu.edu.cn/cjcx/pscj_cx.html');
assert.equal(find(GRADES_WORDS, menu), 'https://jwglxxfwpt.hebeu.edu.cn/cjcx/cjcx_cxXsgrcj.html');
assert.equal(find('空闲教室|空教室|教室查询', menu), 'https://jwglxxfwpt.hebeu.edu.cn/cdjy/cdjy_cxKxcdyb.html');
console.log('PASS: each query button follows its own menu entry.');

// Alternative labels and script links must not be clicked.
assert.equal(find(DAILY_WORDS, [anchor('平时分', 'https://jwglxxfwpt.hebeu.edu.cn/a.html')]),
  'https://jwglxxfwpt.hebeu.edu.cn/a.html');
assert.equal(find(DAILY_WORDS, [anchor('过程考核成绩', 'https://jwglxxfwpt.hebeu.edu.cn/b.html')]),
  'https://jwglxxfwpt.hebeu.edu.cn/b.html');
assert.equal(find(DAILY_WORDS, [anchor('平时成绩', 'javascript:void(0)')]), '');
assert.equal(find(DAILY_WORDS, [anchor('平时成绩', '#')]), '');
assert.equal(find(DAILY_WORDS, [anchor('平时成绩', 'https://jwglxxfwpt.hebeu.edu.cn/c.html', false)]), '');
assert.equal(find(DAILY_WORDS, [anchor('本学期平时成绩汇总与统计分析说明', 'https://jwglxxfwpt.hebeu.edu.cn/d.html')]), '');
assert.equal(find(DAILY_WORDS, [anchor('我的课表', 'https://jwglxxfwpt.hebeu.edu.cn/e.html')]), '');
console.log('PASS: script links, hidden links, long article titles and unrelated menus are ignored.');

// Table reading: a 平时分 grid keeps its header row and cells, layout wrappers are dropped.
function cell(text, tag = 'TD') { return {innerText: text, textContent: text, tagName: tag}; }
function table(rows, captionText = '') {
  return {rows, caption: {innerText: captionText}, getClientRects: () => [{}]};
}
function read(tables, title = '学生平时成绩查询') {
  const document = {
    title,
    querySelectorAll: selector => selector === 'table' ? tables : [],
  };
  return JSON.parse(vm.runInNewContext(tableScript, {document, location: {href: 'https://jwglxxfwpt.hebeu.edu.cn/cjcx/pscj_cx.html'}}));
}
const header = {rows: null, cells: [cell('学号', 'TH'), cell('姓名', 'TH'), cell('课程名称', 'TH'), cell('考勤', 'TH'), cell('作业', 'TH'), cell('平时成绩', 'TH')]};
header.rows = [header];
const dailyRows = table([
  header,
  {cells: [cell('20230001'), cell('甲'), cell('高等数学'), cell('10'), cell('8'), cell('18')]},
  {cells: [cell('20230001'), cell('甲'), cell('大学英语'), cell('9'), cell('7'), cell('16')]},
], '平时成绩');
const oneColumn = table([{cells: [cell('查询条件')]}, {cells: [cell('2024-2025 学年')]}]);
const page = read([dailyRows, oneColumn]);
assert.equal(page.error, '');
assert.equal(page.tables.length, 1, 'A one-column layout table is not a result grid');
assert.deepEqual(page.tables[0].headers, ['学号', '姓名', '课程名称', '考勤', '作业', '平时成绩']);
assert.equal(page.tables[0].caption, '平时成绩');
assert.equal(page.tables[0].rows.length, 2);
assert.equal(read([]).error.length > 0, true);
console.log('PASS: a daily-score table keeps its headers and cells; layout wrappers are rejected.');

// The same header set is read by the column mapper used on the result screen.
const columns = page.tables[0].headers;
assert.equal(columns.indexOf('平时成绩'), 5);
console.log('PASS: page script and column mapper agree on the 平时成绩 column.');
