export function defineOnce(name, constructor, options) {
  const defined = customElements.get(name);
  if (typeof defined === 'undefined') {
    customElements.define(name, constructor, options);
  }
}
