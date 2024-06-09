import React from 'react';
import styled from 'styled-components';

const Aside = styled.aside`
  background-color: var(--marx-color-background-slab);
  padding: 1em;
  border: var(--marx-border-box);
`;

function SettingsBox() {
  return <Aside>
    <h2>SETTINGS GO HERE</h2>
    <section>Section one</section>
    <section>Section two</section>
  </Aside>;
}

export {SettingsBox};
