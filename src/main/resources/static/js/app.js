document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.alert-close').forEach(button => button.addEventListener('click', () => button.closest('.alert').remove()));
  const symbol = document.getElementById('symbol');
  const priceSymbol = document.getElementById('price-symbol');
  if (symbol && priceSymbol) {
    symbol.addEventListener('input', () => {
      if (!priceSymbol.dataset.touched) priceSymbol.value = symbol.value.toUpperCase();
    });
    priceSymbol.addEventListener('input', () => priceSymbol.dataset.touched = 'true');
  }
  document.querySelectorAll('form').forEach(form => form.addEventListener('submit', event => {
    const qty = form.querySelector('[name="quantity"]');
    const price = form.querySelector('[name="price"]');
    if (qty && (!Number.isInteger(Number(qty.value)) || Number(qty.value) <= 0)) {
      event.preventDefault(); qty.setCustomValidity('Enter a positive whole-share quantity.'); qty.reportValidity();
    } else if (qty) qty.setCustomValidity('');
    if (price && (!Number.isFinite(Number(price.value)) || Number(price.value) <= 0)) {
      event.preventDefault(); price.setCustomValidity('Enter a price greater than zero.'); price.reportValidity();
    } else if (price) price.setCustomValidity('');
  }));
});

// Profile menu: opens on click, closes on outside click or Escape, one detail section open at a time.
(function () {
  const root = document.getElementById('profileMenu');
  const button = document.getElementById('profileButton');
  const panel = document.getElementById('profilePanel');
  if (!root || !button || !panel) return;
  const items = panel.querySelectorAll('.profile-item');

  function collapseSections() {
    items.forEach(item => {
      item.setAttribute('aria-expanded', 'false');
      document.getElementById(item.dataset.section).hidden = true;
    });
  }
  function setOpen(open, returnFocus) {
    panel.hidden = !open;
    button.setAttribute('aria-expanded', open ? 'true' : 'false');
    button.setAttribute('aria-label', open ? 'Close profile menu' : 'Open profile menu');
    if (!open) { collapseSections(); if (returnFocus) button.focus(); }
  }
  button.addEventListener('click', () => setOpen(panel.hidden));
  items.forEach(item => item.addEventListener('click', () => {
    const section = document.getElementById(item.dataset.section);
    const willOpen = section.hidden;
    collapseSections();
    section.hidden = !willOpen;
    item.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
  }));
  document.addEventListener('click', event => { if (!panel.hidden && !root.contains(event.target)) setOpen(false); });
  document.addEventListener('keydown', event => { if (event.key === 'Escape' && !panel.hidden) setOpen(false, true); });
})();
