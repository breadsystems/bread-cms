import React, {useState} from 'react';
import styled from 'styled-components';
import * as Rp from '@radix-ui/react-popover';

const PopoverContent = styled.div`
  --bar-height: 1em; // TODO

  position: absolute;

  [data-radix-popper-content-wrapper] {
    position: absolute !important;
    transform: translate(0, calc(-1 * (var(--bar-height) + 100%))) !important;
    transform-origin: bottom left;
  }
`;

function Popover({trigger, children}) {
  const [open, setOpen] = useState(false);

  return <Rp.Root open={open}>
    {open && (
      <PopoverContent>
        <Rp.Content>
          {children}
        </Rp.Content>
      </PopoverContent>
    )}
    <Rp.Trigger asChild onClick={() => setOpen(!open)}>
      {trigger}
    </Rp.Trigger>
  </Rp.Root>;
}

export {Popover};
