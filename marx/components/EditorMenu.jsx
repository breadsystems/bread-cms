import React from 'react';

const EditorMenu = ({ tools }) => {
  console.log(tools);
  return (
    <div>
      {tools.map(({ type, label, tooltip, effect }) => {
        return (
          <button
            key={type}
            onClick={effect}
            title={tooltip}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
};

export default EditorMenu;
