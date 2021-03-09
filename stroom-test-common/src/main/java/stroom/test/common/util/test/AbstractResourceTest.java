package stroom.test.common.util.test;

import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.RestResource;

import io.dropwizard.jersey.validation.ValidationErrorMessage;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.assertj.core.api.Assertions;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.Rule;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.function.Function;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
@ExtendWith(DropwizardExtensionsSupport.class)
public abstract class AbstractResourceTest<R extends RestResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractResourceTest.class);

    // Need to add resources as suppliers so they can be fully mocked by mocktio before being used
    @Rule
    private final ResourceExtension resources = ResourceExtension.builder()
            .addResource(() -> {
                LOGGER.info("Calling getRestResource()");
                return getRestResource();
            })
            .setClientConfigurator(clientConfig -> clientConfig.register(LoggingFeature.class))
            .build();

    private final WebTargetFactory webTargetFactory = url -> {
        // Convert url to sub path http://localhost:9998/base/path/sub/path => /sub/path
        String fullPath = url.replace("http://", "");
        fullPath = fullPath.replace("//", "/");
        fullPath = fullPath.substring(fullPath.indexOf("/"));
        final String subPath = fullPath.replace(getResourceBasePath(), "");
        System.out.println("subPath: " + subPath);
        return resources.target(subPath);
    };

    /**
     * @return A mocked implementation of the resource
     */
    public abstract R getRestResource();

    public abstract String getResourceBasePath();

    public ResourceExtension getResources() {
        return resources;
    }

    public WebTargetFactory webTargetFactory() {
        return webTargetFactory;
    }

    public URI getServerBaseUri() {
        return resources.getJerseyTest().target().getUri();
    }

//    public  <T_RESP> T_RESP doGetTest(final String subPath,
//                            final Class<T_RESP> responseType,
//                            final T_RESP expectedResponse) {
//
//        LOGGER.info("Calling GET on {}{}, expecting {}", getResourceBasePath(), subPath, expectedResponse);
//        T_RESP response =  resources
//            .target(getResourceBasePath())
//            .path(subPath)
//            .request()
//            .get(responseType);
//
//        Assertions.assertThat(response).isEqualTo(expectedResponse);
//
//        return response;
//    }

    public <T_RESP> T_RESP doGetTest(final String subPath,
                                     final Class<T_RESP> responseType,
                                     final T_RESP expectedResponse,
                                     final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling GET on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        return doTest(
                Invocation.Builder::get,
                subPath,
                responseType,
                expectedResponse,
                builderMethods);
    }

    public <T_REQ, T_RESP> T_RESP doPostTest(final String subPath,
                                             final T_REQ request,
                                             final Class<T_RESP> responseType,
                                             final T_RESP expectedResponse,
                                             final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling GET on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        WebTarget webTarget = resources
                .target(getResourceBasePath())
                .path(subPath);

        for (Function<WebTarget, WebTarget> method : builderMethods) {
            webTarget = method.apply(webTarget);
        }

        Invocation.Builder builder = webTarget
                .request();

        final Entity<T_REQ> entity = Entity.json(request);

        final Response response = builder.post(entity);

        if (!isSuccessful(response.getStatus())) {
//            String json = response.readEntity(String.class);
//            LOGGER.info("json:\n{}", json);
//            System.out.println(json);

            final ValidationErrorMessage validationErrorMessage = response.readEntity(ValidationErrorMessage.class);

//            ErrorMessage errorMessage = response.readEntity(ErrorMessage.class);

            throw new RuntimeException(LogUtil.message("Error: {} {}",
                    response.getStatus(), validationErrorMessage.getErrors()));
        }

        T_RESP responseEntity = response.readEntity(responseType);

        if (expectedResponse != null) {
            Assertions.assertThat(responseEntity)
                    .isEqualTo(expectedResponse);
        }

        return responseEntity;
    }

    public <T_REQ, T_RESP> T_RESP doPutTest(final String subPath,
                                            final T_REQ requestEntity,
                                            final Class<T_RESP> responseType,
                                            final T_RESP expectedResponse,
                                            final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling PUT on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        return doTest(builder -> builder.put(Entity.json(requestEntity)),
                subPath,
                responseType,
                expectedResponse,
                builderMethods);
    }

    public <T_REQ> void doPutTest(final String subPath,
                                  final T_REQ requestEntity,
                                  final Function<WebTarget, WebTarget>... builderMethods) {

        LOGGER.info("Calling PUT on {}{}, passing {}",
                getResourceBasePath(), subPath, requestEntity);

        WebTarget webTarget = resources
                .target(getResourceBasePath())
                .path(subPath);

        for (Function<WebTarget, WebTarget> method : builderMethods) {
            webTarget = method.apply(webTarget);
        }

        Invocation.Builder builder = webTarget
                .request();

        Response response = builder.put(Entity.json(requestEntity));

        if (!isSuccessful(response.getStatus())) {
            throw new RuntimeException(LogUtil.message("Error: {} {}",
                    response.getStatus(), response));
        }
    }

    public <T_RESP> T_RESP doDeleteTest(final String subPath,
                                        final Class<T_RESP> responseType,
                                        final T_RESP expectedResponse,
                                        final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling DELETE on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        return doTest(Invocation.Builder::delete,
                subPath,
                responseType,
                expectedResponse,
                builderMethods);
    }

    private <T_RESP> T_RESP doTest(final Function<Invocation.Builder, Response> operation,
                                   final String subPath,
                                   final Class<T_RESP> responseType,
                                   final T_RESP expectedResponse,
                                   final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling GET on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        WebTarget webTarget = resources
                .target(getResourceBasePath())
                .path(subPath);

        for (Function<WebTarget, WebTarget> method : builderMethods) {
            webTarget = method.apply(webTarget);
        }

        Invocation.Builder builder = webTarget
                .request();

        final Response response = operation.apply(builder);

        if (!isSuccessful(response.getStatus())) {
            throw new RuntimeException(LogUtil.message("Error: {} {}",
                    response.getStatus(), response));
        }

//        String json = response.readEntity(String.class);
//
//        LOGGER.info("json:\n{}", json);

        T_RESP entity = response.readEntity(responseType);

        if (expectedResponse != null) {
            Assertions.assertThat(entity)
                    .isEqualTo(expectedResponse);
        }

        return entity;
    }

    public WebTarget getWebTarget(final String subPath) {

        return resources
                .target(getResourceBasePath())
                .path(subPath);
    }

    private boolean isSuccessful(final int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}
