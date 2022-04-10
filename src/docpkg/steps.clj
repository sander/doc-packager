(ns docpkg.steps
  (:require
    [clojure.java.io :as io]
    [docpkg.main :as d])
  (:gen-class
    :methods [[^{io.cucumber.java.en.Given "a BPMN model"}
               a_bpmn_model [] void]
              [^{io.cucumber.java.en.When "I render it to PDF"}
               i_render_it_to_pdf [] void]
              [^{io.cucumber.java.en.Then "I have a PDF file"}
               i_have_a_pdf_file [] void]]))

(defn -a_bpmn_model [_]
  (assert (.exists (io/file "processes/hello.bpmn"))))

(defn -i_render_it_to_pdf [_]
  (d/render! ::d/business-process-model "processes/hello.bpmn"
             ::d/portable-document "out/hello.pdf"))

(defn -i_have_a_pdf_file [_]
  (assert (.exists (io/file "out/hello.pdf"))))
