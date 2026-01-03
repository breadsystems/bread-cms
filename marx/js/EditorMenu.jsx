import React from 'react';

const EditorMenu = ({ tools }) => {
  return (
    <div data-marx-menu data-bread>
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
            data-marx-button={true}
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
