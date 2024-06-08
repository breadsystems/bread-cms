import React from 'react';

const EditorMenu = ({ tools }) => {
  console.log(tools);
  return (
    <div>
      {tools.map(({
        type,
        icon,
        content,
        tooltip,
        effect,
      }) => {
        return (
          <button
            key={type}
            onClick={effect}
            title={tooltip}
            data-icon={icon || "empty"}
          >
            {content}
          </button>
        );
      })}
    </div>
  );
};

export {EditorMenu};
