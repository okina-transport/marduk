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

package no.rutebanken.marduk.routes.file.beans;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE;
import static no.rutebanken.marduk.routes.file.FileType.GTFS;
import static no.rutebanken.marduk.routes.file.FileType.INVALID_FILE_NAME;
import static no.rutebanken.marduk.routes.file.FileType.NEPTUNE;
import static no.rutebanken.marduk.routes.file.FileType.NETEXPROFILE;
import static org.junit.Assert.assertEquals;

public class FileTypeClassifierBeanTest {

    private FileTypeClassifierBean bean;

    @Before
    public void before() {
        bean = new FileTypeClassifierBean();
    }

    @Test
    public void classifyGtfsFile() throws Exception {
        assertFileType("gtfs.zip", GTFS, "gtfs");
    }

    @Test
    public void classifyGtfsFileContainingFolder() throws Exception {
        // The file is known to be invalid - repack zip
        File rePackedZipFile = ZipFileUtils.rePackZipFile(IOUtils.toByteArray(this.getClass().getResourceAsStream("gtfs-folder.zip")));
        assertFileType(rePackedZipFile, GTFS,"gtfs");
    }

    @Test
    public void classifyNetexFile() throws Exception {
        assertFileType("netex.zip", NETEXPROFILE,null);
    }

    @Test(expected = RuntimeException.class)
    public void classifyNetexFileFromRuter() throws Exception {
        assertFileType("AOR.zip", NETEXPROFILE,null);
    }

    @Test
    public void classifyNetexWithNeptuneFileNameInside() throws Exception {
        assertFileType("netex_with_neptune_file_name_inside.zip", NETEXPROFILE,null);
    }

    @Test
    public void classifyNetexWithTwoFiles() throws Exception {
        assertFileType("netex_with_two_files.zip", NETEXPROFILE,null);
    }

    @Test(expected = FileValidationException.class)
    public void classifyNetexWithTwoFilesOneInvalid() throws Exception {
        assertFileType("netex_with_two_files_one_invalid.zip", NETEXPROFILE,null);
    }

    @Test
    public void classifyFileNameWithNonISO_8859_1CharacterAsInvalid() throws Exception {
        // The å in ekspressbåt below is encoded as 97 ('a') + 778 (ring above)
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream("netex.zip"));
        assertFileType("sof-20170904121616-2907_20170904_Buss_og_ekspressbåt_til_rutesøk_19.06.2017-28.02.2018 (1).zip", data, INVALID_FILE_NAME,null);
    }

    @Test
    public void classifyFileNameWithOnlyISO_8859_1CharacterAsValid() throws Exception {
        // The å in ekspressbåt below is encoded as a regular 229 ('å')
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream("netex.zip"));
        assertFileType("sof-20170904121616-2907_20170904_Buss_og_ekspressbåt_til_rutesøk_19.06.2017-28.02.2018 (1).zip", data, NETEXPROFILE,null);
    }

    @Test
    public void nonXMLFilePatternShouldMatchOtherFileTypes() {
        Assert.assertTrue("test.log".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        Assert.assertTrue("test.xml.log".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        Assert.assertTrue("test.xml2".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        Assert.assertTrue("test.txml".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
    }

    @Test
    public void nonXMLFilePatternShouldNotMatchXMLFiles() {
        Assert.assertFalse("test.xml".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        Assert.assertFalse("test.XML".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        Assert.assertFalse("test.test.xml".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
    }

   //missing test file
   // @Test
    public void classifyFileNameNeptune() throws Exception {
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream("inputFile.zip"));
        assertFileType("someFileName.zip", data, NEPTUNE,"neptune");
    }

    private void assertFileType(String fileName, FileType expectedFileType, String importType) throws IOException {
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream(fileName));
        assertFileType(fileName, data, expectedFileType,importType);
    }

    private void assertFileType(File file, FileType expectedFileType,String importType) throws IOException {
        byte[] data = IOUtils.toByteArray(new FileInputStream(file));
        assertFileType(file.getName(), data, expectedFileType, importType);
    }

    private void assertFileType(String fileName, byte[] data, FileType expectedFileType, String importType) {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = ExchangeBuilder.anExchange(context).build();

        exchange.getIn().setHeader(IMPORT_TYPE, importType);
        exchange.getIn().setHeader(FILE_HANDLE, fileName);

        FileType resultType = bean.classifyFile(exchange, data);
        assertEquals(expectedFileType, resultType);
    }

}
