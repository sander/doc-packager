(ns docpkg.main
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.java.shell :refer [sh]]
    [clojure.test :refer [with-test deftest is run-tests run-test]])
  (:import
    [java.io File]
    [java.nio.file Files Paths FileSystems Path StandardOpenOption]
    [java.nio.file.attribute FileAttribute]
    [java.util Base64]))

(def glossary (atom {}))

(defn defconcept [id name description]
  (swap! glossary assoc id {::name name ::description description}))

(with-test
  (defn- expect-success [{:keys [exit out err]} error-description]
    (when (not= exit 0)
      (throw (ex-info error-description {:error err :output out}))))
  (is (nil? (expect-success {:exit 0} "Error")))
  (is (thrown? RuntimeException (expect-success {:exit 1} "Error"))))

(defn- path [s]
  (.getPath (FileSystems/getDefault) s (into-array String [])))

(defn zip-directory [f]
  (let [name (str (.getFileName (path f)))
        tmp (File/createTempFile (str name) ".zip")]
    (.delete tmp)
    (println ["zip" "-r" (str (.toPath tmp)) "." :dir f])
    (-> (sh "zip" "-r" (str (.toPath tmp)) "." :dir f)
      (expect-success "Could not zip"))
    (Files/newInputStream (.toPath tmp)
      (into-array [StandardOpenOption/DELETE_ON_CLOSE]))))

(comment
  (str (.getFileName (path "target/static")))
  (.toPath (.getFileName (path "target/static")))
  (str (.toPath (File/createTempFile (str name) ".zip")))
  (zip-directory "target/static")
  (.getFileName (.getPath (FileSystems/getDefault) "target/static" (into-array String []))))

(defn stream->bytes [in]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy in baos)
    (.toByteArray baos)))

(defn bytes->base64 [v]
  (.. (Base64/getUrlEncoder) (encodeToString v)))

(defn wrap [s n]
  (str/join "\n" (re-seq (re-pattern (str ".{1," n "}")) s)))

(defn wrapper-html
  [in file-name]
  (str "<!doctype html>\n<a href=\"data:application/octet-stream,\n"
    (-> in stream->bytes bytes->base64 (wrap 80))
    "\n\" download=\"" file-name "\">Download</a>"))

(comment
  (with-open [f])
  (io/copy (io/reader (zip-directory "target/static")) (io/writer "target/out.zip"))
  (spit "target/wrapped.html" (wrapper-html (zip-directory "target/static") "static.zip"))
  (-> (zip-directory "target/static") stream->bytes bytes->base64 (wrap 80) println)
  
  (str/join "\n" (re-seq (re-pattern ".{1,4}") "hello world"))
  re-seq)

