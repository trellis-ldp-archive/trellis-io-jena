/*
 * Copyright 2016 Amherst College
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
package edu.amherst.acdc.trellis.service.io.jena;

import static java.util.stream.Stream.of;
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
import org.apache.commons.rdf.api.Triple;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.graph.Graph;
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

    private static final String JSONLD_COMPACTED = "http://www.w3.org/ns/json-ld#compacted";
    private static final String JSONLD_EXPANDED = "http://www.w3.org/ns/json-ld#expanded";
    private static final String JSONLD_FLATTENED = "http://www.w3.org/ns/json-ld#flattened";
    private static final JenaRDF rdf = new JenaRDF();
    private SerializationService service;

    @Mock
    private NamespaceService mockNamespaceService;

    @Before
    public void setUp() {
        final Map<String, String> namespaces = new HashMap<>();
        namespaces.put("dcterms", DCTerms.NS);
        namespaces.put("rdf", RDF.uri);

        service = new JenaSerializationService(mockNamespaceService);
        when(mockNamespaceService.getNamespaces()).thenReturn(namespaces);
    }

    @Test
    public void testJsonLdDefaultSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.serialize(getTriples(), out, JSONLD);
        final String output = out.toString("UTF-8");
        assertTrue(output.contains("\"http://purl.org/dc/terms/title\":[{\"@value\":\"A title\"}]"));
        assertFalse(output.contains("\"@context\":"));
        assertFalse(output.contains("\"@graph\":"));
    }

    @Test
    public void testJsonLdExpandedSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.serialize(getTriples(), out, JSONLD, JSONLD_EXPANDED);
        final String output = out.toString("UTF-8");
        assertTrue(output.contains("\"http://purl.org/dc/terms/title\":[{\"@value\":\"A title\"}]"));
        assertFalse(output.contains("\"@context\":"));
        assertFalse(output.contains("\"@graph\":"));
    }

    @Test
    public void testJsonLdCompactedSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.serialize(getTriples(), out, JSONLD, JSONLD_COMPACTED);
        final String output = out.toString("UTF-8");
        assertTrue(output.contains("\"title\":\"A title\""));
        assertTrue(output.contains("\"@context\":"));
        assertFalse(output.contains("\"@graph\":"));
    }

    @Test
    public void testJsonLdFlattenedSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.serialize(getTriples(), out, JSONLD, JSONLD_FLATTENED);
        final String output = out.toString("UTF-8");
        assertTrue(output.contains("\"title\":\"A title\""));
        assertTrue(output.contains("\"@context\":"));
        assertTrue(output.contains("\"@graph\":"));
    }

    @Test
    public void testXMLSerializer() throws UnsupportedEncodingException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.serialize(getTriples(), out, RDFXML);
        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        final Graph graph = createDefaultGraph();
        RDFDataMgr.read(graph, in, Lang.RDFXML);
        validateGraph(graph);
    }

    @Test
    public void testNTriplesSerializer() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.serialize(getTriples(), out, NTRIPLES);
        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        final Graph graph = createDefaultGraph();
        RDFDataMgr.read(graph, in, Lang.NTRIPLES);
        validateGraph(graph);
    }

    @Test
    public void testTurtleSerializer() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.serialize(getTriples(), out, TURTLE);
        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        final Graph graph = createDefaultGraph();
        RDFDataMgr.read(graph, in, Lang.TURTLE);
        validateGraph(graph);
    }


    private static Stream<Triple> getTriples() {
        final Node subject = createURI("info:trellis/resource");
        return of(
                create(subject, title.asNode(), createLiteral("A title")),
                create(subject, spatial.asNode(), createURI("http://sws.geonames.org/4929022/")),
                create(subject, type, Text.asNode()))
            .map(rdf::asTriple);
    }

    private static void validateGraph(final Graph graph) {
        getTriples().map(rdf::asJenaTriple).forEach(triple -> {
            assertTrue(graph.contains(triple));
        });
    }
}
