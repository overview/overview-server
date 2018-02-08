# alpine:3.6 doesn't have new-enough Chromium+Node.
# [adam, 2017-11-24] alpine:3.7 should be out any day now!
# [adam, later 2017-11-24] alpine:edge has Chromium 61. That's not enough:
# Chromium 61 headless doesn't let us test `window.alert()`.
#
# Switch to alpine when it has chromium 62. Debian makes the build
# super-slow.
FROM debian:stretch

# 1. Install everything we need to install nodejs
# 2. Run the nodejs pre-installer
# 3. Install nodejs and chromium
# 4. Install chromedriver (stretch is v2.32, we need 2.33)
RUN set -x \
      && echo "force-unsafe-io" > /etc/dpkg/dpkg.cfg.d/force-unsafe-io \
      && echo "Acquire::http {No-Cache=True;};" > /etc/apt/apt.conf.d/no-cache \
      && apt-get update \
      && apt-get install -y ca-certificates curl gnupg \
      && curl -sL https://deb.nodesource.com/setup_9.x | bash - \
      && apt-get install -y nodejs chromium libgconf2.0 unzip \
      && (cd /tmp && curl "https://chromedriver.storage.googleapis.com/2.35/chromedriver_linux64.zip" -o chromedriver_linux64.zip && unzip chromedriver_linux64.zip && rm -f chromedriver_linux64.zip && mv chromedriver /usr/bin/)

# Expose remote debugging port. Browse to http://docker-container-ip:9222
# to see what's going on inside Chromium.
EXPOSE 9222

COPY package.json package-lock.json /integration-test/
WORKDIR /integration-test

RUN npm install --production --quiet

COPY . /integration-test/

CMD [ "npm", "test" ]
