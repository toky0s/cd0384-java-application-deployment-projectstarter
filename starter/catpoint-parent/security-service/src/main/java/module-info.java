module com.udacity.catpoint.security {
    requires com.udacity.catpoint.image;
    requires java.desktop;
    requires com.google.gson;
    requires java.prefs;
    requires com.google.common;
    requires org.mockito;
    exports com.udacity.catpoint.security.application to app;
    exports com.udacity.catpoint.security.data to app;
    exports com.udacity.catpoint.security.service to app;

    opens com.udacity.catpoint.security.data to com.google.gson, org.mockito, org.junit.jupiter.api, app;
    opens com.udacity.catpoint.security.service to org.junit.jupiter.api, org.mockito;
    opens com.udacity.catpoint.security.application to org.junit.jupiter.api, org.mockito;
}