import React from 'react';
import styled from 'styled-components';

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
  box-sizing: border-box;
  position: fixed;
  bottom: 0;
  left: 0;
  display: flex;
  justify-content: space-between;
  width: 100%;
  padding: 1em;
  gap: 2em;

  line-height: 1.5;
`;

function BreadBar({children}) {
  return <Styled>
    {children}
  </Styled>;
}

export {BarSection, PopoverSection, BreadBar};
