package org.weasis.core.ui.model.imp;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.weasis.core.ui.model.imp.suite.ConstructorImageElementSuite;
import org.weasis.core.ui.model.imp.suite.ContructorNoArgumentsSuite;
import org.weasis.core.ui.model.imp.suite.DeserializationSuite;
import org.weasis.core.ui.model.imp.suite.SerializationSuite;

@RunWith(Suite.class)
@SuiteClasses({
    ContructorNoArgumentsSuite.class,
    ConstructorImageElementSuite.class,
    SerializationSuite.class,
    DeserializationSuite.class
})
public class XmlGraphicModelTest {
    // Do nothing in this class
}
