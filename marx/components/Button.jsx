import React from 'react';
import styled from 'styled-components';

const StyledButton = styled.button`
  color: var(--marx-color-text-main);
`;

function Button({children, onClick}) {
  return <StyledButton onClick={onClick}>{children}</StyledButton>;
}

export {Button};
