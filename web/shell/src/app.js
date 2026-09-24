const app = document.querySelector('#app');

function renderError(message) {
  app.innerHTML = `<h1>Temporarily unavailable</h1><p role="alert">${text(message)}</p><p><a href="/auth/legacy">Continue to legacy OFBiz</a></p>`;
}

function renderLogin() {
  app.innerHTML = `<h1>Sign in required</h1><p>This route requires the development-user role.</p><p><a href="/auth/login?return=/modern/platform">Sign in</a></p>`;
}

function text(value) {
  const element = document.createElement('span');
  element.textContent = value;
  return element.innerHTML;
}

async function render() {
  try {
    const routes = await fetch('/route-manifest.json').then(r => r.json());
    const route = routes.find(item => item.path === '/modern/platform');
    if (!route || route.experience !== 'modern') throw new Error('The route is not enabled.');
    const identity = await fetch('/auth/session').then(r => r.status === 401 ? null : r.json());
    if (!identity) { renderLogin(); return; }
    if (!identity.platformRouteEnabled) { location.assign(route.fallback); return; }
    const response = await fetch(route.bff, {headers: {'X-Correlation-ID': crypto.randomUUID()}});
    if (!response.ok) throw new Error('The platform service did not respond.');
    const status = await response.json();
    app.innerHTML = `<div class="marker" data-experience="modern" role="status">Modern experience</div><h1>Platform foundation</h1><p>The hybrid shell and sample service are healthy.</p><dl><dt>Signed in as</dt><dd>${text(identity.user)}</dd><dt>Service</dt><dd>${text(status.service)}</dd><dt>Correlation ID</dt><dd>${text(status.correlationId)}</dd></dl><button id="logout" type="button">Sign out</button>`;
    document.querySelector('#logout').addEventListener('click', async () => {
      await fetch('/auth/logout', {method: 'POST', headers: {'X-CSRF-Token': identity.csrfToken}});
      location.assign('/modern/platform');
    });
  } catch (error) { renderError(error.message); }
}

window.addEventListener('error', event => renderError(event.message));
render();
