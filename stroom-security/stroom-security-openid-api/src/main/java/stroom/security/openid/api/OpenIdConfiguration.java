package stroom.security.openid.api;

/**
 * Defines the configuration required to interact with an Open ID Connect IDP
 */
public interface OpenIdConfiguration {

    IdpType getIdentityProviderType();

    String getOpenIdConfigurationEndpoint();

    String getIssuer();

    String getAuthEndpoint();

    String getTokenEndpoint();

    String getJwksUri();

    String getLogoutEndpoint();

    String getClientId();

    String getClientSecret();

    boolean isFormTokenRequest();

    String getRequestScope();

    boolean isValidateAudience();

    String getLogoutRedirectParamName();

}