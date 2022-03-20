(ns docpkg.main
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.java.shell :refer [sh]]))

(defn render-bpmn []
  (sh "npx" "bpmn-to-image" "--no-footer" "hello.bpmn:hello.pdf"))

(comment
  (render-bpmn))

(defmulti project-envelope
  "Updates the first argument with the third argument tagged by the second.
  See https://github.com/cucumber/common/tree/main/messages for a specification
  of the envelope."
  (fn [_ tag _] tag))

(defmethod project-envelope :default [result _ _]
  result)

(defmethod project-envelope :meta [result _ meta]
  (assoc result :meta meta))

(defmethod project-envelope :source [result _ {:keys [uri] :as source}]
  (-> result (assoc-in [:sources-by-uri uri] source)
             (update :sources conj uri)))

(defmethod project-envelope :gherkinDocument
  [result _ {uri :uri {:keys [children] :as feature} :feature :as doc}]
  (-> result (assoc-in [:gherkin-documents-by-uri uri]
               (assoc feature :children (mapv (comp :id :scenario) children)))
             (update :gherkin-documents conj uri)
             (update :scenarios-by-id merge
               (into {} (map (fn [{{:keys [id steps] :as scenario} :scenario}]
                               [id (assoc scenario :steps (mapv :id steps))])
                          children)))
             (update :gherkin-steps-by-id merge
               (into {} (mapcat (fn [{{steps :steps} :scenario}]
                                  (map (juxt :id identity) steps))
                          children)))))

(defmethod project-envelope :pickle
  [result _ {:keys [id uri steps astNodeIds :as pickle]}]
  (-> result (assoc-in [:pickles-by-id id] (assoc pickle :steps (mapv :id steps)))
             (update :pickles conj id)
             (update :pickle-steps-by-id merge
               (into {} (map (fn [{id :id :as step}] [id step]) steps)))))

(defmethod project-envelope :stepDefinition
  [result _ {id :id :as definition}]
  (assoc-in result [:step-definitions-by-id id] definition))

;; TODO: testRunStarted, testCaseStarted, testStepStarted, testStepFinished, testCaseFinished, testRunFinished

(defn project-message [result [[tag value] & _]]
  (project-envelope result tag value))

(comment
  (def documentation (-> (slurp "../package.json")
                         (json/read-str :key-fn keyword)
                         :documentation))
  (clojure.pprint/pprint
    (with-open [rdr (io/reader (str "../" (documentation :cucumberMessages)))]
      (reduce project-message {:sources []
                               :gherkin-documents []
                               :pickles []}
        (->> rdr line-seq (map #(json/read-str % :key-fn keyword))))))

  (with-open [rdr (io/reader (str "../" (documentation :cucumberMessages)))]
    (doseq [message (->> rdr line-seq (map #(json/read-str % :key-fn keyword)))]
      (println message))))

(defn escape [s]
  (reduce (fn [s [from to]]
            (str/replace s from (str/re-quote-replacement to)))
    s
    {#"\\" "\\\\"
     #"_" "\\_"
     #"\{" "\\{"
     #"\}" "\\}"}))

(def header-template (slurp (io/resource "header.tex")))
(def footer-template (slurp (io/resource "footer.tex")))

(defmacro print-page-with [& body]
  `(do
     (println "\\begin{preview}\n")
     ~@body
     (println "\\end{preview}\n")))

(defn print-package-code
  "Writes LaTeX code to *out*."
  [title readme-path messages-path]
  (println (str/replace header-template #"◊title" (escape title)))
  (let [{:keys [exit out error err]} (sh "pandoc" "-f" "markdown" "-t" "latex" readme-path)]
    (assert (= exit 0) (str "assertion failed:" out error err))
    (print-page-with
      (println out)))
  (let [result
        (with-open [rdr (io/reader (str messages-path))]
          (reduce project-message {:sources []
                                   :gherkin-documents []
                                   :pickles []}
                         (->> rdr line-seq (map #(json/read-str % :key-fn keyword)))))]
    (doseq [doc (map (:gherkin-documents-by-uri result) (:gherkin-documents result))]
      (print-page-with
        (println (str "\\section*{" (escape (:keyword doc)) ": " (escape (:name doc)) "}"))
        (doseq [{:keys [keyword name steps]} (map (:scenarios-by-id result) (:children doc))]
          (println (str "\\subsection*{" (escape keyword) ": " (escape name) "}"))
          (doall
            (->> (for [{:keys [keyword text]} (map (:gherkin-steps-by-id result) steps)]
                  (str "\\textit{" keyword "}" text))
                 (interpose "\\\\")
                 (map println)))))))
  (println footer-template))

(defn build-package
  "Uses LaTeX to create a PDF file."
  [documentation]
  (try
    (do
      (with-open [w (io/writer "out.tex")]
        (binding [*out* w]
          (print-package-code (documentation :title) (documentation :readme) (documentation :cucumber-messages))))
      (let [{:keys [exit out]} (sh "lualatex" "out.tex")]
        (assert (= exit 0))
        nil))
    (catch Exception e (println "Caught exception" (.getMessage e) e))))

(comment
  (build-package documentation))

;; TODO ensure it can be invoked with an input and an output path
