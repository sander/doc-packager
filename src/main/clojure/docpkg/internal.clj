(ns docpkg.internal
  "Internal functionality for docpkg."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))

(defn- example-step-description->symbol
  "Returns a symbol aiming for a 1-1 representation of the example step
  description string."
  [s]
  (symbol (-> s
            (str/replace #" " "_")
            (str/replace #"[^a-zA-Z\d]" "_")
            (str/lower-case))))

(s/def ::example-step (s/cat
                        :keyword #{'Given 'When 'Then}
                        :description string?
                        :args vector?
                        :body (s/* any?)))

(defmacro defsteps
  "Creates a step definition class for Cucumber execution."
  [& body]
  (let [prefix "step-"]
    `(~'do
       (~'gen-class
         :name ~(str (.getName *ns*) "/StepDefinitions")
         :prefix ~prefix
         :methods ~(->> body (map (partial s/conform ::example-step))
                     (mapv (fn [{:keys [keyword description args]}]
                            `[~(with-meta
                                 (example-step-description->symbol description)
                                 `{~({'Given 'io.cucumber.java.en.Given
                                      'When 'io.cucumber.java.en.When
                                      'Then 'io.cucumber.java.en.Then}
                                     keyword)
                                   ~description})
                              ~(mapv (fn [_] String) args)
                              ~'void]))))
       ~@(map (fn [x]
                (let [{:keys [description body args]}
                      (s/conform ::example-step x)]
                  `(~'defn
                     ~(symbol (str prefix
                                (example-step-description->symbol description)))
                     [~'_ ~@args]
                     ~@body)))
           body))))

(def ^:dynamic *glossary*
  "A global glossary."
  (atom {}))

(defn defconcept
  "Adds a concept to *glossary*. Use a qualified keyword as id."
  [id name description]
  (swap! *glossary* assoc id {::name name ::description description}))

(defsteps
  (Given "an empty glossary" []
    (def test-glossary (atom {})))
  (When "I define a concept" []
    (binding [*glossary* test-glossary]
      (defconcept ::example "foo" "bar")))
  (Then "the glossary is not empty" []
    (assert (not (empty? @test-glossary)))))
