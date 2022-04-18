Feature: Command-line interface
  Example: handling an invalid manifest
    Given manifest
      """edn
      {:foo :bar}
      """
    When I run the cli with this manifest
    Then I get an error message
