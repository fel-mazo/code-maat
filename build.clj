(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]))

(def lib 'codescene-lite/codescene-lite)
(def version "0.1.0")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber
  "Build a standalone uberjar.
   Usage: clj -T:build uber"
  [_]
  (clean nil)
  (println "Copying resources...")
  (b/copy-dir {:src-dirs   ["resources"]
               :target-dir class-dir})
  (println "Compiling Clojure sources...")
  (b/compile-clj {:basis      @basis
                  :src-dirs   ["src"]
                  :class-dir  class-dir})
  (println "Packaging uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file (format "target/%s-%s-standalone.jar"
                               (name lib) version)
           :basis     @basis
           :main      'codescene-lite.main})
  (println (str "Built target/" (name lib) "-" version "-standalone.jar")))
