package org.weasis.core.api.explorer;

import java.util.Hashtable;

public interface DataExplorerViewFactory {

    DataExplorerView createDataExplorerView(Hashtable<String, Object> properties);

}
