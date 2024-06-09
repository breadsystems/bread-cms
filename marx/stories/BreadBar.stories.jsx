import React from 'react';

import { BreadBar, Button, Spacer } from '../components';

const children = <>
  <div>Site Name</div>
  <Button>click me!</Button>
  <Button>another button</Button>
  <Spacer />
  <Button>Publish</Button>
</>;

const meta = {
  component: BreadBar,
  args: {
    children,
  },
  parameters: {
    controls: {
      exclude: ['themeVariant', 'children', 'settings'],
    },
  },
};

export default meta;

export const Base = {
  args: {
    settings: {},
  },
};

export const DarkTheme = {
  args: {
    settings: {
      theme: {
        variant: 'dark',
      },
    },
  },
};

export const PositionedTop = {
  args: {
    settings: {
      bar: {
        position: 'top',
      },
    },
  },
};

export const PositionedRight = {
  args: {
    settings: {
      bar: {
        position: 'right',
      },
    },
  },
};

export const PositionedLeft = {
  args: {
    settings: {
      bar: {
        position: 'left',
      },
    },
  },
};
