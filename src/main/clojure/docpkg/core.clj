(ns docpkg.core
  "Core functionality to package documentation into human-readable documents."
  (:require
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.edn :as edn]
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

(defsteps
  (Given "manifest" [manifest]
    (def test-manifest (edn/read-string manifest)))
  (When "I run the cli with this manifest" []
    (def test-result
      (try {:ok (cli test-manifest)}
        (catch Exception e {:exception e}))))
  (Then "I get an error message" []
    (assert (:exception test-result))))
