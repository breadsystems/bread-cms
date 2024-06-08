import React from 'react';
import styled from 'styled-components';

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
`;

function BreadBar({children}) {
  return <>
    <BreadStyle />
    <Styled>
      {children}
    </Styled>
  </>;
}

export {BarSection, PopoverSection, BreadBar};
