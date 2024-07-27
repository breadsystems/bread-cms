import {html} from 'lit-html';

import '../js/bread-bar.js';

const meta = {
  component: 'bread-bar',
};

export default meta;

export const Primary = () =>
  html`
    <bread-bar name="World" color="green"></bread-bar>
  `;
