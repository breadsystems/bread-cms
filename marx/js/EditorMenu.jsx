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
      }, idx) => {
        return (
          <button
            key={idx}
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
