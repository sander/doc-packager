Feature: PDF
  Example: Rendering BPMN
    Given a BPMN model
    When I render it to PDF
    Then I have an PDF file

  Example: Writing PDF
    Given a project
    When I execute the program
    Then I get a PDF
