/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.security;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;

@Service
public class AuthorizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationService.class);

    @Autowired
    private ProviderRepository providerRepository;

    @Value("${authorization.enabled:true}")
    protected boolean authorizationEnabled;

    public void verifyAtLeastOne(AuthorizationClaim... claims) {
        if (!authorizationEnabled){
            LOGGER.debug("Authorization check disabled");
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Collection<? extends GrantedAuthority> authorities = new ArrayList<>(0);
        if (authentication != null) {
            LOGGER.debug("SecurityContextHolder : authentication found");
            authorities = authentication.getAuthorities();
            if (LOGGER.isTraceEnabled()) {
                authorities.stream().map(GrantedAuthority::getAuthority).forEach(LOGGER::trace);
            }
        }


        boolean authorized = false;
        for (AuthorizationClaim claim : claims) {
            LOGGER.trace("trying to match claim {}", claim.getRequiredRole());
            if (claim.getProviderId() == null) {
                authorized |= authorities.stream().anyMatch(ra -> claim.getRequiredRole().equals(ra.getAuthority()));
            } else {
                authorized |= hasRoleForProvider(authorities, claim);
            }
        }

        if (!authorized) {
            throw new AccessDeniedException("Insufficient privileges for operation");
        }


    }

    private boolean hasRoleForProvider(Collection<? extends GrantedAuthority> roleAssignments, AuthorizationClaim claim) {
        LOGGER.debug("hasRoleForProvider try to match claim with id {}", claim.getProviderId());
        Provider provider = providerRepository.getProvider(claim.getProviderId());
        if (provider == null) {
            LOGGER.debug("provider not found");
            return false;
        }

        return roleAssignments.stream()
                       .anyMatch(ra -> claim.getRequiredRole().equals(ra.getAuthority()));

    }
}
