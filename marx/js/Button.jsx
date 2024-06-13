import React from 'react';
import styled from 'styled-components';

const StyledButton = styled.button`
  padding: ${({theme}) => theme.spacing.gap_small} ${({theme}) => theme.spacing.gap_large};

  font-weight: 600;
  font-family: var(--marx-font-copy);

  color: var(--marx-color-text-main);
  background: var(--marx-color-background-main);
  border: var(--marx-border-input);

  &:hover {
    cursor: pointer;
    border-color: var(--marx-color-accent-dark);
  }
  &:focus {
    outline: var(--marx-border-width) solid var(--marx-color-accent-detail);
  }
  &:active:not(:disabled) {
    color: var(--marx-color-text-dark);
    background: var(--marx-color-background-dark);
    outline: var(--marx-border-width) solid var(--marx-color-accent-dark);
  }
  &:disabled {
    cursor: not-allowed;
    color: var(--marx-color-text-desaturated);
    background: var(--marx-color-background-desaturated);
    border-color: var(--marx-color-accent-desaturated);
  }
`;

function Button({
  children,
  disabled,
  onClick,
  buttonRef = null,
}) {
  return <StyledButton
    disabled={disabled}
    onClick={onClick}
    ref={buttonRef}
  >
    {children}
  </StyledButton>;
}

export {Button};
