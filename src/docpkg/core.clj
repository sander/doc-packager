(ns docpkg.core
  "Core functionality to package documentation into human-readable documents."
  (:require
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [docpkg.internal :refer [defconcept defsteps]]))

(s/def :local/path string?)

(s/def ::document (s/keys :req [:local/path]))

(s/def :package/title string?)

(s/def :package/introduction ::document)

(s/def :package/specification ::document)

(s/def :package/test-report ::document)

(s/def :package/manifest
  (s/keys
    :req-un [::title]
    :opt-un [::title ::introduction ::specification ::test-report]))

(defconcept :package/manifest
  "Package manifest"
  "A complete definition of data and references to documents that should be packaged.")

(s/fdef build-package
  :args (s/cat :manifest :package/manifest)
  :ret (s/or :success nil? :anomaly ::anom/anomaly))

(defn- build-package
  "Generates a documentation package from a manifest.

  Default target: target/tex/out.pdf"
  [manifest]
  {::anom/category ::anom/incorrect ::anom/message "Invalid manifest"})

(defn- anomaly?
  [x]
  (s/valid? ::anom/anomaly x))

(defn cli
  "Command-line interface to build-package."
  [manifest]
  (when-not (s/valid? :package/manifest manifest)
    (throw (IllegalArgumentException.
             "Manifest does not conform to :package/manifest spec.")))
  (let [result (build-package manifest)]
    (when (anomaly? result)
      (throw (ex-info "Error while building package" result)))))

(comment
  (require '[clojure.edn :as edn])
  (let [m (-> "deps.edn"
            slurp
            edn/read-string
            (get-in [:aliases :docpkg :exec-args]))]
    (s/explain :package/manifest m)
    (cli m))

  (require '[clojure.spec.test.alpha :as stest])
  (stest/instrument `build-package)

  (binding [*out* *err*] (println "foo"))
  (cli {:foo :bar}))

;; TODO ensure the first Given takes a String arg

(defsteps
  (Given "a project with feature file"
    (println "a project"))
  (When "I run the cli with this manifest"
    (println "run"))
  (Then "I get an error message"
    (println "error")))
