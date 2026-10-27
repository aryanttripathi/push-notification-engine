'use strict';

/** Calls the JSON API. A non-2xx response throws an Error carrying the server's {code, message, details, missing}. */
async function api(method, url, body) {
  const options = { method, headers: { Accept: 'application/json' } };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const response = await fetch(url, options);
  const text = await response.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch (ignored) { data = { message: text }; }
  }
  if (!response.ok) {
    const error = new Error((data && data.message) || response.status + ' ' + response.statusText);
    error.data = data || {};
    throw error;
  }
  return data;
}

/**
 * Builds an element. Children are nodes or values; values are always inserted as text, never parsed as HTML,
 * so DB and user-provided strings can't inject markup.
 */
function el(tag, props, ...children) {
  const node = document.createElement(tag);
  Object.entries(props || {}).forEach(([key, value]) => {
    if (value === null || value === undefined || value === false) return;
    if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key === 'className') node.className = value;
    else node.setAttribute(key, value === true ? '' : value);
  });
  children.flat().forEach(child => {
    if (child === null || child === undefined) return;
    node.append(child instanceof Node ? child : String(child));
  });
  return node;
}

function showError(box, error) {
  const data = error.data || {};
  const extra = data.missing ? ['missing: ' + data.missing.join(', ')] : (data.details || []);
  box.className = 'alert error';
  box.replaceChildren(el('strong', {}, data.code || 'Error'), ' ', error.message);
  if (extra.length) box.append(el('ul', {}, extra.map(item => el('li', {}, item))));
  box.hidden = false;
}

function showOk(box, text) {
  box.className = 'alert ok';
  box.replaceChildren(text);
  box.hidden = false;
}

function hideMessage(box) {
  box.hidden = true;
  box.replaceChildren();
}

/** Renders a cell value as text: objects as compact JSON, null as an empty cell. */
function cellText(value) {
  if (value === null || value === undefined) return '';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}
