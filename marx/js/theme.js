/* CSS HEX
--lavender-pink: #f0a6caff;
--lavender-blush: #f0e6efff;
--tiffany-blue: #7cdedcff;
--glaucous: #7284a8ff;
--charcoal: #474954ff;
 */
const solarizedDarkPalette = {
  base03:  '#002b36',
  base02:  '#073642',
  base01:  '#586e75',
  base00:  '#657b83',
  base0:   '#839496',
  base1:   '#93a1a1',
  base2:   '#eee8d5',
  base3:   '#fdf6e3',
  yellow:  '#b58900',
  orange:  '#cb4b16',
  red:     '#dc322f',
  magenta: '#d33682',
  violet:  '#6c71c4',
  blue:    '#268bd2',
  cyan:    '#2aa198',
  green:   '#859900',
};
const solarizedLightPalette = {
  // TODO
};

const baseTheme = {
  type: {
    font_heading: 'serif',
    font_body: 'sans-serif',
    font_size_basis: '16px',
  },
  spacing: {
    gap_tiny:     '0.25em',
    gap_small:    '0.5em',
    gap_standard: '1em',
    gap_large:    '2em',
    gap_spacious: '4em',
  },
  border: {
    width: '2px',
    style_box: 'dashed',
    style_input: 'solid',
  },
};

const darkTheme = {
  ...baseTheme,
  variant: 'dark',
  color: {
    text_main: solarizedDarkPalette.base3,
    background_main: solarizedDarkPalette.base03,
  },
};

const lightTheme = {
  ...baseTheme,
  variant: 'light',
  color: {
    text_main: '#2c1264',
    text_dark: '#120234',
    text_desaturated: '#373440',
    background_main: '#ffc9a9',
    background_slab: '#ede2b0',
    background_dark: '#c4b04b',
    background_desaturated: '#d9d7cd',
    accent_main: '#ce7575',
    accent_detail: '#c4b5a9',
    accent_dark: '#7b6d63',
    accent_desaturated: '#7b6d63',
  },
};

export {
  solarizedDarkPalette,
  solarizedLightPalette,
  darkTheme,
  lightTheme,
};
