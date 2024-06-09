/** @type { import('@storybook/react').Preview } */
const preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
      exclude: ['children', 'settings'],
    },
    layout: 'fullscreen',
  },
};

export default preview;
