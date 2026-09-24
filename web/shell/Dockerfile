# syntax=docker/dockerfile:1.7
ARG STATIC_WEB_IMAGE
FROM ${STATIC_WEB_IMAGE}
COPY web/shell/nginx.conf /etc/nginx/conf.d/default.conf
COPY web/shell/src/ /usr/share/nginx/html/
