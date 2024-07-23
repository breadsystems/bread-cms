import React from 'react';
import styled from 'styled-components';

const StyledBox = styled.aside`
  display: flex;
  flex-flow: column wrap;
  gap: ${({theme}) => theme.spacing.gap_standard};

  color: var(--marx-color-text-main);
  background-color: var(--marx-color-background-slab);
  padding: 1em;
  border: var(--marx-border-box);
`;

function Box({children}) {
  return <StyledBox>
    {children}
  </StyledBox>;
}

export {StyledBox, Box};
