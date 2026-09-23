# syntax=docker/dockerfile:1.7
ARG STATIC_WEB_IMAGE
FROM ${STATIC_WEB_IMAGE}
COPY web/template-route/nginx.conf /etc/nginx/conf.d/default.conf
COPY web/template-route/src/ /usr/share/nginx/html/
