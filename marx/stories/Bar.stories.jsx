import React from 'react';

import { Bar } from '../components/Bar';
import { Spacer } from '../components/Spacer';

const meta = {
  component: Bar,
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
