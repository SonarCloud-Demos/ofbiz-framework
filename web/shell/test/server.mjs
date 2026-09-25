import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {dirname, join} from 'node:path';

const source = join(dirname(fileURLToPath(import.meta.url)), '..', 'src');
const product = {
  productId: 'GZ-1000', name: 'Tiny Gizmo', description: 'A deterministic catalog product.',
  imageUrl: null, productType: 'FINISHED_GOOD', virtual: false, variant: false
};

function send(response, status, type, body) {
  response.writeHead(status, {'Content-Type': type, 'Cache-Control': 'no-store'});
  response.end(body);
}

function scenario(request) {
  const referer = request.headers.referer || 'http://127.0.0.1:4173/';
  return new URL(referer).searchParams.get('scenario') || 'healthy';
}

async function staticAsset(response, name, type) {
  send(response, 200, type, await readFile(join(source, name)));
}

createServer(async (request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1:4173');
  try {
    if (url.pathname === '/health') return send(response, 200, 'text/plain', 'ok');
    if (url.pathname === '/app.js') return await staticAsset(response, 'app.js', 'text/javascript');
    if (url.pathname === '/styles.css') return await staticAsset(response, 'styles.css', 'text/css');
    if (url.pathname === '/route-manifest.json') return await staticAsset(response, 'route-manifest.json', 'application/json');
    if (url.pathname === '/modern/catalog') return await staticAsset(response, 'index.html', 'text/html');
    if (url.pathname === '/auth/legacy') {
      return send(response, 200, 'text/html', '<!doctype html><html lang="en"><title>Legacy</title><h1>Legacy OFBiz</h1></html>');
    }
    if (url.pathname === '/auth/session') {
      const enabled = scenario(request) !== 'disabled';
      return send(response, 200, 'application/json', JSON.stringify({
        authenticated: true, user: 'browser-test', csrfToken: 'test',
        platformRouteEnabled: true, catalogRouteEnabled: enabled
      }));
    }
    if (url.pathname.startsWith('/bff/catalog/') && scenario(request) === 'failure') {
      return send(response, 502, 'application/json', JSON.stringify({error: 'upstream_unavailable', correlationId: 'test'}));
    }
    if (url.pathname === '/bff/catalog/categories/CATALOG1') {
      return send(response, 200, 'application/json', JSON.stringify({
        categoryId: 'CATALOG1', name: 'Demo catalog', description: null, imageUrl: null,
        children: [{categoryId: '100', name: 'Gizmos', description: null, imageUrl: null}], correlationId: 'test'
      }));
    }
    if (url.pathname === '/bff/catalog/categories/CATALOG1/products') {
      return send(response, 200, 'application/json', JSON.stringify({items: [product], nextCursor: null, correlationId: 'test'}));
    }
    if (url.pathname === '/bff/catalog/search') {
      return send(response, 200, 'application/json', JSON.stringify({items: [product], nextCursor: null, correlationId: 'test'}));
    }
    if (url.pathname === '/bff/catalog/products/GZ-1000/summary') {
      return send(response, 200, 'application/json', JSON.stringify(product));
    }
    send(response, 404, 'application/json', JSON.stringify({error: 'not_found'}));
  } catch (error) {
    send(response, 500, 'application/json', JSON.stringify({error: 'fixture_failure'}));
  }
}).listen(4173, '127.0.0.1');
