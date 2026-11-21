// SPDX-License-Identifier: MPL-2.0
(function () {
  // Reads only rendered table text. Nothing here touches inputs, cookies or login state.
  const MAX_TABLES = 8, MAX_ROWS = 400, MAX_COLS = 24, MAX_CELL = 120;

  function clean(value) {
    return (value || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim().slice(0, MAX_CELL);
  }
  function visible(node) {
    return !!(node.getClientRects && node.getClientRects().length);
  }

  const result = {url: location.href, title: clean(document.title), error: '', tables: []};
  const seen = new Set();

  function collect(doc) {
    for (const table of doc.querySelectorAll('table')) {
      if (result.tables.length >= MAX_TABLES) return;
      if (!visible(table) || table.rows.length < 2) continue;
      const rows = [];
      let hasHeader = false;
      for (const tr of table.rows) {
        const cells = [];
        for (const cell of tr.cells) {
          if (cells.length >= MAX_COLS) break;
          cells.push(clean(cell.innerText !== undefined ? cell.innerText : cell.textContent));
          if (cell.tagName === 'TH' && tr === table.rows[0]) hasHeader = true;
        }
        if (cells.some(text => text)) rows.push(cells);
        if (rows.length > MAX_ROWS) break;
      }
      // A one-column table is a layout wrapper, not a result grid.
      if (rows.length < 2 || rows[0].length < 2) continue;
      const caption = clean(table.caption ? table.caption.innerText : '');
      const key = rows.slice(0, 3).join('|') + '#' + rows.length;
      if (seen.has(key)) continue;
      seen.add(key);
      result.tables.push({caption: caption, headers: hasHeader ? rows[0] : [], rows: hasHeader ? rows.slice(1) : rows});
    }
    for (const frame of doc.querySelectorAll('iframe')) {
      try { if (frame.contentDocument) collect(frame.contentDocument); } catch (ignored) { }
    }
  }

  try {
    collect(document);
    if (!result.tables.length) result.error = '这一页没有可读取的表格。请先点“查成绩”或“空教室”进入查询结果页，再读取。';
  } catch (failure) {
    result.error = failure.message || '读取页面失败';
    result.tables = [];
  }
  return JSON.stringify(result);
})();
