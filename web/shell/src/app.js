const app = document.querySelector('#app');

function renderError(message) {
  app.innerHTML = `<h1>Temporarily unavailable</h1><p role="alert">${message}</p><p><a href="/legacy/">Continue to legacy OFBiz</a></p>`;
}

async function render() {
  try {
    const [routes, response] = await Promise.all([fetch('/route-manifest.json').then(r => r.json()), fetch('/api/platform')]);
    if (!response.ok) throw new Error('The platform service did not respond.');
    const route = routes.find(item => item.path === '/modern/platform');
    const status = await response.json();
    app.innerHTML = `<div class="marker" data-experience="${route.marker.dataExperience}" role="status">${route.marker.label}</div><h1>Platform foundation</h1><p>The hybrid shell and sample service are healthy.</p><dl><dt>Service</dt><dd>${status.service}</dd><dt>Correlation ID</dt><dd>${status.correlationId}</dd></dl>`;
  } catch (error) { renderError(error.message); }
}

window.addEventListener('error', event => renderError(event.message));
render();
