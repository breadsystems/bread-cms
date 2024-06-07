import React from 'react';
import styled from 'styled-components';

const StyledButton = styled.button`
  color: green;
`;

function Button({label, onClick}) {
  return <StyledButton onClick={onClick}>{label}</StyledButton>;
}

export {Button};
