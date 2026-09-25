FROM node:24-alpine
USER node
WORKDIR /app
COPY --chown=node:node server.js .
EXPOSE 10000
ENTRYPOINT ["node", "server.js"]
