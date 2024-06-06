import React from 'react';

// TODO util
function Spacer() {
  return <div style={{
    flexGrow: '100%',
  }}></div>
}

function SiteNameSection({siteName}) {
  return <div>{siteName}</div>
}

function SettingsSection({label, onClick}) {
  return <div>
    <button onClick={onClick}>{label}</button>
  </div>;
}

function MediaLibrarySection({label, onClick}) {
  return <div>
    <button onClick={onClick}>{label}</button>
  </div>;
}

function SaveButtonSection() {
  return <div>
    SAVE
  </div>;
}

function Bar({children}) {
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
    {children}
  </div>;
}

export {
  Spacer,
  SiteNameSection,
  SettingsSection,
  MediaLibrarySection,
  SaveButtonSection,
  Bar,
};
