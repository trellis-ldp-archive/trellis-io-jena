/*
 * Copyright Amherst College
 *
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
package edu.amherst.acdc.trellis.io;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Stream.of;
import static edu.amherst.acdc.trellis.vocabulary.JSONLD.compacted;
import static edu.amherst.acdc.trellis.vocabulary.JSONLD.expanded;
import static edu.amherst.acdc.trellis.vocabulary.JSONLD.flattened;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.apache.commons.rdf.api.RDFSyntax.NTRIPLES;
import static org.apache.commons.rdf.api.RDFSyntax.RDFXML;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.apache.jena.graph.Factory.createDefaultGraph;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.graph.NodeFactory.createLiteral;
import static org.apache.jena.graph.Triple.create;
import static org.apache.jena.vocabulary.DCTerms.title;
import static org.apache.jena.vocabulary.DCTerms.spatial;
import static org.apache.jena.vocabulary.DCTypes.Text;
import static org.apache.jena.vocabulary.RDF.Nodes.type;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import edu.amherst.acdc.trellis.spi.NamespaceService;
import edu.amherst.acdc.trellis.spi.SerializationService;
import org.apache.commons.rdf.api.Graph;
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
public class SerializationServiceTest {

    private static final JenaRDF rdf = new JenaRDF();
    private SerializationService service;

    @Mock
    private NamespaceService mockNamespaceService;

    @Before
    public void setUp() {
        final Map<String, String> namespaces = new HashMap<>();
        namespaces.put("dcterms", DCTerms.NS);
        namespaces.put("rdf", RDF.uri);

        service = new JenaSerializationService();
        service.setNamespaceService(mockNamespaceService);
        when(mockNamespaceService.getNamespaces()).thenReturn(namespaces);
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
    public void testXMLSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(getTriples(), out, RDFXML);
        final String output = out.toString("UTF-8");

        final org.apache.jena.graph.Graph graph1 = createDefaultGraph();
        RDFDataMgr.read(graph1, new ByteArrayInputStream(output.getBytes(UTF_8)), null, Lang.RDFXML);
        validateGraph(rdf.asGraph(graph1));

        final Graph graph2 = rdf.createGraph();
        service.read(new ByteArrayInputStream(output.getBytes(UTF_8)), null, RDFXML).forEach(graph2::add);
        validateGraph(graph2);
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
        service.read(getClass().getResourceAsStream("/testRdf.ttl"), "info:trellisrepo/resource", TURTLE)
            .forEach(graph::add);
        validateGraph(graph);
    }

    private static Stream<Triple> getTriples() {
        final Node subject = createURI("info:trellisrepo/resource");
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
