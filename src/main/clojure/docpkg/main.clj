(ns docpkg.main
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.java.shell :refer [sh]]
    [clojure.test :refer [with-test deftest is run-tests run-test]]
    [docpkg.internal :refer [defsteps defconcept *glossary*]])
  (:import
    [java.io File ByteArrayOutputStream ByteArrayInputStream BufferedInputStream]
    [java.nio.file Files Paths FileSystems Path StandardOpenOption]
    [java.nio.file.attribute FileAttribute]
    [java.util Base64]))

(defn testje
  [args]
  (println :args args))

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
    (-> (sh "zip" "-r" (str (.toPath tmp)) "." :dir f)
      (expect-success "Could not zip"))
    (BufferedInputStream.
      (Files/newInputStream (.toPath tmp)
        (into-array [StandardOpenOption/DELETE_ON_CLOSE])))))

(defn stream->bytes [in]
  (let [baos (ByteArrayOutputStream.)]
    (.transferTo in baos)
    (.toByteArray baos)))

(defn bytes->base64 [v]
  (.. (Base64/getEncoder) (encodeToString v)))

(defn wrap [s n]
  (str/join "\n" (re-seq (re-pattern (str ".{1," n "}")) s)))

(defn wrapper-html
  "Wraps a binary file an HTML downloader, since Adobe Acrobat by default does
  not allow attaching most files such as zip archives, but allows attaching
  HTML files."
  [in file-name]
  (str "<!doctype html>\n"
    "<title>" file-name "</title>\n"
    "<style>body { font-family: sans-serif; margin: 1em; }</style>"
    "<p><a href=\"data:application/octet-stream;base64,\n"
    (-> in stream->bytes bytes->base64 (wrap 80))
    "\n\" download=\"" file-name "\">Download</a>\n"
    "<p><small>This will download a zip archive. Extract it and open the "
    "contained <strong>index.html</strong> file start browsing.</small>"))

(comment
  (do
    (.delete (io/file "target/out.zip"))
    (with-open [in (zip-directory "target/static")
                out (io/output-stream (io/file "target/out.zip"))]
      (.transferTo in out))
    (sh "unzip" "-l" "target/out.zip"))
  (spit "target/wrapped.html" (wrapper-html (zip-directory "target/static") "static.zip")))

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
    (doseq [[k {:keys [:docpkg.internal/name :docpkg.internal/description]}] glossary]
      (println (str "\\textbf{" name ":} " description "\n")))))

(comment
  (print-glossary glossary))

(defn attachment
  [f title]
  (str "\\textattachfile{" (.getPath (io/file f)) "}{\\textcolor{blue}{"
    (escape title) "}}"))

(defn section-header
  [title]
  (str "\\begin{center}\n"
    "\\section*{" title "}\n"
    "\\end{center}\n"))

(defn print-package-code
  "Writes LaTeX code to *out*."
  [title readme-path messages-path static-web-site-path]
  (println (str/replace header-template #"◊title" (escape title)))
  (print-page-with (println (section-header "Context")))
  (let [{:keys [exit out error err]} (sh "pandoc" "-f" "markdown" "-t" "latex" readme-path)]
    (assert (= exit 0) (str "assertion failed:" out error err))
    (print-page-with
      (println out)))
  (print-glossary @*glossary*)
  (print-page-with (println (section-header "Requirements")))
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
  (print-page-with (println (section-header "Solution design")))
  (print-page-with
    (println (str "\\section*{Business processes}"))
    (println (str "\\subsection*{Building a package}"))
    (println (str "\\includegraphics[width=\\columnwidth]{../hello.pdf}")))
  (print-page-with (println (section-header "Validation")))
  (print-page-with (println (section-header "Implementation blueprint")))
  (print-page-with (println (section-header "Attachments")))
  (print-page-with
    (println "\\section*{Static website}")
    (println "\\begin{tcolorbox}")
    (let [p "static-website.zip"
          f "static-website.html"]
      (spit (str "target/" f)
        (wrapper-html (zip-directory static-web-site-path) p))
      (println (attachment (str "../" f) (str "Download " f)))
      (println)
      (println "{\\small Only works in some PDF viewers, such as "
        "\\href{https://get.adobe.com/reader/}{Adobe Acrobat Reader} and "
        "\\href{https://www.mozilla.org/firefox/new/}{Firefox} on desktop "
        "by double-clicking the link, or "
        "\\href{https://pdfviewer.io/pro/}{PDF Viewer Pro} on mobile."
        "On the command line, you can use "
        "\\href{https://www.xpdfreader.com}{Xpdf} with "
        "\\texttt{pdfdetach -saveall <file.pdf>}.}"))
    (println "\\end{tcolorbox}"))
  (println footer-template))

(defn build-package
  "Uses LaTeX to create a PDF file."
  [documentation]
  (try
    (do
      (io/make-parents "target/tex/out.tex")
      (with-open [r (convert "processes/hello.bpmn"
                      ::business-process-model ::portable-document)]
        (io/copy r (io/file "target/hello.pdf")))
      (with-open [w (io/writer "target/tex/out.tex")]
        (binding [*out* w]
          (print-package-code
            (documentation :title)
            (documentation :readme)
            (documentation :cucumber-messages)
            (documentation :static-website))))
      (let [{:keys [exit out err]} (sh "lualatex" "out.tex" :dir "target/tex")]
        (assert (= exit 0) (str out \n err))
        nil))
    (catch Exception e (println "Caught exception" (.getMessage e) e))))

(comment
  into-array
  (build-package {:title "Testing" :readme "README.md" :cucumber-messages "out/cucumber-output"}))

;; TODO ensure it can be invoked with an input and an output path

(defsteps
  (Given "a BPMN model" []
    (assert (.exists (io/file "processes/hello.bpmn"))))
  (When "I render it to PDF" []
    (io/make-parents "target/test/hello.pdf")
    (with-open [r (convert "processes/hello.bpmn" ::business-process-model
                    ::portable-document)]
      (io/copy r (io/file "target/test/hello.pdf"))))
  (Then "I have a PDF file" []
    (assert (.exists (io/file "target/test/hello.pdf")))))
