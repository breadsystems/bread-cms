import React from 'react';
import styled from 'styled-components';

const Aside = styled.aside`
  background-color: pink;
  padding: 1em;
`;

function SettingsBox() {
  return <Aside>
    <h2>SETTINGS GO HERE</h2>
    <section>Section one</section>
    <section>Section two</section>
  </Aside>;
}

export {SettingsBox};
