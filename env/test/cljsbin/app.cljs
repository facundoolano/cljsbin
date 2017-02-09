(ns cljsbin.app
  (:require
    [doo.runner :refer-macros [doo-tests]]
    [cljsbin.core-test]))

(doo-tests 'cljsbin.core-test)


