/**
 * Product image normalisation for the POS cards.
 *
 * Product photos arrive in three shapes:
 *   1. Opaque photos with their own background  -> keep as-is.
 *   2. PNGs with transparency                   -> composite onto white.
 *   3. Renders with a black studio background   -> flood-fill the black that is
 *      connected to the border with white.
 *
 * The app's cards are light, so a transparent or black background reads as a
 * hole in the card. Normalising once per image keeps scrolling cheap: the
 * result is a plain data URL that the <img> and the glass baker both use.
 */
(function () {
  const CACHE_PREFIX = 'pimg:v1:';
  const INDEX_KEY = 'pimg:v1:index';
  const MAX_ENTRIES = 40;
  const MAX_SIDE = 512;        // processing size; big enough for a 2x card
  const BLACK = 34;            // channel ceiling for "background black"
  const JPEG_QUALITY = 0.86;

  const memo = Object.create(null);
  const pending = Object.create(null);

  function hashKey(str) {
    let h = 0x811c9dc5;
    for (let i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = (h * 0x01000193) >>> 0;
    }
    return h.toString(36) + '_' + str.length.toString(36);
  }

  function readCache(key) {
    try { return localStorage.getItem(CACHE_PREFIX + key) || null; } catch (e) { return null; }
  }

  function writeCache(key, value) {
    try {
      const idx = JSON.parse(localStorage.getItem(INDEX_KEY) || '[]');
      if (idx.indexOf(key) === -1) idx.push(key);
      while (idx.length > MAX_ENTRIES) {
        const old = idx.shift();
        try { localStorage.removeItem(CACHE_PREFIX + old); } catch (e) {}
      }
      localStorage.setItem(CACHE_PREFIX + key, value);
      localStorage.setItem(INDEX_KEY, JSON.stringify(idx));
    } catch (e) {
      // Quota: drop the cache rather than fail the render.
      try {
        const idx = JSON.parse(localStorage.getItem(INDEX_KEY) || '[]');
        idx.forEach(k => localStorage.removeItem(CACHE_PREFIX + k));
        localStorage.removeItem(INDEX_KEY);
      } catch (_) {}
    }
  }

  function isBlackish(d, i) {
    return d[i] <= BLACK && d[i + 1] <= BLACK && d[i + 2] <= BLACK;
  }

  function borderIsMostlyBlack(d, w, h) {
    let dark = 0;
    let total = 0;
    const sample = (x, y) => {
      const i = (y * w + x) * 4;
      total++;
      if (d[i + 3] < 250 || isBlackish(d, i)) dark++;
    };
    for (let x = 0; x < w; x += 2) { sample(x, 0); sample(x, h - 1); }
    for (let y = 0; y < h; y += 2) { sample(0, y); sample(w - 1, y); }
    return total > 0 && dark / total > 0.55;
  }

  /** Replaces black pixels that are connected to the image border with white. */
  function whitenBorderBackground(ctx, w, h) {
    const frame = ctx.getImageData(0, 0, w, h);
    const d = frame.data;
    if (!borderIsMostlyBlack(d, w, h)) return false;

    const seen = new Uint8Array(w * h);
    const stack = [];
    const tryPush = (x, y) => {
      if (x < 0 || y < 0 || x >= w || y >= h) return;
      const p = y * w + x;
      if (seen[p]) return;
      seen[p] = 1;
      if (!isBlackish(d, p * 4)) return;
      stack.push(p);
    };

    for (let x = 0; x < w; x++) { tryPush(x, 0); tryPush(x, h - 1); }
    for (let y = 0; y < h; y++) { tryPush(0, y); tryPush(w - 1, y); }

    while (stack.length) {
      const p = stack.pop();
      const i = p * 4;
      d[i] = 255; d[i + 1] = 255; d[i + 2] = 255; d[i + 3] = 255;
      const x = p % w;
      const y = (p - x) / w;
      tryPush(x + 1, y); tryPush(x - 1, y); tryPush(x, y + 1); tryPush(x, y - 1);
    }

    ctx.putImageData(frame, 0, 0);
    return true;
  }

  function hasTransparency(ctx, w, h) {
    const d = ctx.getImageData(0, 0, w, h).data;
    for (let i = 3; i < d.length; i += 4) {
      if (d[i] < 250) return true;
    }
    return false;
  }

  /**
   * Returns a promise for a displayable URL: the original when nothing needed
   * changing, or a white-backed data URL.
   */
  function normalise(url) {
    if (memo[url]) return Promise.resolve(memo[url]);

    const key = hashKey(url);
    const cached = readCache(key);
    if (cached) { memo[url] = cached; return Promise.resolve(cached); }

    if (pending[url]) return pending[url];

    pending[url] = new Promise(resolve => {
      const done = value => {
        delete pending[url];
        memo[url] = value;
        resolve(value);
      };

      const load = (useCors) => {
        const img = new Image();
        if (useCors) img.crossOrigin = 'anonymous';
        img.onload = function () {
          try {
            const scale = Math.min(1, MAX_SIDE / Math.max(img.naturalWidth || 1, img.naturalHeight || 1));
            const w = Math.max(1, Math.round((img.naturalWidth || MAX_SIDE) * scale));
            const h = Math.max(1, Math.round((img.naturalHeight || MAX_SIDE) * scale));

            const canvas = document.createElement('canvas');
            canvas.width = w;
            canvas.height = h;
            const ctx = canvas.getContext('2d');
            if (!ctx) { done(url); return; }

            // Draw first so the source alpha is still readable, then drop the
            // white base in behind it.
            ctx.drawImage(img, 0, 0, w, h);
            const transparent = hasTransparency(ctx, w, h);

            ctx.globalCompositeOperation = 'destination-over';
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(0, 0, w, h);
            ctx.globalCompositeOperation = 'source-over';

            const whitened = whitenBorderBackground(ctx, w, h);
            if (!transparent && !whitened) { done(url); return; }

            const dataUrl = canvas.toDataURL('image/jpeg', JPEG_QUALITY);
            writeCache(key, dataUrl);
            done(dataUrl);
          } catch (e) {
            // Tainted canvas (no CORS) or decode issue: keep the original.
            done(url);
          }
        };
        img.onerror = function () {
          if (useCors) load(false);
          else done(url);
        };
        img.src = url;
      };

      load(true);
    });

    return pending[url];
  }

  window.normalizeProductImage = normalise;
})();
