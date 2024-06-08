import {createGlobalStyle} from 'styled-components';

const BreadStyle = createGlobalStyle`
  * {
    box-sizing: border-box;
  }
  :root {
    --brd-color-text-main: red;
  }
`;

export {BreadStyle};
