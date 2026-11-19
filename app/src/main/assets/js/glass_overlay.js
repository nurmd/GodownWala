/**
 * Baked "frosted glass" backdrop for product cards.
 *
 * A live backdrop-filter costs a full backdrop pass per card per frame, which
 * measured ~35 fps while scrolling the product grid. A CSS filter:blur() layer
 * is not much better because the WebView re-rasterises it during scroll.
 *
 * So the blurred + tinted backdrop is rasterised ONCE with canvas and cached as
 * a small JPEG data URL keyed by the product image. The card then paints it as
 * a plain background-image, which the compositor scrolls for free. It is only
 * re-baked when the image itself changes (the key is derived from the image).
 *
 * Baking is decoupled from any specific DOM node: a re-rendered grid never
 * discards work, and a cached entry is applied to every current overlay that
 * needs it.
 */

(function () {
  const CACHE_PREFIX = 'glass:v1:';
  const INDEX_KEY = 'glass:v1:index';
  const MAX_ENTRIES = 80;      // ~7 KB each -> well under the localStorage cap
  const TILE_W = 128;          // logical px; upscaled by background-size: cover
  const TILE_H = 96;
  const BLUR_PX = 11;

  const queue = [];
  const inflight = {};         // key -> true while a bake is queued/running
  let pumping = false;

  function hashKey(str) {
    let h = 0x811c9dc5;
    for (let i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = (h * 0x01000193) >>> 0;
    }
    return h.toString(36);
  }

  function schedule(fn) {
    if (typeof window.requestIdleCallback === 'function') {
      window.requestIdleCallback(fn, { timeout: 800 });
    } else {
      setTimeout(fn, 16);
    }
  }

  function readCache(key) {
    try { return localStorage.getItem(key) || null; } catch (e) { return null; }
  }

  function writeCache(key, value) {
    try {
      const idx = JSON.parse(localStorage.getItem(INDEX_KEY) || '[]');
      if (idx.indexOf(key) === -1) idx.push(key);
      while (idx.length > MAX_ENTRIES) {
        const old = idx.shift();
        try { localStorage.removeItem(old); } catch (e) {}
      }
      localStorage.setItem(key, value);
      localStorage.setItem(INDEX_KEY, JSON.stringify(idx));
    } catch (e) {
      try {
        const idx = JSON.parse(localStorage.getItem(INDEX_KEY) || '[]');
        idx.forEach(k => localStorage.removeItem(k));
        localStorage.removeItem(INDEX_KEY);
      } catch (_) {}
    }
  }

  function applyData(overlayEl, data) {
    if (!overlayEl) return;
    overlayEl.style.backgroundImage = 'url("' + data + '")';
    overlayEl.dataset.glassDone = '1';
  }

  function applyToCurrent(imageUrl, data) {
    document.querySelectorAll('.product-media-overlay').forEach(ov => {
      if (ov.dataset.glassSrc === imageUrl && ov.dataset.glassDone !== '1') {
        applyData(ov, data);
      }
    });
  }

  function renderBlur(img) {
    try {
      const canvas = document.createElement('canvas');
      canvas.width = TILE_W;
      canvas.height = TILE_H;
      const ctx = canvas.getContext('2d');
      if (!ctx) return null;

      const scale = Math.max(TILE_W / img.naturalWidth, TILE_H / img.naturalHeight);
      const dw = img.naturalWidth * scale;
      const dh = img.naturalHeight * scale;

      if (typeof ctx.filter === 'string') {
        ctx.filter = 'blur(' + BLUR_PX + 'px) saturate(1.3)';
      }
      ctx.drawImage(img, (TILE_W - dw) / 2, (TILE_H - dh) / 2, dw, dh);
      if (typeof ctx.filter === 'string') ctx.filter = 'none';

      const grad = ctx.createLinearGradient(0, 0, 0, TILE_H);
      grad.addColorStop(0, 'rgba(18, 18, 20, 0.58)');
      grad.addColorStop(1, 'rgba(18, 18, 20, 0.86)');
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, TILE_W, TILE_H);

      return canvas.toDataURL('image/jpeg', 0.55);
    } catch (e) {
      return null;
    }
  }

  function finish(key, imageUrl, data) {
    delete inflight[key];
    if (!data) return;
    writeCache(key, data);
    applyToCurrent(imageUrl, data);
  }

  function bake(key, imageUrl, imgEl) {
    const ready = (imgEl && imgEl.complete && imgEl.naturalWidth) ? imgEl : null;
    if (ready) {
      finish(key, imageUrl, renderBlur(ready));
      return;
    }
    // Image not decoded yet: load an offscreen copy so the bake is not tied to
    // the lifetime of the grid node that scheduled it.
    const off = new Image();
    off.onload = function () { finish(key, imageUrl, renderBlur(off)); };
    off.onerror = function () { delete inflight[key]; };
    off.src = imageUrl;
  }

  function pump() {
    if (pumping) return;
    pumping = true;
    const step = () => {
      const job = queue.shift();
      if (!job) { pumping = false; return; }
      try { job(); } catch (e) {}
      if (queue.length) schedule(step); else pumping = false;
    };
    schedule(step);
  }

  /**
   * Applies the cached glass backdrop to a card overlay, queueing a bake when
   * this image has not been seen before.
   */
  window.applyGlassOverlay = function (overlayEl, imageUrl, imgEl) {
    if (!overlayEl || !imageUrl) return;
    if (overlayEl.dataset.glassSrc === imageUrl && overlayEl.dataset.glassDone === '1') return;

    overlayEl.dataset.glassSrc = imageUrl;

    const key = CACHE_PREFIX + hashKey(imageUrl + '|' + TILE_W + 'x' + TILE_H);
    const cached = readCache(key);
    if (cached) {
      applyData(overlayEl, cached);
      return;
    }

    overlayEl.dataset.glassDone = '';
    if (inflight[key]) return; // a bake is already queued/running for this image
    inflight[key] = true;
    queue.push(function () { bake(key, imageUrl, imgEl); });
    pump();
  };

  window.glassCacheStats = function () {
    try {
      const idx = JSON.parse(localStorage.getItem(INDEX_KEY) || '[]');
      let bytes = 0;
      idx.forEach(k => { bytes += (localStorage.getItem(k) || '').length; });
      return { entries: idx.length, kb: Math.round(bytes / 1024), max: MAX_ENTRIES };
    } catch (e) {
      return { entries: 0, kb: 0, max: MAX_ENTRIES };
    }
  };
})();
