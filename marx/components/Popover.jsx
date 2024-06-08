import React, {useState} from 'react';
import styled from 'styled-components';
import * as Rp from '@radix-ui/react-popover';

const PopoverContent = styled.div`
  --bar-height: 54px; // TODO

  position: fixed;
  bottom: 0;
  left: 0;

  [data-radix-popper-content-wrapper] {
    position: absolute !important;
    transform: translate(0, calc(-1 * (var(--bar-height) + 100%))) !important;
  }
`;

function Popover({trigger, children}) {
  const [open, setOpen] = useState(false);

  return <Rp.Root open={open}>
    <Rp.Portal>
      {open && (
        <PopoverContent>
          <Rp.Content>
            {children}
          </Rp.Content>
        </PopoverContent>
      )}
    </Rp.Portal>
    <Rp.Trigger asChild onClick={() => setOpen(!open)}>
      {trigger}
    </Rp.Trigger>
  </Rp.Root>;
}

export {Popover};
