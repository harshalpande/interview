import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

const root = ReactDOM.createRoot(
  document.getElementById('root') as HTMLElement
);

root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// Optional: Register service worker
// if ('serviceWorker' in navigator) {
//   navigator.serviceWorker.register('/sw.js');
// }

