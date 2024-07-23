import React from 'react';
import styled from 'styled-components';

import {StyledBox} from './Box';

const Styled = styled(StyledBox)`
`;

function SettingsBox() {
  return <Styled>
    <h2>SETTINGS GO HERE</h2>
    <section>Section one</section>
    <section>Section two</section>
  </Styled>;
}

export {SettingsBox};
