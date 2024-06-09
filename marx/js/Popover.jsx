import React, {useState} from 'react';
import styled from 'styled-components';
import * as Rp from '@radix-ui/react-popover';

import {Button} from './Button';

const PopoverContent = styled.div`
  --bar-height: 58px; // TODO

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
          <Rp.Content
            onFocusOutside={() => setOpen(false)}
            onInteractOutside={() => setOpen(false)}
            onEscapeKeyDown={() => setOpen(false)}
            onPointerDownOutside={() => setOpen(false)}
          >
            <div>
              <Rp.Close asChild>
                <Button onClick={() => setOpen(false)}>Close</Button>
              </Rp.Close>
            </div>
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
