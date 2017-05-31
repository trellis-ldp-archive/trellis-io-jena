/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trellisldp.io;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.stream.Stream.of;
import static org.trellisldp.vocabulary.JSONLD.compacted;
import static org.trellisldp.vocabulary.JSONLD.expanded;
import static org.trellisldp.vocabulary.JSONLD.flattened;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.apache.commons.rdf.api.RDFSyntax.NTRIPLES;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.apache.jena.graph.Factory.createDefaultGraph;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.graph.NodeFactory.createLiteral;
import static org.apache.jena.graph.Triple.create;
import static org.apache.jena.vocabulary.DCTerms.title;
import static org.apache.jena.vocabulary.DCTerms.spatial;
import static org.apache.jena.vocabulary.DCTypes.Text;
import static org.apache.jena.vocabulary.RDF.Nodes.type;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.trellisldp.spi.NamespaceService;
import org.trellisldp.spi.IOService;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.api.Triple;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class IOServiceTest {

    private static final JenaRDF rdf = new JenaRDF();
    private IOService service;

    @Mock
    private NamespaceService mockNamespaceService;

    @Before
    public void setUp() {
        final Map<String, String> namespaces = new HashMap<>();
        namespaces.put("dcterms", DCTerms.NS);
        namespaces.put("rdf", RDF.uri);

        service = new JenaIOService(mockNamespaceService);
        when(mockNamespaceService.getNamespaces()).thenReturn(namespaces);
        when(mockNamespaceService.getPrefix(eq("http://purl.org/dc/terms/"))).thenReturn(Optional.of("dc"));
        when(mockNamespaceService.getPrefix(eq("http://sws.geonames.org/4929022/"))).thenReturn(empty());
        when(mockNamespaceService.getPrefix(eq("http://www.w3.org/1999/02/22-rdf-syntax-ns#")))
            .thenReturn(Optional.of("rdf"));
        when(mockNamespaceService.getPrefix(eq("http://purl.org/dc/dcmitype/")))
            .thenReturn(Optional.of("dcmitype"));
    }

    @Test
    public void testJsonLdDefaultSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(getTriples(), out, JSONLD);
        final String output = out.toString("UTF-8");
        assertTrue(output.contains("\"http://purl.org/dc/terms/title\":[{\"@value\":\"A title\"}]"));
        assertFalse(output.contains("\"@context\":"));
        assertFalse(output.contains("\"@graph\":"));

        final Graph graph = rdf.createGraph();
        service.read(new ByteArrayInputStream(output.getBytes(UTF_8)), null, JSONLD).forEach(graph::add);
        validateGraph(graph);
    }

    @Test
    public void testJsonLdExpandedSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(getTriples(), out, JSONLD, expanded);
        final String output = out.toString("UTF-8");
        assertTrue(output.contains("\"http://purl.org/dc/terms/title\":[{\"@value\":\"A title\"}]"));
        assertFalse(output.contains("\"@context\":"));
        assertFalse(output.contains("\"@graph\":"));

        final Graph graph = rdf.createGraph();
        service.read(new ByteArrayInputStream(output.getBytes(UTF_8)), null, JSONLD).forEach(graph::add);
        validateGraph(graph);
    }

    @Test
    public void testJsonLdCompactedSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(getTriples(), out, JSONLD, compacted);
        final String output = out.toString("UTF-8");
        assertTrue(output.contains("\"title\":\"A title\""));
        assertTrue(output.contains("\"@context\":"));
        assertFalse(output.contains("\"@graph\":"));

        final Graph graph = rdf.createGraph();
        service.read(new ByteArrayInputStream(output.getBytes(UTF_8)), null, JSONLD).forEach(graph::add);
        validateGraph(graph);
    }

    @Test
    public void testJsonLdFlattenedSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(getTriples(), out, JSONLD, flattened);
        final String output = out.toString("UTF-8");
        assertTrue(output.contains("\"title\":\"A title\""));
        assertTrue(output.contains("\"@context\":"));
        assertTrue(output.contains("\"@graph\":"));

        final Graph graph = rdf.createGraph();
        service.read(new ByteArrayInputStream(output.getBytes(UTF_8)), null, JSONLD).forEach(graph::add);
        validateGraph(graph);
    }

    @Test
    public void testNTriplesSerializer() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(getTriples(), out, NTRIPLES);
        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        final org.apache.jena.graph.Graph graph = createDefaultGraph();
        RDFDataMgr.read(graph, in, Lang.NTRIPLES);
        validateGraph(rdf.asGraph(graph));
    }

    @Test
    public void testTurtleSerializer() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(getTriples(), out, TURTLE);
        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        final org.apache.jena.graph.Graph graph = createDefaultGraph();
        RDFDataMgr.read(graph, in, Lang.TURTLE);
        validateGraph(rdf.asGraph(graph));
    }

    @Test
    public void testTurtleReaderWithContext() {
        final Graph graph = rdf.createGraph();
        service.read(getClass().getResourceAsStream("/testRdf.ttl"), "trellis:repository/resource", TURTLE)
            .forEach(graph::add);
        validateGraph(graph);
    }

    @Test
    public void testHtmlSerializer() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(getTriples(), out, RDFA_HTML);
        final String html = new String(out.toByteArray(), UTF_8);
        assertTrue(html.contains("<title>A title</title>"));
        assertTrue(html.contains("<a href=\"http://sws.geonames.org/4929022/\">http://sws.geonames.org/4929022/</a>"));
        assertTrue(html.contains("<a href=\"http://purl.org/dc/terms/title\">dc:title</a>"));
        assertTrue(html.contains("<a href=\"http://purl.org/dc/terms/spatial\">dc:spatial</a>"));
        assertTrue(html.contains("<a href=\"http://purl.org/dc/dcmitype/Text\">dcmitype:Text</a>"));
        assertTrue(html.contains("<h1>A title</h1>"));
    }

    @Test
    public void testUpdate() {
        final Graph graph = rdf.createGraph();
        getTriples().forEach(graph::add);
        assertEquals(3L, graph.size());
        service.update(graph, "DELETE WHERE { ?s <http://purl.org/dc/terms/title> ?o }", "test:info");
        assertEquals(2L, graph.size());
        service.update(graph, "INSERT { " +
                "<> <http://purl.org/dc/terms/title> \"Other title\" } WHERE {}",
                "trellis:repository/resource");
        assertEquals(3L, graph.size());
        service.update(graph, "DELETE WHERE { ?s ?p ?o };" +
                "INSERT { <> <http://purl.org/dc/terms/title> \"Other title\" } WHERE {}",
                "trellis:repository");
        assertEquals(1L, graph.size());
        assertEquals("<trellis:repository>", graph.stream().findFirst().map(Triple::getSubject)
                .map(RDFTerm::ntriplesString).get());
    }

    private static Stream<Triple> getTriples() {
        final Node subject = createURI("trellis:repository/resource");
        return of(
                create(subject, title.asNode(), createLiteral("A title")),
                create(subject, spatial.asNode(), createURI("http://sws.geonames.org/4929022/")),
                create(subject, type, Text.asNode()))
            .map(rdf::asTriple);
    }

    private static void validateGraph(final Graph graph) {
        getTriples().forEach(triple -> {
            assertTrue(graph.contains(triple));
        });
    }
}
