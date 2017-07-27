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

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.apache.jena.riot.Lang.JSONLD;
import static org.apache.jena.riot.system.StreamRDFWriter.defaultSerialization;
import static org.apache.jena.riot.system.StreamRDFWriter.getWriterStream;
import static org.apache.jena.update.UpdateAction.execute;
import static org.apache.jena.update.UpdateFactory.create;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.io.impl.IOUtils.getJsonLdProfile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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
import org.slf4j.Logger;

import org.trellisldp.io.impl.HtmlSerializer;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.NamespaceService;
import org.trellisldp.spi.RuntimeRepositoryException;

/**
 * An IOService implemented using Jena
 *
 * @author acoburn
 */
public class JenaIOService implements IOService {

    private static final Logger LOGGER = getLogger(JenaIOService.class);

    private static final JenaRDF rdf = new JenaRDF();

    private static final Map<String, String> defaultProperties = unmodifiableMap(new HashMap<String, String>() { {
        put("icon", "//s3.amazonaws.com/www.trellisldp.org/assets/img/trellis.png");
        put("css", "//s3.amazonaws.com/www.trellisldp.org/assets/css/trellis.css");
    }});

    private NamespaceService nsService;
    private HtmlSerializer htmlSerializer;

    /**
     * Create a serialization service
     * @param namespaceService the namespace service
     */
    public JenaIOService(final NamespaceService namespaceService) {
        this(namespaceService, defaultProperties);
    }

    /**
     * Create a serialization service
     * @param namespaceService the namespace service
     * @param properties additional properties for the HTML view
     */
    public JenaIOService(final NamespaceService namespaceService, final Map<String, String> properties) {
        this.nsService = namespaceService;
        this.htmlSerializer = new HtmlSerializer(namespaceService,
                properties.getOrDefault("template", "org/trellisldp/io/resource.mustache"), properties);
    }

    @Override
    public void write(final Stream<Triple> triples, final OutputStream output, final RDFSyntax syntax,
            final IRI... profiles) {
        requireNonNull(triples, "The triples stream may not be null!");
        requireNonNull(output, "The output stream may not be null!");
        requireNonNull(syntax, "The RDF syntax value may not be null!");

        if (RDFA_HTML.equals(syntax)) {
            htmlSerializer.write(output, triples, profiles.length > 0 ? profiles[0] : null);
        } else {
            final Lang lang = rdf.asJenaLang(syntax).orElseThrow(() ->
                    new RuntimeRepositoryException("Invalid content type: " + syntax.mediaType));

            final RDFFormat format = defaultSerialization(lang);

            if (nonNull(format)) {
                LOGGER.debug("Writing stream-based RDF: {}", format.toString());
                final StreamRDF stream = getWriterStream(output, format);
                stream.start();
                ofNullable(nsService).ifPresent(svc -> svc.getNamespaces().forEach(stream::prefix));
                triples.map(rdf::asJenaTriple).forEach(stream::triple);
                stream.finish();
            } else {
                LOGGER.debug("Writing buffered RDF: {}", lang.toString());
                final Model model = createDefaultModel();
                ofNullable(nsService).map(NamespaceService::getNamespaces).ifPresent(model::setNsPrefixes);
                triples.map(rdf::asJenaTriple).map(model::asStatement).forEach(model::add);
                if (JSONLD.equals(lang)) {
                    RDFDataMgr.write(output, model.getGraph(), getJsonLdProfile(profiles));
                } else {
                    RDFDataMgr.write(output, model.getGraph(), lang);
                }
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
                    ofNullable(nsService).ifPresent(svc -> svc.setPrefix(prefix, namespace));
                }
            });
        });
        return rdf.asGraph(model).stream().map(t -> (Triple) t);
    }

    @Override
    public void update(final Graph graph, final String update, final String context) {
        requireNonNull(graph, "The input graph may not be null");
        requireNonNull(update, "The update command may not be null");
        execute(create(update, context), rdf.asJenaGraph(graph));
    }
}
