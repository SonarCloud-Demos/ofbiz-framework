import { createServer } from 'node:http';

const server = createServer((request, response) => {
  if (request.url === '/health') {
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end('{"status":"ready","emulator":"local-blob-port"}');
    return;
  }
  response.writeHead(501, { 'content-type': 'application/json' });
  response.end('{"error":"Blob semantics require Azurite or ephemeral Azure"}');
});
server.listen(10000, '0.0.0.0');
