package stroom.security.impl;

import stroom.security.api.UserIdentity;

import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface UserIdentityFactory {

    Optional<UserIdentity> getApiUserIdentity(HttpServletRequest request);

    boolean hasAuthenticationToken(final HttpServletRequest request);

    void removeAuthorisationEntries(final Map<String, String> headers);

    Optional<UserIdentity> getAuthFlowUserIdentity(HttpServletRequest request,
                                                   String code,
                                                   AuthenticationState state);

    void refresh(UserIdentity userIdentity);
}
