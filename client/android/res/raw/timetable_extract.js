// SPDX-License-Identifier: MPL-2.0
(function () {
  // Read only rendered timetable cells; never inspect inputs, cookies or login state.
  const result = {courses: [], error: ''};
  const days = ['星期一','星期二','星期三','星期四','星期五','星期六','星期日'];
  const compact = text => (text || '').replace(/\s+/g, '');
  function courseText(block) {
    const copy = block.cloneNode(true);
    for (const node of copy.querySelectorAll('[title], [aria-label]')) {
      const label = compact(node.getAttribute('title') || node.getAttribute('aria-label')).replace(/[:：]$/, '');
      // Some school themes put labels in icon tooltips rather than visible text.
      if (/^(上课地点|场地|教室|教师|任课教师)$/.test(label) && !compact(node.textContent)) node.textContent = '\n' + label + ':';
    }
    for (const node of copy.querySelectorAll('br')) node.replaceWith('\n');
    for (const node of copy.querySelectorAll('p, div, li')) node.append('\n');
    return copy.textContent;
  }
  function read(doc) {
    // Verified in the school's actual page: a generated list accompanies the grid.
    // Its weekday group rows and explicit labels avoid presentation-dependent icons.
    const list = doc.getElementById('kblist_table');
    if (list) {
      let day = -1;
      for (const row of Array.from(list.rows)) {
        const cells = Array.from(row.cells);
        if (cells.length === 1) {
          const found = days.indexOf(compact(cells[0].textContent || cells[0].innerText));
          if (found >= 0) day = found;
          continue;
        }
        if (cells.length !== 2 || day < 0) continue;
        const sections = compact(cells[0].textContent || cells[0].innerText);
        const text = compact(cells[1].textContent || cells[1].innerText).replace(/：/g, ':');
        if (!/^\d+(?:-\d+)?$/.test(sections)) throw Error('列表课表节次格式无法识别，未导入。');
        const match = text.match(/^(.+?)周数:(.+?)校区:.*?上课地点:(.+?)教师:(.+?)教学班:/);
        if (!match) throw Error('列表课表存在不完整的课程字段，未导入。');
        result.courses.push({day, title:match[1], text:'('+sections+'节)'+match[2]+'/场地:'+match[3]+'/教师:'+match[4]+'/教学班:'+match[1]});
      }
      if (result.courses.length) return;
    }
    for (const table of doc.querySelectorAll('table')) {
      const rows = Array.from(table.rows);
      const header = rows.findIndex(r => days.every(d => Array.from(r.cells).some(c => compact(c.textContent || c.innerText) === d)));
      if (header < 0) continue;
      const mapping = {}; let col = 0;
      for (const cell of rows[header].cells) {
        const day = days.findIndex(d => compact(cell.innerText) === d);
        if (day >= 0) mapping[col] = day;
        col += cell.colSpan;
      }
      if (Object.keys(mapping).length !== 7) continue;
      const occupied = [];
      for (let row = header + 1; row < rows.length; row++) {
        let x = 0;
        for (const cell of rows[row].cells) {
          while ((occupied[x] || 0) > row) x++;
          const start = x;
          for (let n = 0; n < cell.colSpan; n++) occupied[x++] = row + cell.rowSpan;
          if (mapping[start] === undefined || !cell.getClientRects().length) continue;
          const text = cell.innerText.trim();
          if (!text || /^[-—\s]+$/.test(text)) continue;
          if (!/[（(]\d+(?:-\d+)?节[）)]|节次[:：]/.test(compact(text))) throw Error('存在未识别的课程单元格，请使用 PDF 导入核对。');
          if (cell.colSpan !== 1) throw Error('课程横跨多个星期，请改用 PDF 导入核对。');
          let blocks = Array.from(cell.querySelectorAll('.timetable_con')).filter(n => n.innerText.trim());
          if (!blocks.length) blocks = [cell];
          for (const block of blocks) {
            const title = block.querySelector('.title');
            result.courses.push({day:mapping[start], title:title ? compact(title.innerText) : '', text:courseText(block)});
          }
        }
      }
      if (result.courses.length) return;
    }
    for (const frame of doc.querySelectorAll('iframe')) {
      try {
        if (frame.contentDocument) read(frame.contentDocument);
      } catch (_) { }
      if (result.courses.length) return;
    }
  }
  try { read(document); if (!result.courses.length) result.error = '未找到可识别的课表。请进入学生课表查询，选择学期并显示表格式课表，再读取。'; }
  catch (e) { result.error = e.message; result.courses = []; }
  return JSON.stringify(result);
})();
