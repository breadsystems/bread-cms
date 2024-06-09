import React from 'react';
import styled from 'styled-components';

const Aside = styled.aside`
  background-color: var(--marx-color-background-slab);
  padding: 1em;
  border: var(--marx-border-box);
`;

function Box({children}) {
  return <Aside>
    {children}
  </Aside>;
}

export {Box};
