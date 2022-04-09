(ns build
  (:refer-clojure :exclude [compile test])
  (:require [clojure.tools.build.api :as b]))

(def basis (b/create-basis {:project "deps.edn"}))
(def class-dir "classes")

(defn clean [_]
  (b/delete {:path class-dir}))

(defn compile [_]
  ; (b/compile-clj {:basis basis :class-dir class-dir :src-dirs ["src"]})
  (b/javac {:src-dirs ["java"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["-source" "8" "-target" "8"]}))

(defn test [s]
  (compile s)
  (b/process (b/java-command {:main "io.cucumber.core.cli.Main"
                              :java-opts ["-enableassertions"]
                              :basis basis})))
