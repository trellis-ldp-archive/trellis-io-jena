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

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static edu.amherst.acdc.trellis.vocabulary.JSONLD.compacted;
import static edu.amherst.acdc.trellis.vocabulary.JSONLD.flattened;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.apache.jena.riot.Lang.JSONLD;
import static org.apache.jena.riot.Lang.RDFXML;
import static org.apache.jena.riot.RDFFormat.JSONLD_COMPACT_FLAT;
import static org.apache.jena.riot.RDFFormat.JSONLD_EXPAND_FLAT;
import static org.apache.jena.riot.RDFFormat.JSONLD_FLATTEN_FLAT;
import static org.apache.jena.riot.RDFFormat.RDFXML_PLAIN;
import static org.apache.jena.riot.system.StreamRDFWriter.defaultSerialization;
import static org.apache.jena.riot.system.StreamRDFWriter.getWriterStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import edu.amherst.acdc.trellis.api.RuntimeRepositoryException;
import edu.amherst.acdc.trellis.spi.NamespaceService;
import edu.amherst.acdc.trellis.spi.SerializationService;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.api.Triple;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.StreamRDF;

/**
 * A SerializationService implemented using Jena
 *
 * @author acoburn
 */
public class JenaSerializationService implements SerializationService {

    private static final JenaRDF rdf = new JenaRDF();

    private final NamespaceService nsService;

    /**
     * Create a Jena-backed serialization service
     * @param namespaceService a namespace service
     */
    public JenaSerializationService(final NamespaceService namespaceService) {
        this.nsService = namespaceService;
    }

    @Override
    public void write(final Stream<Triple> triples, final OutputStream output, final RDFSyntax syntax) {
        write(triples, output, syntax, null);
    }

    @Override
    public void write(final Stream<Triple> triples, final OutputStream output, final RDFSyntax syntax,
            final IRI profile) {
        final Lang lang = rdf.asJenaLang(syntax).orElseThrow(() ->
                new RuntimeRepositoryException("Invalid content type: " + syntax.mediaType));

        final Optional<RDFFormat> format = ofNullable(defaultSerialization(lang));

        if (format.isPresent()) {
            final StreamRDF stream = getWriterStream(output, format.get());
            stream.start();
            nsService.getNamespaces().forEach(stream::prefix);
            triples.map(rdf::asJenaTriple).forEach(stream::triple);
            stream.finish();
        } else {
            final Model model = createDefaultModel();
            model.setNsPrefixes(nsService.getNamespaces());
            triples.map(rdf::asJenaTriple).map(model::asStatement).forEach(model::add);
            if (RDFXML.equals(lang)) {
                RDFDataMgr.write(output, model.getGraph(), RDFXML_PLAIN);
            } else if (JSONLD.equals(lang)) {
                RDFDataMgr.write(output, model.getGraph(), getJsonLdProfile(profile));
            } else {
                RDFDataMgr.write(output, model.getGraph(), lang);
            }
        }
    }

    @Override
    public void read(final Graph graph, final InputStream input, final RDFSyntax syntax) {
        final Model model = createDefaultModel();
        final Lang lang = rdf.asJenaLang(syntax).orElseThrow(() ->
                new RuntimeRepositoryException("Unsupported RDF Syntax: " + syntax.mediaType));

        RDFDataMgr.read(model, input, lang);
        final Set<String> namespaces = nsService.getNamespaces().entrySet().stream().map(Map.Entry::getValue)
                    .collect(toSet());
        model.getNsPrefixMap().forEach((prefix, namespace) -> {
            if (!namespaces.contains(namespace)) {
                nsService.setNamespace(prefix, namespace);
            }
        });
        rdf.asGraph(model).stream().forEach(graph::add);
    }

    private static RDFFormat getJsonLdProfile(final IRI profile) {
        return ofNullable(profile).map(p -> {
            if (p.equals(compacted)) {
                return JSONLD_COMPACT_FLAT;
            } else if (p.equals(flattened)) {
                return JSONLD_FLATTEN_FLAT;
            }
            return JSONLD_EXPAND_FLAT;
        }).orElse(JSONLD_EXPAND_FLAT);
    }
}
