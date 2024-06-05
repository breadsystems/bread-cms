import React from 'react';

const MarxEditor = ({ children }) => {
  console.log(children);
  return <div>
    <span>start</span>
    {children.length}
    <span>end</span>
  </div>;
};

export default MarxEditor;
