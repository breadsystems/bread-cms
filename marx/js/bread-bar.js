import {LitElement, css, html} from 'lit';
import {styleMap} from 'lit/directives/style-map.js';

//import {defineOnce} from './util';

export class BreadBar extends LitElement {
  static properties = {
    name: {},
    color: {},
    styles: {},
  };

  constructor(args) {
    super();
    this.name = this.getAttribute('name') ?? 'World';
    this.color = this.getAttribute('color') ?? 'blue';
    this.styles = {
      color: this.color,
      fontWeight: 600,
    };
  }

  render() {
    return html`
      <div style="${styleMap(this.styles)}">Hello, ${this.name}!</div>
    `;
  }
}

customElements.define('bread-bar', BreadBar);
