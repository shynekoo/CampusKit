// SPDX-License-Identifier: MPL-2.0
(function () {
  // Drives the school's own query form: lists its filters, sets them, presses its 查询 button and
  // reads the rendered table. Nothing here stores credentials or writes to the page.
  if (window.__cq && window.__cq.version === 3) return 'ok';

  var MAX_TABLES = 6, MAX_ROWS = 400, MAX_COLS = 24, MAX_CELL = 120;

  function clean(value) {
    return (value || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim().slice(0, MAX_CELL);
  }
  function visible(node) {
    return !!(node && node.getClientRects && node.getClientRects().length);
  }
  function label(node) {
    var text = '';
    var cell = node.closest ? node.closest('td') : null;
    if (cell && cell.previousElementSibling) text = cell.previousElementSibling.textContent || '';
    if (!clean(text) && node.labels && node.labels.length) text = node.labels[0].textContent || '';
    if (!clean(text)) {
      var parent = node.parentElement;
      if (parent) {
        var own = clean(parent.textContent);
        if (own && own.length <= 12) text = own;
      }
    }
    var name = clean(text) || node.getAttribute('aria-label') || node.name || node.id || '';
    return name.replace(/[：:*]/g, '').replace(/\s+/g, '');
  }

  function selects() {
    var found = [];
    var all = document.querySelectorAll('select');
    for (var i = 0; i < all.length; i++) {
      var node = all[i];
      if (!visible(node) || node.disabled || node.options.length < 2) continue;
      if (node.options.length > 400) continue;
      var options = [];
      for (var o = 0; o < node.options.length; o++) {
        var option = node.options[o];
        var text = clean(option.textContent);
        if (!text || text === '请选择' || text === '全部') continue;
        options.push({v: option.value, t: text});
      }
      if (options.length < 2) continue;
      found.push({i: found.length, node: node, label: label(node), value: node.value, options: options});
    }
    window.__cq._sels = found;
    return found.map(function (entry) {
      return {i: entry.i, label: entry.label, value: entry.value, options: entry.options};
    });
  }

  function setValue(index, value) {
    var entry = (window.__cq._sels || [])[index];
    if (!entry) return false;
    var node = entry.node;
    if (node.value === value) return true;
    node.value = value;
    node.dispatchEvent(new Event('input', {bubbles: true}));
    node.dispatchEvent(new Event('change', {bubbles: true}));
    try { if (window.jQuery) window.jQuery(node).trigger('change'); } catch (ignored) { }
    return true;
  }

  function button() {
    var wanted = ['查询', '查 询', '搜索', '检索'];
    var nodes = document.querySelectorAll('button, input[type=button], input[type=submit], a');
    for (var w = 0; w < wanted.length; w++) {
      for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        if (!visible(node)) continue;
        var text = clean(node.value || node.textContent);
        if (text === wanted[w] || text.indexOf(wanted[w]) === 0 && text.length <= 4) return node;
      }
    }
    return null;
  }

  function press() {
    var node = button();
    if (!node) return '';
    try { node.click(); } catch (ignored) { }
    var form = node.closest ? node.closest('form') : null;
    if (form && typeof form.submit === 'function' && node.tagName === 'BUTTON' && node.type !== 'button') {
      // A plain submit button only needs click(); this exists for pages that ignore synthetic clicks.
      try { form.submit(); } catch (ignored) { }
    }
    return clean(node.value || node.textContent);
  }

  function tables() {
    var result = {tables: [], text: ''};
    var seen = {};
    function collect(doc) {
      var list = doc.querySelectorAll('table');
      for (var t = 0; t < list.length; t++) {
        if (result.tables.length >= MAX_TABLES) return;
        var table = list[t];
        if (!visible(table) || table.rows.length < 2) continue;
        var rows = [], hasHeader = false;
        for (var r = 0; r < table.rows.length && rows.length <= MAX_ROWS; r++) {
          var tr = table.rows[r], cells = [];
          for (var c = 0; c < tr.cells.length && cells.length < MAX_COLS; c++) {
            var cell = tr.cells[c];
            cells.push(clean(cell.innerText !== undefined ? cell.innerText : cell.textContent));
            if (cell.tagName === 'TH' && r === 0) hasHeader = true;
          }
          if (cells.some(function (v) { return v; })) rows.push(cells);
        }
        if (rows.length < 2 || rows[0].length < 2) continue;
        var key = rows.slice(0, 3).join('|') + '#' + rows.length;
        if (seen[key]) continue;
        seen[key] = 1;
        result.tables.push({
          caption: clean(table.caption ? table.caption.innerText : ''),
          headers: hasHeader ? rows[0] : [],
          rows: hasHeader ? rows.slice(1) : rows
        });
      }
      var frames = doc.querySelectorAll('iframe');
      for (var f = 0; f < frames.length; f++) {
        try { if (frames[f].contentDocument) collect(frames[f].contentDocument); } catch (ignored) { }
      }
    }
    try {
      collect(document);
      var body = clean(document.body ? document.body.textContent : '').slice(0, 200);
      result.text = body;
    } catch (failure) {
      result.text = '';
    }
    return result;
  }

  window.__cq = {
    version: 3,
    _sels: [],
    list: function () { return JSON.stringify({selects: selects(), hasButton: !!button()}); },
    set: function (index, value) { return setValue(index, value); },
    query: function () { return press(); },
    tables: function () { return JSON.stringify(tables()); },
    url: function () { return location.href; }
  };
  return 'ok';
})();
