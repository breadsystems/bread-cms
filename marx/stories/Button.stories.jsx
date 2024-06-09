import {userEvent, within} from '@storybook/test';

import {Button} from '../js/Button';

const meta = {
  component: Button,
  parameters: {
    layout: 'centered',
  },
  args: {
    children: 'Button',
  },
};

export default meta;

export const Base = {};

export const Disabled = {
  args: {
    disabled: true,
  },
};

export const Focused = {
  play: async ({canvasElement}) => {
    const canvas = within(canvasElement);
    const button = canvas.getByText('Button');
    button.focus();
  },
};
