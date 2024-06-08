import React from 'react';

import { BreadBar } from '../components/BreadBar';
import { Button } from '../components/Button';
import { Spacer } from '../components/Spacer';

const meta = {
  component: BreadBar,
};

export default meta;

export const Base = {
  args: {
    children: <>
      <div>Site Name</div>
      <Button>click me!</Button>
      <Button>another button</Button>
      <Spacer />
      <Button>Publish</Button>
    </>,
  },
};
