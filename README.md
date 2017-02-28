## cljsbin - HTTP Request & Response Service

cljsbin is a ClojureScript clone of [httpbin](https://httpbin.org/) that
runs on Node.js. It provides an API to test common HTTP features and operations
(request methods, headers, redirects, etc.).

cljsbin is implemented using the [Macchiato web framework](https://github.com/macchiato-framework/)
for ClojureScript.

### Prequisites

[Node.js](https://nodejs.org/) and [leiningen](http://leiningen.org/)
need to be installed to run the application.

### Running in development mode

run the following command in the terminal to install NPM modules and start Figwheel:

```
lein build
```

run `node` in another terminal:

```
npm start
```

### Building the release version

```
lein package
```

Run the release version:

```
npm start
```
