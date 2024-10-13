module com.udacity.catpoint.image {
    exports com.udacity.catpoint.image.service to app, com.udacity.catpoint.security;
    requires software.amazon.awssdk.auth;
    requires software.amazon.awssdk.core;
    requires software.amazon.awssdk.regions;
    requires software.amazon.awssdk.services.rekognition;
    requires java.desktop;
    requires org.slf4j;

    opens com.udacity.catpoint.image.service to org.mockito;
}