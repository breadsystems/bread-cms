function setTheme(theme) {
  document.querySelector('html').setAttribute('data-theme', theme);
  localStorage.setItem('crust.theme', theme);
}

window.addEventListener('load', () => {
  const savedTheme = localStorage.getItem('crust.theme');
  if (savedTheme) {
    setTheme(savedTheme);
  } else {
    const prefersDarkTheme = window.matchMedia('(prefers-color-scheme: dark)').matches;
    setTheme(prefersDarkTheme ? 'dark' : 'light');
  }

  document
    .getElementById('toggle-theme')
    .addEventListener('click', () => {
      const currentTheme = document.querySelector('html').getAttribute('data-theme');
      setTheme(currentTheme === 'dark' ? 'light' : 'dark');
    });
});
