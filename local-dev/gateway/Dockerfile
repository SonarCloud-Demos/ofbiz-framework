FROM nginx:1.27-alpine
RUN apk add --no-cache openssl \
    && mkdir -p /etc/nginx/tls \
    && openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
       -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
       -keyout /etc/nginx/tls/local.key -out /etc/nginx/tls/local.crt
COPY nginx.conf /etc/nginx/conf.d/default.conf
