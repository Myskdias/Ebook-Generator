// Runs only on the sanitized, offline WebView preview.
(() => {
  "use strict";
  if (window.__ebookPicker) return;

  let hovered = null;
  let selected = null;
  let previousOutline = "";
  let previousOutlinePriority = "";
  let previousOffset = "";
  let pending = null;

  function clearHighlight() {
    if (!hovered) return;
    hovered.style.setProperty("outline", previousOutline, previousOutlinePriority);
    hovered.style.outlineOffset = previousOffset;
    hovered = null;
  }

  function highlight(element) {
    if (hovered === element) return;
    clearHighlight();
    if (!element || element === document.documentElement) return;
    hovered = element;
    previousOutline = element.style.getPropertyValue("outline");
    previousOutlinePriority = element.style.getPropertyPriority("outline");
    previousOffset = element.style.outlineOffset;
    element.style.setProperty("outline", "3px solid #e68a2e", "important");
    element.style.outlineOffset = "2px";
  }

  function unique(selector) {
    try {
      return document.querySelectorAll(selector).length === 1;
    } catch (_) {
      return false;
    }
  }

  function simpleSelector(element) {
    const tag = element.tagName.toLowerCase();
    if (element.id && /^[A-Za-z_][A-Za-z0-9_-]*$/.test(element.id)) {
      const byId = "#" + element.id;
      if (unique(byId)) return byId;
    }
    const classes = Array.from(element.classList)
      .filter(c => /^[A-Za-z_][A-Za-z0-9_-]*$/.test(c)).slice(0, 2);
    if (classes.length) {
      const withClass = tag + "." + classes.join(".");
      if (unique(withClass)) return withClass;
    }
    if (unique(tag)) return tag;
    return "";
  }

  function selectorFor(element) {
    const simple = simpleSelector(element);
    if (simple) return simple;
    const segments = [];
    for (let node = element; node && node.nodeType === 1; node = node.parentElement) {
      const tag = node.tagName.toLowerCase();
      const index = Array.from(node.parentElement ? node.parentElement.children : [])
        .filter(child => child.tagName === node.tagName).indexOf(node) + 1;
      let segment = tag;
      if (node.id && /^[A-Za-z_][A-Za-z0-9_-]*$/.test(node.id)) {
        segment = "#" + node.id;
      } else {
        segment += ":nth-of-type(" + index + ")";
      }
      segments.unshift(segment);
      const candidate = segments.join(" > ");
      if (unique(candidate)) return candidate;
      if (segment[0] === "#") return candidate;
    }
    return segments.join(" > ");
  }

  function choose(element) {
    if (!element || element === document.documentElement || element === document.body) {
      return;
    }
    selected = element;
    highlight(element);
    pending = JSON.stringify({
      selector: selectorFor(element),
      tag: element.tagName.toLowerCase(),
      text: (element.innerText || element.textContent || "").trim().slice(0, 120)
    });
  }

  document.addEventListener("mouseover", event => {
    if (event.target instanceof Element) highlight(event.target);
  }, true);

  document.addEventListener("click", event => {
    event.preventDefault();
    event.stopImmediatePropagation();
    if (event.target instanceof Element) choose(event.target);
  }, true);

  // Never submit forms while the user is selecting DOM elements.
  document.addEventListener("submit", event => event.preventDefault(), true);

  window.__ebookPicker = Object.freeze({
    takeSelection() {
      const result = pending;
      pending = null;
      return result;
    },
    selectParent() {
      if (selected && selected.parentElement &&
          selected.parentElement !== document.body &&
          selected.parentElement !== document.documentElement) {
        choose(selected.parentElement);
      }
    }
  });
})();
