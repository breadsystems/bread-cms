import {LitElement, css, html} from 'lit';

export class Hello extends LitElement {
  static properties = {
    name: {},
  };

  static styles = css`
    :host {
      color: blue;
    }
  `;

  constructor(args) {
    super();
    this.name = this.getAttribute('name');
  }

  render() {
    return html`
      <p>Hello, ${this.name}!</p>
    `;
  }
}

customElements.define('bread-bar', Hello);
