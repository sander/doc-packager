package docpkg;

import java.lang.annotation.Repeatable;

class Design {

  @interface BoundedContext {}

  @Repeatable(Risks.class)
  @interface Risk {

    String scenario();
  }

  @interface Risks {

    Risk[] value();
  }
}
