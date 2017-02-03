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

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static edu.amherst.acdc.trellis.vocabulary.JSONLD.compacted;
import static edu.amherst.acdc.trellis.vocabulary.JSONLD.compacted_flattened;
import static edu.amherst.acdc.trellis.vocabulary.JSONLD.expanded;
import static edu.amherst.acdc.trellis.vocabulary.JSONLD.expanded_flattened;
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
import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import edu.amherst.acdc.trellis.api.RuntimeRepositoryException;
import edu.amherst.acdc.trellis.spi.NamespaceService;
import edu.amherst.acdc.trellis.spi.SerializationService;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.api.Triple;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.StreamRDF;
import org.slf4j.Logger;

/**
 * A SerializationService implemented using Jena
 *
 * @author acoburn
 */
public class JenaSerializationService implements SerializationService {

    private static final Logger LOGGER = getLogger(JenaSerializationService.class);

    private static final JenaRDF rdf = new JenaRDF();

    private static final Map<IRI, RDFFormat> JSONLD_FORMATS = unmodifiableMap(new HashMap<IRI, RDFFormat>() { {
        put(compacted, JSONLD_COMPACT_FLAT);
        put(flattened, JSONLD_FLATTEN_FLAT);
        put(expanded, JSONLD_EXPAND_FLAT);
        put(compacted_flattened, JSONLD_FLATTEN_FLAT);
        put(expanded_flattened, JSONLD_FLATTEN_FLAT);
    }});

    private NamespaceService nsService;

    @Override
    public synchronized void bind(final NamespaceService namespaceService) {
        requireNonNull(namespaceService, "The namespaceService may not be null!");
        this.nsService = namespaceService;
    }

    @Override
    public synchronized void unbind(final NamespaceService namespaceService) {
        if (this.nsService == namespaceService) {
            this.nsService = null;
        }
    }

    @Override
    public void write(final Stream<Triple> triples, final OutputStream output, final RDFSyntax syntax,
            final IRI... profiles) {
        requireNonNull(triples, "The triples stream may not be null!");
        requireNonNull(output, "The output stream may not be null!");
        requireNonNull(syntax, "The RDF syntax value may not be null!");

        final Lang lang = rdf.asJenaLang(syntax).orElseThrow(() ->
                new RuntimeRepositoryException("Invalid content type: " + syntax.mediaType));

        final Optional<RDFFormat> format = ofNullable(defaultSerialization(lang));

        if (format.isPresent()) {
            LOGGER.debug("Writing stream-based RDF: {}", format.get().toString());
            final StreamRDF stream = getWriterStream(output, format.get());
            stream.start();
            ofNullable(nsService).ifPresent(svc -> svc.getNamespaces().forEach(stream::prefix));
            triples.map(rdf::asJenaTriple).forEach(stream::triple);
            stream.finish();
        } else {
            LOGGER.debug("Writing buffered RDF: {}", lang.toString());
            final Model model = createDefaultModel();
            ofNullable(nsService).map(NamespaceService::getNamespaces).ifPresent(model::setNsPrefixes);
            triples.map(rdf::asJenaTriple).map(model::asStatement).forEach(model::add);
            if (RDFXML.equals(lang)) {
                RDFDataMgr.write(output, model.getGraph(), RDFXML_PLAIN);
            } else if (JSONLD.equals(lang)) {
                RDFDataMgr.write(output, model.getGraph(), getJsonLdProfile(profiles));
            } else {
                RDFDataMgr.write(output, model.getGraph(), lang);
            }
        }
    }

    @Override
    public Stream<Triple> read(final InputStream input, final String context, final RDFSyntax syntax) {
        requireNonNull(input, "The input stream may not be null!");
        requireNonNull(syntax, "The syntax value may not be null!");

        final Model model = createDefaultModel();
        final Lang lang = rdf.asJenaLang(syntax).orElseThrow(() ->
                new RuntimeRepositoryException("Unsupported RDF Syntax: " + syntax.mediaType));

        RDFDataMgr.read(model, input, context, lang);
        ofNullable(nsService).map(NamespaceService::getNamespaces).map(Map::entrySet).ifPresent(ns -> {
            final Set<String> namespaces = ns.stream().map(Map.Entry::getValue).collect(toSet());
            model.getNsPrefixMap().forEach((prefix, namespace) -> {
                if (!namespaces.contains(namespace)) {
                    LOGGER.debug("Setting prefix ({}) for namespace {}", prefix, namespace);
                    nsService.setPrefix(prefix, namespace);
                }
            });
        });
        return rdf.asGraph(model).stream().map(t -> (Triple) t);
    }

    private static RDFFormat getJsonLdProfile(final IRI... profiles) {
        return of(mergeProfiles(profiles)).map(JSONLD_FORMATS::get).orElse(JSONLD_EXPAND_FLAT);
    }

    /**
     * This will combine multiple JSON-LD profiles into a single profile. For example,
     * jsonld:compacted + jsonld:flattened = jsonld:compacted_flattened
     * The default (i.e. no arguments) is jsonld:expanded
     * Multiple, conflicting profiles (e.g. jsonld:compacted + jsonld:expanded) will result
     * in a "last profile wins" situation. Profile URIs that are not part of the JSON-LD
     * vocabulary are ignored.
     */
    private static IRI mergeProfiles(final IRI... profiles) {
        Boolean isExpanded = true;
        Boolean isFlattened = false;

        for (final IRI uri : profiles) {
            if (compacted_flattened.equals(uri) || expanded_flattened.equals(uri)) {
                return uri;
            }

            if (flattened.equals(uri)) {
                isFlattened = true;
            } else if (compacted.equals(uri)) {
                isExpanded = false;
            } else if (expanded.equals(uri)) {
                isExpanded = true;
            }
        }
        if (isFlattened) {
            return isExpanded ? expanded_flattened : compacted_flattened;
        }
        return isExpanded ? expanded : compacted;
    }
}
