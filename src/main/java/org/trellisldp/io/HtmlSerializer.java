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
import static java.util.stream.Collectors.toList;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Triple;

import org.trellisldp.spi.NamespaceService;

/**
 * @author acoburn
 */
class HtmlSerializer {

    private static final MustacheFactory mf = new DefaultMustacheFactory();

    private final NamespaceService namespaceService;
    private final Mustache template;


    /**
     * Create a ResourceView object
     * @param namespaceService a namespace service
     * @param template the template name
     */
    public HtmlSerializer(final NamespaceService namespaceService, final String template) {
        this.template = mf.compile(template);
        this.namespaceService = namespaceService;
    }

    /**
     * Create a ResourceView object
     * @param namespaceService a namespace service
     */
    public HtmlSerializer(final NamespaceService namespaceService) {
        this(namespaceService, "org/trellisldp/io/resource.mustache");
    }

    public void write(final OutputStream out, final Stream<Triple> triples, final IRI subject) {
        final Writer writer = new OutputStreamWriter(out, UTF_8);
        try {
            template.execute(writer, new HtmlData(namespaceService, subject, triples.collect(toList()))).flush();
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
