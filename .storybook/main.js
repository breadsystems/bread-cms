/** @type { import('@storybook/react-vite').StorybookConfig } */
const config = {
  stories: [
    "../marx/components/**/stories.@(js|jsx)",
  ],
  addons: [
    "@storybook/addon-onboarding",
    "@storybook/addon-links",
    "@storybook/addon-essentials",
    "@chromatic-com/storybook",
    "@storybook/addon-interactions",
  ],
  framework: {
    name: "@storybook/react-vite",
    options: {},
  },
  core: {
    builder: '@storybook/builder-vite',
  },
};
export default config;
