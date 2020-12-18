package org.modelingvalue.gradle.corrector;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public interface Info {
    String  NAME         = "mvgCorrector";
    Logger  LOGGER       = Logging.getLogger(NAME);
    boolean CI           = Boolean.parseBoolean(Util.envOrProp("CI"));
    String  TOKEN = Util.envOrProp("TOKEN");
}
