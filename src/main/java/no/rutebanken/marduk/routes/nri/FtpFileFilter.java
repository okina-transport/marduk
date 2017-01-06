package no.rutebanken.marduk.routes.nri;

import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileFilter;
import org.springframework.stereotype.Component;

@Component(value = "ftpFileFilter")
public class FtpFileFilter<T> implements GenericFileFilter<T> {

	public boolean accept(GenericFile<T> file) {

		String filenameUppercase = file.getFileName().toUpperCase();

		// we only want zip or rar files or directories and not the GTFS folder
		return file.isDirectory()
				|| (!file.isDirectory() && !file.getAbsoluteFilePath().contains("/GTFS/") && (filenameUppercase.endsWith(".ZIP") || filenameUppercase.endsWith(".RAR")));
	}
}