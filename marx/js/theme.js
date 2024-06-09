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
  },
  spacing: {
    gap_small: '0.25em',
  },
  border: {
    box_style: 'dashed',
    box_width: '2px',
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
    text_main: '#171818',
    background_main: '#e3d8a2',
    accent_main: '#ce7575',
  },
};

export {
  solarizedDarkPalette,
  solarizedLightPalette,
  darkTheme,
  lightTheme,
};
