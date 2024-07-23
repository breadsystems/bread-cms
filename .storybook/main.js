/** @type { import('@storybook/react-vite').StorybookConfig } */
const config = {
  stories: [
    "../marx/stories/*.stories.js",
  ],
  addons: [
    "@storybook/addon-essentials",
    "@chromatic-com/storybook",
  ],
  framework: {
    name: "@storybook/web-components-vite",
    options: {},
  },
  core: {
    builder: '@storybook/builder-vite',
  },
};

export default config;
