package no.rutebanken.marduk.repository;

import com.amazonaws.services.s3.AmazonS3;
import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.services.FileSystemService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;


@Repository
@Profile("localFile-blobstore")
public class FileStoreRepository implements BlobStoreRepository{

    @Autowired
    FileSystemService fileSystemService;

    @Autowired
    private ProviderRepository providerRepository;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {
        return  listBlobsForProvider(prefixes,null);
    }

    private Optional<BlobStoreFiles.File> toBlobStoreFile(Path pathToFile){
        try {
            BasicFileAttributes attr = Files.readAttributes(pathToFile, BasicFileAttributes.class);
            BlobStoreFiles.File file = new BlobStoreFiles.File(pathToFile.getFileName().toString(), new Date(attr.creationTime().toMillis()), new Date(attr.lastModifiedTime().toMillis()), attr.size());
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
            return Optional.of(file);
        } catch (IOException e) {
            logger.error("Erreur lecture du fichier: "+pathToFile.getFileName());
            logger.error(e.toString());
            return Optional.empty();
        }
    }

    private Provider parseProviderFromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String[] fileParts = fileName.split("/");
        String potentialRef = fileParts[fileParts.length - 1].split("-")[0];
        return providerRepository.getProviders().stream().filter(provider -> potentialRef.equalsIgnoreCase((provider.chouetteInfo.referential))).findFirst().orElse(null);
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
    public BlobStoreFiles listBlobsForProvider(Collection<String> prefixes, Long providerId) {

        List<Path> pathList = new ArrayList();
        prefixes.stream()
                .forEach(prefix -> pathList.addAll(fileSystemService.getAllFilesFromLocalStorage(prefix, ".zip")));

        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        pathList.stream()
                .map(this::toBlobStoreFile)
                .filter(opt->opt.isPresent() && (providerId == null  || opt.get().getProviderId().equals(providerId)))
                .map(Optional::get)
                .forEach(blobStoreFiles::add);

        return blobStoreFiles;
    }

    @Override
    public BlobStoreFiles listBlobsFlat(String prefix) {
        List<BlobStoreFiles.File> files = listBlobs(prefix).getFiles();
        List<BlobStoreFiles.File> result = files.stream().map(k -> new BlobStoreFiles.File(k.getName().replaceFirst(prefix + "/", ""), new Date(), new Date(), 1234L)).collect(Collectors.toList());
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        blobStoreFiles.add(result);
        return blobStoreFiles;
    }

    @Override
    public InputStream getBlob(String objectName) {
        logger.debug("get blob from file store:"+objectName);
        return fileSystemService.getFile(objectName);
    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic) {
        try {
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);

            Path newPath = fileSystemService.getOrCreateFilePath(objectName);
            File targetFile = new File(newPath.toUri());
            OutputStream outStream = new FileOutputStream(targetFile);
            outStream.write(buffer);
        } catch (IOException e) {
            logger.error("Erreur upload blob fichier:"+objectName);
            logger.error(e.toString());
        }
    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic, String contentType) {
        uploadBlob(objectName,inputStream,makePublic);
    }

    @Override
    public void copyBlob(String src, String dest) {

        File srcFile = fileSystemService.getOrCreateFilePath(src).toFile();
        File destFile = fileSystemService.getOrCreateFilePath(dest).toFile();
        try {
            FileUtils.copyFile(srcFile, destFile);
        } catch (IOException e) {
            logger.error("Erreur copie fichier:"+src + " vers : "+dest);
            logger.error(e.toString());
        }
    }

    @Override
    public void setStorage(Storage storage) {

    }

    @Override
    public void setAmazonS3Client(AmazonS3 amazonS3Client) {

    }

    @Override
    public void setContainerName(String containerName) {

    }

    @Override
    public boolean delete(String objectName) {
        Path filePath = fileSystemService.getOrCreateFilePath(objectName);
        return filePath.toFile().delete();
    }

    @Override
    public boolean deleteAllFilesInFolder(String folder){
        listBlobs(folder).getFiles().forEach(file -> delete(file.getName()));
        return true;
    }

    @Override
    public void setPublicAccess(boolean isPublic, String filepath) {
        throw new NotImplementedException("setPublicAccess not implemented for FileStoreRepositoru");
    }
}
