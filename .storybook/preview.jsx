import React from 'react';
import {ThemeProvider} from 'styled-components';
import {withThemeFromJSXProvider} from '@storybook/addon-themes';

import {darkTheme, lightTheme} from '../marx/components/theme';
import {BreadStyle} from '../marx/components/BreadStyle';

const themeVariants = {
  dark: darkTheme,
  light: lightTheme,
};

/** @type { import('@storybook/react').Preview } */
const preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
      exclude: ['children', 'settings'],
    },
    layout: 'fullscreen',
  },
  decorators: [
    withThemeFromJSXProvider({
      themes: {
        light: lightTheme,
        dark: darkTheme,
      },
      defaultTheme: 'light',
      Provider: ThemeProvider,
      GlobalStyles: BreadStyle,
    }),
  ],
};

export default preview;
