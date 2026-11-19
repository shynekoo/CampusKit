(function () {
  'use strict';

  // 与 update.json 保持一致；仅在网络读取失败时用于展示。
  var FALLBACK = {
    version: '2.5.8',
    bytes: 5420922,
    url: 'https://wbapp-g8yy3klci8kmeufm6vdn0h.app.workbuddy.host/campuskit-2.5.8.apk?sharecode=BZD63HRP4B'
  };

  var release = Object.assign({}, FALLBACK);
  var dialog = document.getElementById('download-dialog');

  function render() {
    var version = 'v' + release.version;
    Array.prototype.forEach.call(document.querySelectorAll('[data-version]'), function (el) {
      el.textContent = version;
    });
    Array.prototype.forEach.call(document.querySelectorAll('[data-size]'), function (el) {
      el.textContent = (release.bytes / 1024 / 1024).toFixed(1) + ' MB';
    });
  }

  function validRelease(d) {
    return d && typeof d.version === 'string' && /^\d+(\.\d+)+$/.test(d.version) &&
      Number.isInteger(d.bytes) && d.bytes > 0 &&
      typeof d.url === 'string' && /^https:\/\//.test(d.url);
  }

  function loadRelease() {
    fetch('update.json', { cache: 'no-store' })
      .then(function (response) {
        if (!response.ok) throw new Error('update.json unavailable');
        return response.json();
      })
      .then(function (data) {
        if (!validRelease(data)) throw new Error('invalid update.json');
        release = {
          version: data.version,
          bytes: data.bytes,
          url: data.url
        };
      })
      .catch(function () { /* 网络失败时保留内置展示信息 */ })
      .then(render);
  }

  Array.prototype.forEach.call(document.querySelectorAll('[data-download]'), function (button) {
    button.addEventListener('click', function () {
      if (dialog) {
        dialog.showModal();
        button.setAttribute('aria-expanded', 'true');
      }
    });
  });

  if (dialog) {
    dialog.addEventListener('close', function () {
      Array.prototype.forEach.call(document.querySelectorAll('[data-download]'), function (button) {
        button.setAttribute('aria-expanded', 'false');
      });
    });

    var closeButton = dialog.querySelector('[data-close]');
    if (closeButton) closeButton.addEventListener('click', function () { dialog.close(); });
    dialog.addEventListener('click', function (event) {
      if (event.target === dialog) dialog.close();
    });

    var primary = dialog.querySelector('[data-primary-download]');
    if (primary) primary.addEventListener('click', function () {
      window.location.assign(release.url);
    });

  }

  render();
  loadRelease();
})();
