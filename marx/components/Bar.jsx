import React from 'react';

const Bar = ({ children }) => {
  return <div style={{
    position: "fixed",
    bottom: 0,
    left: 0,
    right: 0,
    display: "flex",
    justifyContent: "space-between",
    width: "100%",
    padding: "1em 2em",
  }}>
    {children.map((child) => {
      return <div>{child}</div>;
    })}
  </div>;
};

export {
  Bar
};
