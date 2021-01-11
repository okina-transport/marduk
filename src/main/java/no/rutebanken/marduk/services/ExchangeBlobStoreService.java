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

import com.amazonaws.services.s3.AmazonS3;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;


import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Arrays;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Service
public class ExchangeBlobStoreService {

    @Autowired
    private BlobStoreRepository repository;

    @Value("${blobstore.aws.exchange.container.name}")
    private String containerName;

    @Autowired
    Environment env;

    @Autowired
    private ApplicationContext context;


    @PostConstruct
    public void init(){
        if (hasS3Profile())            {
            repository.setAmazonS3Client( context.getBean(AmazonS3.class));
        }
        repository.setContainerName(containerName);
    }

    private boolean hasS3Profile(){
        return Arrays.stream(env.getActiveProfiles())
                     .anyMatch(profile-> "aws-blobstore".equals(profile));
    }

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
