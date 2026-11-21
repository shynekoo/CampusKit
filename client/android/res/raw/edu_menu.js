// SPDX-License-Identifier: MPL-2.0
(function () {
  // Finds the academic system's own menu entry instead of hard-coding a route, so a semester
  // change on the school side cannot silently send the user to a dead page.
  const wanted = "__CQ_KEYWORDS__".split('|').filter(Boolean);
  function clean(value) { return (value || '').replace(/\s+/g, ''); }
  function visible(node) { return !!(node.getClientRects && node.getClientRects().length); }
  function usable(href) {
    if (!href) return false;
    const value = String(href).trim();
    return value.indexOf('javascript:') !== 0 && value !== '#' && value.indexOf('void(0)') < 0;
  }

  const best = {score: -1, href: ''};
  for (const link of document.querySelectorAll('a[href]')) {
    const text = clean(link.innerText || link.textContent);
    if (!text || text.length > 12 || !visible(link) || !usable(link.href)) continue;
    for (const word of wanted) {
      if (text.indexOf(word) < 0) continue;
      // Exact match wins, then the shortest label (menu items, not article links).
      const score = (text === word ? 1000 : 500) + (60 - text.length) + word.length;
      if (score > best.score) { best.score = score; best.href = link.href; }
    }
  }
  if (!best.href) return '';
  return String(best.href);
})();
