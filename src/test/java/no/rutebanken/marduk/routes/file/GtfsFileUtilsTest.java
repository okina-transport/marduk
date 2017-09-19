package no.rutebanken.marduk.routes.file;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GtfsFileUtilsTest {

    private static final String GTFS_FILE_1 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs.zip";
    private static final String GTFS_FILE_2 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs2.zip";
    private static final String GTFS_FILE_EXTENDED_ROUTE_TYPES = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs_extended_route_types.zip";

    @Test
    public void mergeGtfsFiles_identicalFilesShouldYieldMergedFileIdenticalToOrg() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        File merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input1));

        // Should assert content, but no exceptions must do for now
        // Assert.assertTrue(FileUtils.sizeOf(merged) <= FileUtils.sizeOf(input1));

        Assert.assertTrue(new ZipFileUtils().listFilesInZip(merged).stream().anyMatch(n -> "feed_info.txt".equals(n)));
    }

    @Test
    public void mergeGtfsFiles_nonIdenticalFilesShouldYieldUnion() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        File input2 = new File(GTFS_FILE_2);
        File merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input2));

        Assert.assertTrue(FileUtils.sizeOf(merged) >= FileUtils.sizeOf(input1));
        Assert.assertTrue(FileUtils.sizeOf(merged) >= FileUtils.sizeOf(input2));

        Assert.assertTrue(new ZipFileUtils().listFilesInZip(merged).stream().anyMatch(n -> "feed_info.txt".equals(n)));
    }


    @Test
    public void replaceIdSeparatorInFile() throws Exception {
        File out = GtfsFileUtils.transformIdsToOTPFormat(new File(GTFS_FILE_2));

        List<String> stopLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "stops.txt").toByteArray()));

        Assert.assertEquals("RUT.StopArea.7600100,Oslo S,59.910200,10.755330,RUT.StopArea.7600207", stopLines.get(1));

        List<String> feedInfoLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "feed_info.txt").toByteArray()));

        Assert.assertEquals("Feed info should be unchanged", "RB,Rutebanken,http://www.rutebanken.org,no", feedInfoLines.get(1));
    }

    @Test
    public void replaceExtendedRouteTypesInFile() throws Exception {
        File out = GtfsFileUtils.transformExtendedRouteTypesToBasicRouteTypes(new File(GTFS_FILE_EXTENDED_ROUTE_TYPES));

        List<String> routeLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "routes.txt").toByteArray()));
        routeLines.remove(0); // remove header
        List<String> transformedRouteTypes = routeLines.stream().map(routeLine -> routeLine.split(",")[4]).collect(Collectors.toList());
        Assert.assertTrue("Expected all route types to have been rounded to whole hundreds", transformedRouteTypes.stream().allMatch(routeType -> (routeType.length() == 3 || routeType.length() == 4) && routeType.endsWith("00")));
        Assert.assertEquals("200", transformedRouteTypes.get(0));
        Assert.assertEquals("200", transformedRouteTypes.get(1));
        Assert.assertEquals("200", transformedRouteTypes.get(2));
        Assert.assertEquals("200", transformedRouteTypes.get(3));
        Assert.assertEquals("700", transformedRouteTypes.get(4));
        Assert.assertEquals("1000", transformedRouteTypes.get(5));
        Assert.assertEquals("1000", transformedRouteTypes.get(6));
        Assert.assertEquals("1100", transformedRouteTypes.get(7));


        List<String> stopLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "stops.txt").toByteArray()));
        stopLines.remove(0); // remove header
        Assert.assertTrue("Line without vehicle journey should not be changed", stopLines.get(0).endsWith(","));
        Assert.assertTrue("Line with extended value 701 should be converted to 700", stopLines.get(1).endsWith(",700"));
        Assert.assertTrue("Line with extended value 1112 should be converted to 1100",stopLines.get(2).endsWith(",1100"));

        List<String> feedInfoLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "feed_info.txt").toByteArray()));
        Assert.assertEquals("Feed info should be unchanged", "RB,Rutebanken,http://www.rutebanken.org,no", feedInfoLines.get(1));
    }


}
