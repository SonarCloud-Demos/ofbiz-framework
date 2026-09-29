const app = document.querySelector('#app');

function text(value) {
  const element = document.createElement('span');
  element.textContent = value ?? '';
  return element.innerHTML;
}

function renderError(message) {
  app.innerHTML = `<h1>Temporarily unavailable</h1><p role="alert">${text(message)}</p><p><a href="/auth/legacy">Continue to legacy OFBiz</a></p>`;
}

function renderLogin(returnPath) {
  app.innerHTML = `<h1>Sign in required</h1><p>This route requires the development-user role.</p><p><a href="/auth/login?return=${encodeURIComponent(returnPath)}">Sign in</a></p>`;
}

async function json(url) {
  const response = await fetch(url, {headers: {'X-Correlation-ID': crypto.randomUUID()}});
  if (!response.ok) {
    if (response.status === 404) throw new Error('The requested catalog item was not found.');
    throw new Error('The catalog service did not respond.');
  }
  return response.json();
}

function productCards(items) {
  if (!items.length) return '<p class="empty" role="status">No products found.</p>';
  return `<ul class="product-grid">${items.map(item => `<li><button class="product-card" data-product-id="${text(item.productId)}"><strong>${text(item.name)}</strong><span>${text(item.description || 'No description available.')}</span><small>${text(item.productId)}</small></button></li>`).join('')}</ul>`;
}

async function showProduct(productId) {
  const item = await json(`/bff/catalog/products/${encodeURIComponent(productId)}/summary`);
  const detail = document.querySelector('#product-detail');
  detail.innerHTML = `<h2 tabindex="-1">${text(item.name)}</h2><p>${text(item.description || 'No description available.')}</p><dl><dt>Product ID</dt><dd>${text(item.productId)}</dd><dt>Type</dt><dd>${text(item.productType)}</dd></dl>`;
  detail.querySelector('h2').focus();
}

function bindProductCards() {
  document.querySelectorAll('[data-product-id]').forEach(button => button.addEventListener('click', () => {
    showProduct(button.dataset.productId).catch(error => renderError(error.message));
  }));
}

async function renderCatalog(identity, route) {
  if (!identity.catalogRouteEnabled) { location.assign(route.fallback); return; }
  app.innerHTML = `<div class="preview" role="status">Migration preview — not yet production cutover</div>
    <h1>Product catalog</h1>
    <p>Browse the extracted catalog projection or search by product name.</p>
    <form id="catalog-search" role="search"><label for="catalog-query">Search products</label><div class="search-row"><input id="catalog-query" name="q" required maxlength="200"><button type="submit">Search</button></div></form>
    <section aria-labelledby="catalog-heading"><h2 id="catalog-heading">Loading catalog…</h2><div id="category-links"></div><div id="catalog-results" aria-live="polite"></div><button id="next-page" type="button" hidden>Next page</button></section>
    <aside id="product-detail" aria-live="polite"></aside>`;

  const heading = document.querySelector('#catalog-heading');
  const categories = document.querySelector('#category-links');
  const results = document.querySelector('#catalog-results');
  const next = document.querySelector('#next-page');
  let mode = 'browse';
  let cursor = null;
  let query = '';
  let categoryId = 'CATALOG1';

  async function loadCategory(id) {
    mode = 'browse'; cursor = null; categoryId = id;
    const [category, page] = await Promise.all([
      json(`/bff/catalog/categories/${encodeURIComponent(id)}`),
      json(`/bff/catalog/categories/${encodeURIComponent(id)}/products?limit=20&sort=catalog`)
    ]);
    heading.textContent = category.name;
    categories.innerHTML = category.children.length ? `<nav aria-label="Subcategories">${category.children.map(child => `<button type="button" data-category-id="${text(child.categoryId)}">${text(child.name)}</button>`).join('')}</nav>` : '';
    categories.querySelectorAll('[data-category-id]').forEach(button => button.addEventListener('click', () => loadCategory(button.dataset.categoryId).catch(error => renderError(error.message))));
    showPage(page);
  }

  function showPage(page, append = false) {
    results.innerHTML = append ? results.innerHTML + productCards(page.items) : productCards(page.items);
    cursor = page.nextCursor || null;
    next.hidden = !cursor;
    bindProductCards();
  }

  document.querySelector('#catalog-search').addEventListener('submit', async event => {
    event.preventDefault();
    query = new FormData(event.currentTarget).get('q').trim();
    if (!query) return;
    mode = 'search'; cursor = null; heading.textContent = `Search results for “${query}”`; categories.innerHTML = '';
    showPage(await json(`/bff/catalog/search?q=${encodeURIComponent(query)}&limit=20&sort=name`));
  });

  next.addEventListener('click', async () => {
    if (!cursor) return;
    const url = mode === 'search'
      ? `/bff/catalog/search?q=${encodeURIComponent(query)}&limit=20&sort=name&cursor=${encodeURIComponent(cursor)}`
      : `/bff/catalog/categories/${encodeURIComponent(categoryId)}/products?limit=20&sort=catalog&cursor=${encodeURIComponent(cursor)}`;
    showPage(await json(url), true);
  });

  await loadCategory(categoryId);
}

async function renderPlatform(identity, route) {
  if (!identity.platformRouteEnabled) { location.assign(route.fallback); return; }
  const status = await json(route.bff);
  app.innerHTML = `<div class="marker" data-experience="modern" role="status">Modern experience</div><h1>Platform foundation</h1><p>The hybrid shell and sample service are healthy.</p><dl><dt>Signed in as</dt><dd>${text(identity.user)}</dd><dt>Service</dt><dd>${text(status.service)}</dd><dt>Correlation ID</dt><dd>${text(status.correlationId)}</dd></dl><button id="logout" type="button">Sign out</button>`;
  document.querySelector('#logout').addEventListener('click', async () => {
    await fetch('/auth/logout', {method: 'POST', headers: {'X-CSRF-Token': identity.csrfToken}});
    location.assign('/modern/platform');
  });
}

async function render() {
  try {
    const routes = await fetch('/route-manifest.json').then(response => response.json());
    const route = routes.find(item => item.path === location.pathname);
    if (!route || !['modern', 'candidate'].includes(route.experience)) throw new Error('The route is not enabled.');
    const identity = await fetch('/auth/session').then(response => response.status === 401 ? null : response.json());
    if (!identity) { renderLogin(location.pathname); return; }
    if (route.path === '/modern/catalog') await renderCatalog(identity, route);
    else await renderPlatform(identity, route);
  } catch (error) { renderError(error.message); }
}

window.addEventListener('error', event => renderError(event.message));
render();
