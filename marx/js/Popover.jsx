import React, {forwardRef, useState} from 'react';
import styled from 'styled-components';
import * as Rp from '@radix-ui/react-popover';

import {Button} from './Button';

const PopoverButton = forwardRef(function PopoverButton(props, ref) {
  return <Button {...props} buttonRef={ref} />;
});

function Popover({buttonProps, content}) {
  return <Rp.Root>
    <Rp.Portal>
      <Rp.Content>
        <div>
          <Rp.Close asChild>
            <Button>Close</Button>
          </Rp.Close>
        </div>
        {content}
      </Rp.Content>
    </Rp.Portal>
    <Rp.Trigger asChild>
      <PopoverButton {...buttonProps} />
    </Rp.Trigger>
  </Rp.Root>;
}

export {Popover};
