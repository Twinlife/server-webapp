
With our exclusive click-to-call feature, subscribers can generate unlimited personalized QR codes,
allowing anyone to contact them instantly—without the caller needing to install an app or create an account.
For a browser to access and use the click-to-call feature, there are two server components which are used:

- the [Click-to-call web application](https://github.com/Twinlife/server-webapp-ui) which represents the UI
  in HTML, CSS and typescript that runs within the browser,
- the [Client-to-call signaling proxy server](https://github.com/Twinlife/server-webapp) which handles
  the browser signaling and interaction with the signaling server used by mobile devices.


# Build and packaging

Maven is used to build the Web Application Proxy.

## Compilation

```
mvn compile
```

## Packaging

```
mvn clean package
```

This produces the `target/twinapp-<version>-package.tar.gz` package which can be
installed on a server.

Notes:

* The use of 'clean' target is strongly advised to ensure that compilation & packaging are done from an empty workspace.
Otherwise the package may still include some (obsolete) files of previous packaging.


# Installation

Copy the package file `target/twinapp-<version>-package.tar.gz` to the server.
On the server, extract the archive, stop the Web Application server, install and restart it:

```
tar xf twinapp-<version>-package.tgz
cd twinapp-<version>
service twinapp stop
bash ./install.sh
service twinapp start
```

# Apache configuration

The Apache configuration must have at least the following modules:

```
rewrite
proxy
proxy_http
proxy_wstunnel
```

The Apache configuration must contain at least two redirection rules:

* one for the REST API with `/rest/` entry point,
* one for the WebSocket API accessed through the `/p2p/` entry point.

```
        RewriteEngine on

        RewriteRule "^/rest/(.*)$" "http:/localhost:8081/rest/$1" [P]
        ProxyPassReverse "/rest/" "http:/localhost:8081/rest"

        RewriteCond %{HTTP:Upgrade} =websocket [NC]
        RewriteRule ^/p2p/(.*) ws://localhost:8081/p2p/$1 [P,L]
```

Apache configuration to serve static files:

```
        AddType text/javascript .js
        AddType text/css .css
        AddType image/jpg .jpg

        RewriteRule ^/call/.* /index.html [L]

        DocumentRoot /opt/twinapp-ui

        Alias / "/opt/twinapp-ui/"
        <Directory "/opt/twinapp-ui/">
            Options -Indexes +FollowSymLinks
            Options Multiviews
            Require all granted
        </Directory>
```

## Debugging

Run the JVM with the following options:

```
-DlogPath=<path>/logs
-Dlog4j.configurationFile=<path>/src/main/resources/log4j2/log4j2-debug.xml
```

