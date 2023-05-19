package com.datastax.oss.venice;

import com.linkedin.venice.authorization.AceEntry;
import com.linkedin.venice.authorization.AclBinding;
import com.linkedin.venice.authorization.AuthorizerService;
import com.linkedin.venice.authorization.Method;
import com.linkedin.venice.authorization.Principal;
import com.linkedin.venice.authorization.Resource;
import com.linkedin.venice.utils.Utils;
import com.linkedin.venice.utils.VeniceProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.AuthenticationFactory;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PulsarAuthorizer implements AuthorizerService {

    private static final Logger LOGGER = LogManager.getLogger(PulsarAuthorizer.class);
    private PulsarAdmin pulsarAdmin;

    private ConcurrentHashMap<String, Set<String>> adminsForTenant = new ConcurrentHashMap<>();
    private List<String> adminRoles = new ArrayList<>();

    @Override
    public void initialise(VeniceProperties veniceProperties) throws Exception {
       String webServiceUrl = veniceProperties.getString("pulsar.webserviceUrl");
       String authenticationPlugin = veniceProperties.getString("pulsar.authenticationPlugin");
       String authenticationPluginParams =  veniceProperties.getString("pulsar.authenticationPluginParams");
       adminRoles = veniceProperties.getList("pulsar.adminRoles");
       pulsarAdmin = PulsarAdmin
               .builder()
               .serviceHttpUrl(webServiceUrl)
               .authentication(AuthenticationFactory.create(authenticationPlugin, authenticationPluginParams))
               .allowTlsInsecureConnection(true)
               .build();
    }

    private Set<String> getAdminsForTenant(String tenant) {
        return adminsForTenant.computeIfAbsent(tenant, t -> {
            Set<String> result = new HashSet<>();
            result.addAll(adminRoles);
            if (!tenant.isEmpty()) {
                try {
                    Set<String> adminRolesForTenant
                            = pulsarAdmin.tenants().getTenantInfo(tenant).getAdminRoles();
                    result.addAll(adminRolesForTenant);
                } catch (PulsarAdminException error) {
                    throw new RuntimeException("Cannot access Tenant permissions for " + tenant,
                            error);
                }
            }
            return result;
        });
    }

    private boolean isPrincipalAllowedOnResource(Principal principal, Resource resource) {
        String role = principal.getName();
        String tenant = extractTenant(resource.getName());
        Set<String> adminRoles = getAdminsForTenant(tenant);
        boolean granted = adminRoles.contains(role);
        LOGGER.info("isPrincipalAllowedOnResource {} {} {} -> {}",
                role, tenant, adminRoles, granted);
        return granted;
    }

    private static final String extractTenant(String resource) {
        if (resource == null || !resource.contains(".")) {
            // SYSTEM stuff
            return "";
        }
        int pos = resource.indexOf('.');
        return resource.substring(0, pos);
    }

    @Override
    public boolean canAccess(Method method, Resource resource, Principal principal) {
        return isPrincipalAllowedOnResource(principal, resource);
    }

    @Override
    public boolean canAccess(Method method, Resource resource, X509Certificate accessorCert) {
        return false;
    }

    @Override
    public AclBinding describeAcls(Resource resource) {
        return new AclBinding(resource);
    }

    @Override
    public void setAcls(AclBinding aclBinding) {

    }

    @Override
    public void clearAcls(Resource resource) {

    }

    @Override
    public void addAce(Resource resource, AceEntry aceEntry) {

    }

    @Override
    public void removeAce(Resource resource, AceEntry aceEntry) {

    }

    @Override
    public void setupResource(Resource resource) {
    }

    @Override
    public void clearResource(Resource resource) {
    }

    @Override
    public boolean isSuperUser(Principal principal, String storeName) {
        return isPrincipalAllowedOnResource(principal, new Resource(storeName));
    }

    @Override
    public void close() {
        if (pulsarAdmin != null) {
            Utils.closeQuietlyWithErrorLogged(pulsarAdmin);
        }
    }
}