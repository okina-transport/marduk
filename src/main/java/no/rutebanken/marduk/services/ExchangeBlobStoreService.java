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

package no.rutebanken.marduk.services;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.InputStream;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Service
public class ExchangeBlobStoreService {

    @Autowired
    private BlobStoreRepository repository;

    @Autowired
    Environment env;

    @Autowired
    private ApplicationContext context;

    public void uploadBlob(@Header(value = Constants.FILE_HANDLE) String name, InputStream inputStream, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        repository.uploadBlob(name, inputStream, false);
    }

    public InputStream getBlob(@Header(value = FILE_HANDLE) String name, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.getBlob(name);
    }

    public boolean deleteBlob(@Header(value = FILE_HANDLE) String name, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.delete(name);
    }

}
