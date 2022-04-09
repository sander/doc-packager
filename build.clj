(ns build
  (:refer-clojure :exclude [compile test])
  (:require [clojure.tools.build.api :as b]))

(def basis (b/create-basis {:project "deps.edn"}))
(def class-dir "classes")

(defn clean [_]
  (b/delete {:path class-dir}))

(defn compile [_]
  (b/compile-clj {:basis basis :class-dir class-dir :src-dirs ["src"]}))

(defn test [s]
  (compile s)
  (b/process (b/java-command {:main "io.cucumber.core.cli.Main"
                              :java-opts ["-enableassertions"]
                              :main-args ["--plugin" "message:out/cucumber-output" "--plugin" "html:out/features.html"]
                              :basis basis})))
