import {createGlobalStyle} from 'styled-components';

const BreadStyle = createGlobalStyle`
  * {
    box-sizing: border-box;
  }
  :root {
    --marx-color-text-main: ${({theme}) => theme.color.text_main};
    --marx-color-bg-main: ${({theme}) => theme.color.background_main};
    --marx-color-accent-main: ${({theme}) => theme.color.accent_main};
  }
`;

export {BreadStyle};