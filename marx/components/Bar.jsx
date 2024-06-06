import React from 'react';

import {Popover} from './Popover';
import {Button} from './Button';

function BarSection(children) {
  return <div>{children}</div>;
}

function PopoverSection({buttonProps, content}) {
  const button = <Button {...buttonProps} />;
  return <Popover trigger={button}>{content}</Popover>;
}

function Bar({children}) {
  return <div style={{
    position: "fixed",
    bottom: 0,
    left: 0,
    right: 0,
    display: "flex",
    justifyContent: "space-between",
    width: "100%",
    padding: "1em 2em",
    gap: "2em",
  }}>
    {children}
  </div>;
}

export {BarSection, PopoverSection, Bar};
