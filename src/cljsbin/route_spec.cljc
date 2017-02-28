(ns cljsbin.route-spec)

(defmacro route-spec
  "Build a route spec usable by bidy (map method to handler) and include
  metadata that can be used to generate a router index."
  [method sym & {:keys [bidi-tag]}]
  (if bidi-tag
    `{~method (bidi/tag ~sym ~bidi-tag)
      :doc (:doc (meta (var ~sym)))}
    `{~method ~sym
      :doc (:doc (meta (var ~sym)))}))
