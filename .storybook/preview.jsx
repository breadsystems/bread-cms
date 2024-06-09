import React from 'react';
import {ThemeProvider} from 'styled-components';

import {BreadStyle, darkTheme, lightTheme} from '../marx/components';

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
  argTypes: {
    themeVariant: {
      control: 'select',
      description: 'Marx editor theme variant',
      options: ['dark', 'light'],
    },
  },
  decorators: [
    (Story, { args }) => {
      if (args.settings) {
        return <Story />;
      }

      const theme = themeVariants[args.themeVariant] || lightTheme;

      return <ThemeProvider theme={theme}>
        <BreadStyle />
        <Story />
      </ThemeProvider>;
    },
  ],
};

export default preview;
