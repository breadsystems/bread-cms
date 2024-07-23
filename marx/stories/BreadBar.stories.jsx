import React from 'react';

import {BreadBar, BarSection, HeadingSection} from '../js/BreadBar';
import {Button} from '../js/Button';
import {Spacer} from '../js/Spacer';

const children = <>
  <HeadingSection>Site Name</HeadingSection>
  <Button>Click me!</Button>
  <Button>Another button</Button>
  <Spacer />
  <BarSection>
    <Button>Publish</Button>
  </BarSection>
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
