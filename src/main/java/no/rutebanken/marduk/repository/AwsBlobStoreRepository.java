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

package no.rutebanken.marduk.repository;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.cloud.storage.Storage;
import com.okina.helper.aws.BlobStoreHelper;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Repository
@Profile("aws-blobstore")
@Scope("prototype")
public class AwsBlobStoreRepository implements BlobStoreRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private String containerName;

    @Autowired
    private ProviderRepository providerRepository;

    private AmazonS3 amazonS3Client;

    @Override
    public void setStorage(Storage storage) {
        // dummy
    }

    @Override
    public void setAmazonS3Client(AmazonS3 amazonS3Client) {
        this.amazonS3Client = amazonS3Client;
    }

    @Override
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {

        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();

        for (String prefix : prefixes) {
            List<S3ObjectSummary> s3ObjectSummaries = BlobStoreHelper.listAllBlobsRecursively(amazonS3Client, containerName, prefix);
            BlobStoreHelper.listAllBlobsRecursively(amazonS3Client, containerName, prefix).forEach(blob -> blobStoreFiles.add(toBlobStoreFile(amazonS3Client, containerName, blob)));
        }
        return blobStoreFiles;
    }

    @Override
    public BlobStoreFiles listBlobsForProvider(Collection<String> prefixes, Long providerId) {
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();

        for (String prefix : prefixes) {
            List<S3ObjectSummary> s3ObjectSummaries = BlobStoreHelper.listAllBlobsRecursively(amazonS3Client, containerName, prefix);
            s3ObjectSummaries.forEach(blob -> {
                BlobStoreFiles.File blobFile = toBlobStoreFile(amazonS3Client, containerName, blob);
                if (blobFile.getProviderId().equals(providerId)) {
                    blobStoreFiles.add(blobFile);
                }
            });
        }

        return blobStoreFiles;
    }

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        return listBlobs(Arrays.asList(prefix));
    }

    @Override
    public BlobStoreFiles listBlobsForProvider(String prefix, Long providerId) {
        return listBlobsForProvider(Arrays.asList(prefix), providerId);
    }

    @Override
    public BlobStoreFiles listBlobsFlat(String prefix) {
        List<S3ObjectSummary> blobs = BlobStoreHelper.listAllBlobsRecursively(amazonS3Client, containerName, prefix);
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        blobs.forEach(blob -> {
            String fileName = blob.getKey().replace(prefix, "");
            if (!StringUtils.isEmpty(fileName)) {
                blobStoreFiles.add(toBlobStoreFile(amazonS3Client, containerName, blob));
            }
        });

        return blobStoreFiles;
    }

    @Override
    public InputStream getBlob(String name) {
        return BlobStoreHelper.getBlob(amazonS3Client, containerName, name);
    }

    @Override
    public void uploadBlob(String name, InputStream inputStream, boolean makePublic) {
        try {
            BlobStoreHelper.uploadBlob(amazonS3Client, containerName, name, inputStream);
        } catch (InterruptedException | IOException e) {
            throw new MardukException("error while uploading blob", e);
        }
    }

    @Override
    public void uploadBlob(String name, InputStream inputStream, boolean makePublic, String contentType) {
        uploadBlob(name, inputStream, false);
    }


    public void copyBlob(String src, String dest) {
        CopyObjectRequest req = new CopyObjectRequest(this.containerName, src, this.containerName, dest);
        CopyObjectResult res = amazonS3Client.copyObject(req);
        logger.info("Aws => copied " + src + " to " + dest);
    }


    @Override
    public boolean delete(String objectName) {
        try {
            BlobStoreHelper.delete(amazonS3Client, containerName, objectName);
        } catch (AmazonClientException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean deleteAllFilesInFolder(String folder) {
        return BlobStoreHelper.deleteBlobsByPrefix(amazonS3Client, containerName, folder);
    }


    private BlobStoreFiles.File toBlobStoreFile(AmazonS3 client, String containerName, S3ObjectSummary blobSummary) {
        BlobStoreHelper.getBlob(client, containerName, blobSummary.getKey());
        BlobStoreFiles.File file = new BlobStoreFiles.File(blobSummary.getKey(), blobSummary.getLastModified(), blobSummary.getLastModified(), blobSummary.getSize());
        Provider provider = null;
        if (file.getName().contains("graphs/")) {
            file.setFormat(BlobStoreFiles.File.Format.GRAPH);
        } else if (file.getName().contains("/netex/") || file.getName().contains("tiamat/")) {
            file.setFormat(BlobStoreFiles.File.Format.NETEX);
            provider = parseProviderFromFileName(file.getName());
        } else if (file.getName().contains("/gtfs/")) {
            file.setFormat(BlobStoreFiles.File.Format.GTFS);
            provider = parseProviderFromFileName(file.getName());
        } else {
            file.setFormat(BlobStoreFiles.File.Format.UNKOWN);
        }

        if (provider != null) {
            file.setProviderId(provider.id);
            file.setReferential(provider.chouetteInfo.referential);
        }

        return file;
    }


    private Provider parseProviderFromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }

        String[] fileParts = fileName.split("/");
        String potentialRef = fileParts[fileParts.length - 1].split("-")[0];


        return providerRepository.getProviders().stream().filter(provider -> potentialRef.equalsIgnoreCase((provider.chouetteInfo.referential))).findFirst().orElse(null);
    }
}
