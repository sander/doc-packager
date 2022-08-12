#!/usr/bin/env -S scala-cli shebang

import sys.process.*

"scala-cli test --test-framework munit.Framework .".!
