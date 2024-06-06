import React from 'react';

function Button({label, onClick}) {
  return <button onClick={onClick}>{label}</button>
}

export {Button};
