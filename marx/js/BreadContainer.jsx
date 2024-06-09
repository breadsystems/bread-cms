import React from 'react';
import styled, {css, ThemeProvider} from 'styled-components';

import {darkTheme, lightTheme} from './theme';
import {BreadStyle} from './BreadStyle';
import {BreadBar} from './BreadBar';

function BreadContainer({
  settings = {},
  themeVariants = {},
  children,
}) {
  const {theme: {variant} = {variant: 'light'}} = settings;
  const theme = themeVariants[variant] || lightTheme;

  return <ThemeProvider theme={theme}>
    <BreadStyle />
    <BreadBar
      settings={settings}
    >
      {children}
    </BreadBar>
  </ThemeProvider>
}

export {BreadContainer};
