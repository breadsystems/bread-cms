import React from 'react';

import { BreadBar } from '../components/BreadBar';
import { Spacer } from '../components/Spacer';

const meta = {
  component: BreadBar,
};

export default meta;

export const Base = {
  args: {
    children: <>
      <div>Site Name</div>
      <button>click me!</button>
      <button>another button</button>
      <Spacer />
      <button>Publish</button>
    </>,
  },
};
