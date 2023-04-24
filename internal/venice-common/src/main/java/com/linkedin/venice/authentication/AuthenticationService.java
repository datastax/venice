package com.linkedin.venice.authentication;


import com.linkedin.venice.authorization.Principal;

import java.security.cert.X509Certificate;
import java.util.function.Function;

/**
 * Performs authentication.
 */
public interface AuthenticationService {
        default Principal getPrincipalFromHttpRequest(X509Certificate certificate,
                                              Function<String, String> headers) {
            return null;
        }

}
