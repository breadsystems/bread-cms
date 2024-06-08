import React from 'react';
import styled, {ThemeProvider} from 'styled-components';

import {darkTheme, lightTheme} from './theme';
import {BreadStyle} from './BreadStyle';
import {Popover} from './Popover';
import {Button} from './Button';

function BarSection(children) {
  return <div>{children}</div>;
}

function PopoverSection({buttonProps, content}) {
  const button = <Button {...buttonProps} />;
  return <Popover trigger={button}>{content}</Popover>;
}

const Styled = styled.div`
  position: fixed;
  bottom: 0;
  left: 0;
  display: flex;
  justify-content: space-between;
  width: 100%;
  padding: 1em;
  gap: 2em;

  line-height: 1.5;
  color: var(--marx-color-text-main);
  background: var(--marx-color-bg-main);
  border-top: 2px dashed var(--marx-color-accent-main);
`;

const themeVariants = {
  dark: darkTheme,
  light: lightTheme,
};

function BreadBar({
  settings = {},
  children,
}) {
  const {
    theme: {variant} = {variant: 'dark'},
  } = settings;
  const theme = themeVariants[variant] || lightTheme;
  console.log(variant, theme);

  return <ThemeProvider theme={theme}>
    <BreadStyle />
    <Styled data-bread-theme-variant={variant}>
      {children}
    </Styled>
  </ThemeProvider>;
}

export {BarSection, PopoverSection, BreadBar};
