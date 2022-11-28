package stroom.security.impl;

import stroom.security.api.RequestAuthenticator;
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

public class RequestAuthenticatorImpl implements RequestAuthenticator {

    private final OpenIdManager openIdManager;

    @Inject
    public RequestAuthenticatorImpl(final OpenIdManager openIdManager) {
        this.openIdManager = openIdManager;
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request) {
        return openIdManager.loginWithRequestToken(request);
    }

    @Override
    public boolean hasAuthenticationToken(final HttpServletRequest request) {
        return openIdManager.hasAuthenticationToken(request);
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {
        NullSafe.consume(headers, openIdManager::removeAuthorisationEntries);
    }
}
