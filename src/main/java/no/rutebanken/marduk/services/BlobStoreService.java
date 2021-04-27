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
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Service
public class BlobStoreService {

    @Autowired
    BlobStoreRepository repository;

    @Autowired
    ProviderRepository providerRepository;

    @Autowired
    Environment env;

    @Autowired
    private ApplicationContext context;

    public BlobStoreFiles listBlobsInFolder(@Header(value = Exchange.FILE_PARENT) String folder, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.listBlobs(folder + "/");
    }

    public BlobStoreFiles listBlobsInFolders(@Header(value = Constants.FILE_PARENT_COLLECTION) Collection<String> folders, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.listBlobs(folders);
    }

    public BlobStoreFiles listBlobsInFoldersByProvider(@Header(value = Constants.FILE_PARENT_COLLECTION) Collection<String> folders, @Header(value = Constants.PROVIDER_ID) String providerId, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.listBlobs(folders);
    }


    public BlobStoreFiles listBlobs(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.listBlobs(getMobiitiIdFromReferential(referential) + "/imports/");
    }

    public BlobStoreFiles listBlobsFlat(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.listBlobsFlat(getMobiitiIdFromReferential(referential) + "/imports/");
    }

    public InputStream getBlob(@Header(value = Constants.FILE_HANDLE) String name, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.getBlob(name);
    }

    public void uploadBlob(@Header(value = Constants.FILE_HANDLE) String name,
                           @Header(value = Constants.BLOBSTORE_MAKE_BLOB_PUBLIC) boolean makePublic, InputStream inputStream, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        repository.uploadBlob(name, inputStream, makePublic);
    }


    public void uploadBlobExport(@Header(value = Constants.FILE_HANDLE) String name,
                                 @Header(value = Constants.ARCHIVE_FILE_HANDLE) String archiveName,
                                 @Header(value = Constants.BLOBSTORE_MAKE_BLOB_PUBLIC) boolean makePublic,
                                 InputStream inputStream,
                                 Exchange exchange) throws UnsupportedEncodingException {
        name = URLDecoder.decode(name, StandardCharsets.UTF_8.toString());
        uploadBlob(name, makePublic, inputStream, exchange);
        if (StringUtils.isNotBlank(archiveName)) {
            copyBlob(name, archiveName);
        }
    }


    public void setPublicAccess(String filepath, boolean makePublic) {
        repository.setPublicAccess(makePublic, filepath);
    }


    public void copyBlob(String src, String dest) {
        repository.copyBlob(src, dest);
    }


    public boolean deleteBlob(@Header(value = FILE_HANDLE) String name, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.delete(name);
    }

    public boolean deleteAllBlobsInFolder(String folder, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.deleteAllFilesInFolder(folder);
    }

    public void uploadBlob(String name, boolean makePublic, InputStream inputStream) {
        repository.uploadBlob(name, inputStream, makePublic);
    }

    public BlobStoreFiles listBlobsInFolders(String path) {
        return repository.listBlobs(path);
    }

    public InputStream getBlob(String name) {
        return repository.getBlob(name);
    }

    private Long getMobiitiIdFromReferential(final String referential) {
        if (referential == null) {
            return null;
        }

        return providerRepository.getProviders().stream().filter(provider ->
                referential.equalsIgnoreCase(provider.name))
                .findFirst()
                .orElse(null)
                .mobiitiId;
    }

}
