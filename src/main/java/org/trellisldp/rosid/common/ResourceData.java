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
package org.trellisldp.rosid.common;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.Triple;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.OA;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
public class ResourceData {

    /**
     * The binary-specific data
     */
    public static class BinaryData {
        /**
         * The binary object identifier
         */
        @JsonProperty("@id")
        public String id;

        /**
         * The binary object format (MIMEType)
         */
        public String format;

        /**
         * The binary object size
         */
        public Long size;

        /**
         * The modification date of the binary object
         */
        public Instant modified;
    }

    /**
     * The JSON-LD context of the resource data
     */
    @JsonProperty("@context")
    public String context = "http://www.trellisrepo.org/ns/trellisresource.jsonld";

    /**
     * The resource identifier
     */
    @JsonProperty("@id")
    public String id;

    /**
     * The interaction model for the resource
     */
    @JsonProperty("@type")
    public String ldpType;

    /**
     * Any additional RDF types for the resource
     */
    @JsonProperty("type")
    public List<String> userTypes;

    /**
     * The binary object data, if available
     */
    public BinaryData binary;

    /**
     * An ldp:inbox for the resource, if available
     */
    public String inbox;

    /**
     * The oa:annotationService for the resource, if available
     */
    public String annotationService;

    /**
     * The modification date
     */
    public Instant modified;

    /**
     * The ldp:membershipResource, if available
     */
    public String membershipResource;

    /**
     * The ldp:hasMemberRelation, if available
     */
    public String hasMemberRelation;

    /**
     * The ldp:isMemberOfRelation, if available
     */
    public String isMemberOfRelation;

    /**
     * The ldp:insertedContentRelation, if available
     */
    public String insertedContentRelation;

    private static final Function<Triple, String> objectUriAsString = triple ->
        ((IRI) triple.getObject()).getIRIString();

    private static final Function<Triple, String> objectLiteralAsString = triple ->
        ((Literal) triple.getObject()).getLexicalForm();

    /**
     * Create a ResourcData object from an identifier and a dataset
     * @param identifier the identifier
     * @param dataset the dataset
     * @return the resource data, if present from the dataset
     */
    public static Optional<ResourceData> from(final IRI identifier, final Dataset dataset) {
        requireNonNull(identifier, "identifier may not be null!");
        requireNonNull(dataset, "dataset may not be null!");

        final ResourceData rd = new ResourceData();
        rd.id = identifier.getIRIString();

        dataset.getGraph(Trellis.PreferServerManaged).ifPresent(graph -> {
            graph.stream(identifier, DC.modified, null).findFirst().map(objectLiteralAsString).map(Instant::parse)
                .ifPresent(date -> rd.modified = date);

            graph.stream(identifier, RDF.type, null).findFirst().map(objectUriAsString)
                .ifPresent(type -> rd.ldpType = type);

            // Populate binary, if present
            graph.stream(identifier, DC.hasPart, null).findFirst().map(Triple::getObject).map(x -> (IRI) x)
                    .ifPresent(id -> {
                rd.binary = new ResourceData.BinaryData();
                rd.binary.id = id.getIRIString();

                graph.stream(id, DC.modified, null).findFirst().map(objectLiteralAsString).map(Instant::parse)
                    .ifPresent(date -> rd.binary.modified = date);

                graph.stream(id, DC.format, null).findFirst().map(objectLiteralAsString)
                    .ifPresent(format -> rd.binary.format = format);

                graph.stream(id, DC.extent, null).findFirst().map(objectLiteralAsString).map(Long::parseLong)
                    .ifPresent(size -> rd.binary.size = size);
            });
        });

        dataset.getGraph(Trellis.PreferUserManaged).ifPresent(graph -> {
            rd.userTypes = graph.stream(identifier, RDF.type, null).map(objectUriAsString).collect(toList());

            graph.stream(identifier, LDP.inbox, null).findFirst().map(objectUriAsString)
                .ifPresent(res -> rd.inbox = res);

            graph.stream(identifier, LDP.membershipResource, null).findFirst().map(objectUriAsString)
                .ifPresent(res -> rd.membershipResource = res);

            graph.stream(identifier, LDP.hasMemberRelation, null).findFirst().map(objectUriAsString)
                .ifPresent(res -> rd.hasMemberRelation = res);

            graph.stream(identifier, LDP.isMemberOfRelation, null).findFirst().map(objectUriAsString)
                .ifPresent(res -> rd.isMemberOfRelation = res);

            graph.stream(identifier, LDP.insertedContentRelation, null).findFirst().map(objectUriAsString)
                .ifPresent(res -> rd.insertedContentRelation = res);

            graph.stream(identifier, OA.annotationService, null).findFirst().map(objectUriAsString)
                .ifPresent(res -> rd.annotationService = res);
        });
        return of(rd).filter(x -> nonNull(x.ldpType)).filter(x -> nonNull(x.modified));
    }
}