(comment
  (remove-ns 'docpkg.main)
  (run-tests))

(defmulti convert (fn [_ from-type to-type] [from-type to-type]))

(with-test
  (defn- bpmn-to-svg-command [bpmn-file svg-file]
    ["npx" "bpmn-to-image" "--no-footer"
     (str (.getPath bpmn-file) \: (.getPath svg-file))])
  (is (= (bpmn-to-svg-command (File. "foo.bpmn") (File. "bar.svg"))
        ["npx" "bpmn-to-image" "--no-footer" "foo.bpmn:bar.svg"])))

(with-test
  (defn- svg-to-pdf-command [svg-file pdf-file]
    ["inkscape" (.getPath svg-file) (str "--export-pdf=" (.getPath pdf-file))])
  (is (= (svg-to-pdf-command (File. "foo.svg") (File. "bar.pdf"))
        ["inkscape" "foo.svg" "--export-pdf=bar.pdf"])))

(comment
  (run-test bpmn-to-svg-command)
  (run-test svg-to-pdf-command))

(defconcept ::business-process-model
  "Business process model"
  "A model for the end to end flow of a business process, enabling understanding and communication in a standard way (typically BPMN).")

(defconcept ::portable-document
  "Portable document"
  "A document that is presented independently of application software, hardware, and operating systems. Typically in PDF format.")

(defmethod convert [::business-process-model ::portable-document]
  [in _ _]
  (let [tmp-pdf (File/createTempFile "document" ".pdf")]
    (let [tmp-svg (File/createTempFile "process" ".svg")]
      (let [tmp-bpmn (File/createTempFile "model" ".bpmn")]
        (io/copy (io/reader in) tmp-bpmn)
        (try (expect-success (apply sh (bpmn-to-svg-command tmp-bpmn tmp-svg))
               "Error while converting to SVG")
             (finally (.delete tmp-bpmn))))
      (try (expect-success (apply sh (svg-to-pdf-command tmp-svg tmp-pdf))
             "Error while converting to PDF")
           (finally (.delete tmp-svg))))
    (Files/newInputStream (.toPath tmp-pdf)
      (into-array [StandardOpenOption/DELETE_ON_CLOSE]))))

(comment
  (with-open [r (convert "processes/hello.bpmn" ::business-process-model ::portable-document)]
    (io/copy r (io/file "out/test.pdf"))))

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
  (def documentation (-> (slurp "package.json")
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

(defconcept ::documentation-package
  "Documentation package"
  "A curated collection of rendered documents from one or more repositories.")

(defconcept ::documentation-package-manifest
  "Documentation package manifest"
  "A document listing the source documents to be processed into a documentation package.")

(defconcept ::executable-specification
  "Executable specification"
  "A document describing requirements in a way that can be processed into automated tests.")

(defn print-glossary
  [glossary]
  (print-page-with
    (println "\\section*{Glossary}")
    (doseq [[k {:keys [::name ::description]}] glossary]
      (println (str "\\textbf{" name ":} " description "\n")))))

(comment
  (print-glossary glossary))

(defn attachment
  [f title]
  (str "\\textattachfile{" (.getPath (io/file f)) "}{\\textcolor{blue}{"
    (escape title) "}}"))

(defn print-package-code
  "Writes LaTeX code to *out*."
  [title readme-path messages-path]
  (println (str/replace header-template #"◊title" (escape title)))
  (print-page-with
    (println "\\section*{Hyperlink test}")
    (println "\\href{out.tex}{Example}"))
  (print-page-with
    (println "\\section*{Attachments}")
    (println (attachment "../../test.html" "HTML file")))
  (print-page-with (println "\\section*{Context}"))
  (let [{:keys [exit out error err]} (sh "pandoc" "-f" "markdown" "-t" "latex" readme-path)]
    (assert (= exit 0) (str "assertion failed:" out error err))
    (print-page-with
      (println out)))
  (print-glossary @glossary)
  (print-page-with (println "\\section*{Requirements}"))
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
  (print-page-with (println "\\section*{Solution design}"))
  (print-page-with
    (println (str "\\section*{Business processes}"))
    (println (str "\\subsection*{Building a package}"))
    (println (str "\\includegraphics[width=\\columnwidth]{../hello.pdf}")))
  (print-page-with (println "\\section*{Validation}"))
  (print-page-with (println "\\section*{Implementation blueprint}"))
  (println footer-template))

(defn build-package
  "Uses LaTeX to create a PDF file."
  [documentation]
  (try
    (do
      (io/make-parents "target/tex/out.tex")
      (zip-directory "target/static")
      (with-open [r (convert "processes/hello.bpmn"
                      ::business-process-model ::portable-document)]
        (io/copy r (io/file "target/hello.pdf")))
      (with-open [w (io/writer "target/tex/out.tex")]
        (binding [*out* w]
          (print-package-code (documentation :title) (documentation :readme) (documentation :cucumber-messages))))
      (let [{:keys [exit out err]} (sh "lualatex" "out.tex" :dir "target/tex")]
        (assert (= exit 0) (str out \n err))
        nil))
    (catch Exception e (println "Caught exception" (.getMessage e) e))))

(comment
  into-array
  (build-package {:title "Testing" :readme "README.md" :cucumber-messages "out/cucumber-output"}))

;; TODO ensure it can be invoked with an input and an output path

(defn- step-description->symbol [s]
  (symbol (-> s
            (str/replace #" " "_")
            (str/replace #"[^a-zA-Z\d]" "_")
            (str/lower-case))))

(s/def ::step (s/cat
                :keyword #{'Given 'When 'Then}
                :description string?
                :body (s/* any?)))

(defmacro ^:private defsteps [& body]
  (let [prefix "step-"]
    `(~'do
       (~'gen-class
         :name ~(str (.getName *ns*) "/StepDefinitions")
         :prefix ~prefix
         :methods ~(->> body (map (partial s/conform ::step))
                     (mapv (fn [{:keys [keyword description]}]
                            `[~(with-meta
                                 (step-description->symbol description)
                                 `{~({'Given 'io.cucumber.java.en.Given
                                      'When 'io.cucumber.java.en.When
                                      'Then 'io.cucumber.java.en.Then}
                                     keyword)
                                   ~description})
                              []
                              ~'void]))))
       ~@(map (fn [x]
                (let [{:keys [description body]} (s/conform ::step x)]
                  `(~'defn
                     ~(symbol (str prefix
                                (step-description->symbol description)))
                     [~'_]
                     ~@body)))
           body))))

(defsteps
  (Given "a BPMN model"
    (assert (.exists (io/file "processes/hello.bpmn"))))
  (When "I render it to PDF"
    (io/make-parents "target/test/hello.pdf")
    (with-open [r (convert "processes/hello.bpmn" ::business-process-model
                    ::portable-document)]
      (io/copy r (io/file "target/test/hello.pdf"))))
  (Then "I have a PDF file"
    (assert (.exists (io/file "target/test/hello.pdf")))))
