import {createGlobalStyle} from 'styled-components';

const BreadStyle = createGlobalStyle`
  * {
    box-sizing: border-box;
  }
  :root {
    --marx-color-text-main:        ${({theme}) => theme.color.text_main};
    --marx-color-text-dark:        ${({theme}) => theme.color.text_dark};
    --marx-color-text-desaturated: ${({theme}) => theme.color.text_desaturated};

    --marx-color-background-main:        ${({theme}) => theme.color.background_main};
    --marx-color-background-slab:        ${({theme}) => theme.color.background_slab};
    --marx-color-background-dark:        ${({theme}) => theme.color.background_dark};
    --marx-color-background-desaturated: ${({theme}) => theme.color.background_desaturated};

    --marx-color-accent-main:        ${({theme}) => theme.color.accent_main};
    --marx-color-accent-detail:      ${({theme}) => theme.color.accent_detail};
    --marx-color-accent-dark:        ${({theme}) => theme.color.accent_dark};
    --marx-color-accent-desaturated: ${({theme}) => theme.color.accent_desaturated};

    --marx-border-width:       ${({theme}) => theme.border.width};
    --marx-border-style-box:   ${({theme}) => theme.border.style_box};
    --marx-border-style-input: ${({theme}) => theme.border.style_input};

    --marx-font-heading: ${({theme}) => theme.type.font_heading};
    --marx-font-copy:    ${({theme}) => theme.type.font_copy};
  }
`;

export {BreadStyle};
