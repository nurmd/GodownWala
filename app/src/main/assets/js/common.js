/**
 * Shared, load-order-independent helpers. This file must be loaded before any
 * other app script so its functions are available everywhere.
 */

/**
 * Escapes untrusted data for safe interpolation into innerHTML / attributes.
 * Escapes &, <, >, ", and ' so the result is safe both in element content and
 * inside double- or single-quoted attribute values.
 */
function escapeHtml(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Debounces a render so typing in a search box does not rebuild the DOM on
 * every keystroke (each rebuild also triggers Tailwind's runtime scan).
 */
var _renderTimers = _renderTimers || {};
function debounceRender(key, fn, ms) {
  clearTimeout(_renderTimers[key]);
  _renderTimers[key] = setTimeout(fn, ms || 120);
}
