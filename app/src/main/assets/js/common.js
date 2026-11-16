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
