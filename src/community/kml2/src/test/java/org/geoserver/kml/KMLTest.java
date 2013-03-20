/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.kml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.custommonkey.xmlunit.XMLAssert.*;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.namespace.QName;

import org.geoserver.catalog.Catalog;
import org.geoserver.data.test.MockData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.test.RemoteOWSTestSupport;
import org.geoserver.wms.WMSTestSupport;
import org.junit.Test;
import org.w3c.dom.Document;

import com.mockrunner.mock.web.MockHttpServletResponse;

public class KMLTest extends WMSTestSupport {
    
        
    private static final QName STORM_OBS = new QName(MockData.CITE_URI, "storm_obs", MockData.CITE_PREFIX);

    @Override
    protected void setUpTestData(SystemTestData testData) throws Exception {
        super.setUpTestData(testData);
        testData.setUpDefaultRasterLayers();
    }
    
    @Override
    protected String getLogConfiguration() {
        return "/DEFAULT_LOGGING.properties";
    }
    
    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        Catalog catalog = getCatalog();
        testData.addStyle("notthere","notthere.sld",getClass(),catalog);
        testData.addStyle("scaleRange","scaleRange.sld",getClass(),catalog);
        testData.addVectorLayer(STORM_OBS,Collections.EMPTY_MAP, 
                "storm_obs.properties",getClass(),catalog);
    }
    
    
    @Test
    public void testVector() throws Exception {
        Document doc = getAsDOM(
            "wms?request=getmap&service=wms&version=1.1.1" + 
            "&format=" + KMLMapOutputFormat.MIME_TYPE + 
            "&layers=" + MockData.BASIC_POLYGONS.getPrefix() + ":" + MockData.BASIC_POLYGONS.getLocalPart() +
            "&styles=" + MockData.BASIC_POLYGONS.getLocalPart() + 
            "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326" 
        );
        
        assertEquals( getFeatureSource(MockData.BASIC_POLYGONS).getFeatures().size(), 
            doc.getElementsByTagName("Placemark").getLength()
        );
    }
    
    @Test
    public void testVectorScaleRange() throws Exception {
        Document doc = getAsDOM(
            "wms?request=getmap&service=wms&version=1.1.1" + 
            "&format=" + KMLMapOutputFormat.MIME_TYPE + 
            "&layers=" + MockData.BASIC_POLYGONS.getPrefix() + ":" + MockData.BASIC_POLYGONS.getLocalPart() +
            "&styles=scaleRange&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326" 
        );
        
        assertEquals( getFeatureSource(MockData.BASIC_POLYGONS).getFeatures().size(), 
            doc.getElementsByTagName("Placemark").getLength()
        );
    }
   
    @Test
    public void testVectorWithFeatureId() throws Exception {
        Document doc = getAsDOM(
            "wms?request=getmap&service=wms&version=1.1.1" + 
            "&format=" + KMLMapOutputFormat.MIME_TYPE + 
            "&layers=" + MockData.BASIC_POLYGONS.getPrefix() + ":" + MockData.BASIC_POLYGONS.getLocalPart() +
            "&styles=" + MockData.BASIC_POLYGONS.getLocalPart() + 
            "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326" +  
            "&featureid=BasicPolygons.1107531493643"
        );
        
        assertEquals( 1, doc.getElementsByTagName("Placemark").getLength());
    }
    
    @Test
    public void testVectorWithRemoteLayer() throws Exception {
        if(!RemoteOWSTestSupport.isRemoteWFSStatesAvailable(LOGGER))
            return;
        
        Document doc = getAsDOM(
            "wms?request=getmap&service=wms&version=1.1.1" + 
            "&format=" + KMLMapOutputFormat.MIME_TYPE + 
            "&layers=topp:states" + 
            "&styles=Default" + 
            "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326" +
            "&remote_ows_type=wfs" +
            "&remote_ows_url=" + RemoteOWSTestSupport.WFS_SERVER_URL +
            "&cql_filter=PERSONS>20000000"
        );
        // print(doc);
        
        assertEquals( 1, doc.getElementsByTagName("Placemark").getLength());
    }
   
    // see GEOS-1948
    @Test
    public void testMissingGraphic() throws Exception {
        Document doc = getAsDOM(
                "wms?request=getmap&service=wms&version=1.1.1" + 
                "&format=" + KMLMapOutputFormat.MIME_TYPE + 
                "&layers=" + getLayerId(MockData.BRIDGES) +  
                "&styles=notthere" + 
                "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326"
            );
        assertEquals( 1, doc.getElementsByTagName("Placemark").getLength());
    }    
    
    @Test
    public void testContentDisposition() throws Exception {
        MockHttpServletResponse resp = getAsServletResponse(
                "wms?request=getmap&service=wms&version=1.1.1"
                + "&format=" + KMZMapOutputFormat.MIME_TYPE
                + "&layers=" + MockData.BASIC_POLYGONS.getPrefix() + ":" + MockData.BASIC_POLYGONS.getLocalPart()
                + "&styles=" + MockData.BASIC_POLYGONS.getLocalPart()
                + "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326"
            );
        assertEquals("attachment; filename=cite-BasicPolygons.kmz",resp.getHeader("Content-Disposition"));
    }
    
    @Test
    public void testEncodeTime() throws Exception {
        setupTemplate(STORM_OBS, "time.ftl", "${obs_datetime.value}");
        // AA: for the life of me I cannot make xpath work against this output, not sure why, so going
        // to test with strings instead...
        String doc = getAsString("wms?request=getmap&service=wms&version=1.1.1" + "&format="
                + KMLMapOutputFormat.MIME_TYPE + "&layers=" + getLayerId(STORM_OBS)
                + "&styles=&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326&featureId=storm_obs.1321870537475");
        assertTrue(doc.contains("<when>1994-07-0"));
    }

    @Test
    public void testKmltitleFormatOption() throws Exception {
        final String kmlRequest = "wms?request=getmap&service=wms&version=1.1.1" + 
            "&format=" + KMLMapOutputFormat.MIME_TYPE + 
            "&layers=" + getLayerId(MockData.BRIDGES) +  
            "&styles=notthere" + 
            "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326" +
            "&format_options=kmltitle:myCustomLayerTitle";
        
        Document doc = getAsDOM(kmlRequest);
        assertEquals("name", doc.getElementsByTagName("Document").item(0).getFirstChild().getNextSibling().getLocalName());
        assertEquals("myCustomLayerTitle", doc.getElementsByTagName("Document").item(0).getFirstChild().getNextSibling().getTextContent());
    }    

    @Test
    public void testKmltitleFormatOptionWithMultipleLayers() throws Exception {
        final String kmlRequest = "wms?request=getmap&service=wms&version=1.1.1" + 
        "&format=" + KMLMapOutputFormat.MIME_TYPE + 
        "&layers=" + getLayerId(MockData.BRIDGES) + "," + getLayerId(MockData.BASIC_POLYGONS) +
        "&styles=notthere" + "," + MockData.BASIC_POLYGONS.getLocalPart() +
        "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326" +
        "&format_options=kmltitle:myCustomLayerTitle";
        
        Document doc = getAsDOM(kmlRequest);
        print(doc);
        assertEquals("name", doc.getElementsByTagName("Document").item(0).getFirstChild().getNextSibling().getLocalName());
        assertEquals(1, doc.getElementsByTagName("Document").getLength());
        assertEquals(2, doc.getElementsByTagName("Folder").getLength());
        assertEquals("myCustomLayerTitle", doc.getElementsByTagName("Document").item(0).getFirstChild().getNextSibling().getTextContent());
        assertXpathEvaluatesTo("cite:Bridges", "//kml:Folder[1]/kml:name", doc);
        assertXpathEvaluatesTo("cite:BasicPolygons", "//kml:Folder[2]/kml:name", doc);
    }
    
    @Test
    public void testRelativeLinks() throws Exception {
        final String kmlRequest = "wms?request=getmap&service=wms&version=1.1.1" + 
        "&format=" + KMLMapOutputFormat.MIME_TYPE + 
        "&layers=" + getLayerId(MockData.BASIC_POLYGONS) +
        "&styles=" + MockData.BASIC_POLYGONS.getLocalPart() +
        "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326&format_options=rellinks:true";
        
        // first page
        Document dom = getAsDOM(kmlRequest + "&startIndex=0&maxFeatures=1");
        // print(dom);
        // only one link, the "next" one
        assertXpathEvaluatesTo("1", "count(//kml:Folder/kml:NetworkLink)", dom);
        assertXpathEvaluatesTo("next", "//kml:Folder/kml:NetworkLink/@id", dom);
        assertXpathEvaluatesTo("Next page", "//kml:Folder/kml:NetworkLink/kml:description", dom);
        assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/cite/BasicPolygons.kml?startindex=1&maxfeatures=1", "//kml:Folder/kml:NetworkLink/kml:Link/kml:href", dom);

        // mid page
        dom = getAsDOM(kmlRequest + "&startIndex=1&maxFeatures=1");
        // print(dom);
        // only one link, the "next" one
        assertXpathEvaluatesTo("2", "count(//kml:Folder/kml:NetworkLink)", dom);
        assertXpathEvaluatesTo("prev", "//kml:Folder/kml:NetworkLink[1]/@id", dom);
        assertXpathEvaluatesTo("Previous page", "//kml:Folder/kml:NetworkLink[1]/kml:description", dom);
        assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/cite/BasicPolygons.kml?startindex=0&maxfeatures=1", "//kml:Folder/kml:NetworkLink[1]/kml:Link/kml:href", dom);
        assertXpathEvaluatesTo("next", "//kml:Folder/kml:NetworkLink[2]/@id", dom);
        assertXpathEvaluatesTo("Next page", "//kml:Folder/kml:NetworkLink[2]/kml:description", dom);
        assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/cite/BasicPolygons.kml?startindex=2&maxfeatures=1", "//kml:Folder/kml:NetworkLink[2]/kml:Link/kml:href", dom);
        
        // the last page is same as the mid one, as the code does not have enough context to know it's hitting
        // the last one squarely
    }
    
    @Test
    public void testForceGroundOverlay() throws Exception {
        Document dom = getAsDOM("wms?request=getmap&service=wms&version=1.1.1" + 
                "&format=" + KMLMapOutputFormat.MIME_TYPE + 
                "&layers=" + getLayerId(MockData.BASIC_POLYGONS) +
                "&styles=" + MockData.BASIC_POLYGONS.getLocalPart() +
                "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326&format_options=kmscore:0");
        // print(dom);
        
        assertXpathEvaluatesTo("0", "count(//kml:Placemark)", dom);
        assertXpathEvaluatesTo("1", "count(//kml:GroundOverlay)", dom);
        String pngOverlay = "http://localhost:8080/geoserver/wms?service=wms&request=GetMap&version=1.1.1&format=image%2Fpng&layers=cite%3ABasicPolygons&styles=BasicPolygons&height=1024&width=1024&transparent=true&bbox=-180.0%2C-90.0%2C180.0%2C90.0&srs=EPSG%3A4326&format_options=KMSCORE%3A0%3B";
        assertXpathEvaluatesTo(pngOverlay, "//kml:GroundOverlay/kml:Icon/kml:href", dom);
    }
    
    @Test
    public void testRasterLayer() throws Exception {
        Document dom = getAsDOM("wms?request=getmap&service=wms&version=1.1.1" + 
                "&format=" + KMLMapOutputFormat.MIME_TYPE + 
                "&layers=" + getLayerId(MockData.TASMANIA_DEM) +
                "&styles=&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326");
        // print(dom);
        
        assertXpathEvaluatesTo("0", "count(//kml:Placemark)", dom);
        assertXpathEvaluatesTo("1", "count(//kml:GroundOverlay)", dom);
        String pngOverlay = "http://localhost:8080/geoserver/wms?service=wms&request=GetMap&version=1.1.1&format=image%2Fpng&layers=wcs%3ADEM&styles=raster&height=1024&width=1024&transparent=true&bbox=-180.0%2C-90.0%2C180.0%2C90.0&srs=EPSG%3A4326&format_options=KMSCORE%3A0%3B";
        assertXpathEvaluatesTo(pngOverlay, "//kml:GroundOverlay/kml:Icon/kml:href", dom);
    }
    
    @Test
    public void testKMZMixed() throws Exception {
        MockHttpServletResponse response = getAsServletResponse("wms?request=getmap&service=wms&version=1.1.1" + 
                "&format=" + KMZMapOutputFormat.MIME_TYPE + 
                "&layers=" + getLayerId(MockData.BASIC_POLYGONS) + "," + getLayerId(MockData.WORLD) +
                "&styles=" + MockData.BASIC_POLYGONS.getLocalPart() + "," +
                "&height=1024&width=1024&bbox=-180,-90,180,90&srs=EPSG:4326&format_options=kmscore:0");
        
        assertEquals(KMZMapOutputFormat.MIME_TYPE, response.getContentType());
        ByteArrayInputStream bis = getBinaryInputStream(response);
        ZipInputStream zis = new ZipInputStream(bis);
        ZipEntry entry = zis.getNextEntry();
        assertEquals("wms.kml", entry.getName());
        zis.closeEntry();
        entry = zis.getNextEntry();
        assertEquals("images/layer_0.png", entry.getName());
        zis.closeEntry();
        assertNull(zis.getNextEntry());

    }
}