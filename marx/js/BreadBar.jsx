import React, {useContext} from 'react';
import styled, {css, ThemeContext} from 'styled-components';

import {Popover} from './Popover';
import {Button} from './Button';

const StyledHeadingSection = styled.div`
  font-family: var(--marx-font-heading);
  font-size: 1.5em;
  font-weight: 600;
`;

function HeadingSection({children}) {
  return <StyledHeadingSection>{children}</StyledHeadingSection>;
}

const StyledBarSection = styled.div`
  font-family: var(--marx-font-copy);
`;

function BarSection({children}) {
  return <StyledBarSection>{children}</StyledBarSection>;
}

function PopoverSection({buttonProps, content}) {
  const button = <Button {...buttonProps} />;
  return <Popover trigger={button}>{content}</Popover>;
}

const positionedTop = css`
  top: 0;
  width: 100%;
  border-width: 0 0 var(--marx-border-width) 0;
`;

const positionedRight = css`
  flex-direction: column;
  top: 0;
  right: 0;
  height: 100%;
  border-width: 0 0 0 var(--marx-border-width);
`;

const positionedBottom = css`
  bottom: 0;
  left: 0;
  width: 100%;
  border-width: var(--marx-border-width) 0 0 0;
`;

const positionedLeft = css`
  flex-direction: column;
  top: 0;
  left: 0;
  height: 100%;
  border-width: 0 var(--marx-border-width) 0 0;
`;

const Styled = styled.div`
  position: fixed;
  ${({position}) => ({
    top: positionedTop,
    right: positionedRight,
    bottom: positionedBottom,
    left: positionedLeft,
  }[position] || positionedBottom)};

  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1em;
  gap: 2em;

  font-family: var(--marx-font-copy);

  line-height: 1.5;
  color: var(--marx-color-text-main);
  background: var(--marx-color-background-slab);
  border-style: var(--marx-border-style-box);
  border-color: var(--marx-color-accent-main);
`;

function BreadBar({
  settings = {},
  children,
}) {
  const {
    bar: {position} = {position: 'bottom'},
  } = settings;
  const theme = useContext(ThemeContext);

  return (
    <Styled
      position={position}
      data-bread-theme-variant={theme.variant}
    >
      {children}
    </Styled>
  );
}

export {BarSection, HeadingSection, PopoverSection, BreadBar};
