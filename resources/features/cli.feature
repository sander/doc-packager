Feature: Command-line interface
  Example: requiring a title
    Given manifest
      """edn
      {:introduction {:local/path "README.md"}}
      """
    When I run the cli with this manifest
    Then I get an error message
