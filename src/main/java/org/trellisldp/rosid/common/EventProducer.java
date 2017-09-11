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

import static java.util.Optional.of;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.rosid.common.RDFUtils.getParent;
import static org.trellisldp.rosid.common.RDFUtils.serialize;
import static org.trellisldp.rosid.common.RosidConstants.TOPIC_CACHE;
import static org.trellisldp.rosid.common.RosidConstants.TOPIC_LDP_CONTAINMENT_ADD;
import static org.trellisldp.rosid.common.RosidConstants.TOPIC_LDP_CONTAINMENT_DELETE;
import static org.trellisldp.rosid.common.RosidConstants.TOPIC_LDP_MEMBERSHIP_ADD;
import static org.trellisldp.rosid.common.RosidConstants.TOPIC_LDP_MEMBERSHIP_DELETE;
import static org.trellisldp.spi.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.AS.Create;
import static org.trellisldp.vocabulary.AS.Delete;
import static org.trellisldp.vocabulary.DC.modified;
import static org.trellisldp.vocabulary.LDP.PreferContainment;
import static org.trellisldp.vocabulary.LDP.contains;
import static org.trellisldp.vocabulary.RDF.type;
import static org.trellisldp.vocabulary.Trellis.PreferAudit;
import static org.trellisldp.vocabulary.Trellis.PreferServerManaged;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;

/**
 * @author acoburn
 */
class EventProducer {

    private static final Logger LOGGER = getLogger(EventProducer.class);

    private static final RDF rdf = getInstance();

    private final Set<Quad> existing = new HashSet<>();

    private final Producer<String, String> producer;

    private final IRI identifier;

    private final Dataset dataset;

    private final Boolean async;

    /**
     * Create a new event producer
     * @param producer the kafka producer
     * @param identifier the identifier
     * @param dataset the dataset
     * @param async whether the cache is generated asynchronously
     */
    public EventProducer(final Producer<String, String> producer, final IRI identifier, final Dataset dataset,
            final Boolean async) {
        this.producer = producer;
        this.identifier = identifier;
        this.dataset = dataset;
        this.async = async;
    }

    /**
     * Create a new event producer
     * @param producer the kafka producer
     * @param identifier the identifier
     * @param dataset the dataset
     */
    public EventProducer(final Producer<String, String> producer, final IRI identifier, final Dataset dataset) {
        this(producer, identifier, dataset, false);
    }

    /**
     * Emit messages to the relevant kafka topics
     * @return true if the messages were successfully delivered to the kafka topics; false otherwise
     */
    public Boolean emit() {
        final Boolean isCreate = dataset.contains(of(PreferAudit), null, type, Create);
        final Boolean isDelete = dataset.contains(of(PreferAudit), null, type, Delete);
        try {
            final List<Future<RecordMetadata>> results = new ArrayList<>();

            final String serialized = serialize(dataset);
            if (async) {
                results.add(producer.send(new ProducerRecord<>(TOPIC_CACHE, identifier.getIRIString(), serialized)));
            }

            // Update the containment triples of the parent resource if this is a delete or create operation
            getParent(identifier.getIRIString()).ifPresent(container -> {
                if (isDelete) {
                    dataset.add(rdf.createQuad(PreferContainment, rdf.createIRI(container), contains, identifier));
                    results.add(producer.send(
                                new ProducerRecord<>(TOPIC_LDP_CONTAINMENT_DELETE, container, serialized)));
                    results.add(producer.send(
                                new ProducerRecord<>(TOPIC_LDP_MEMBERSHIP_DELETE, container, serialized)));
                } else if (isCreate) {
                    dataset.add(rdf.createQuad(PreferContainment, rdf.createIRI(container), contains, identifier));
                    results.add(producer.send(new ProducerRecord<>(TOPIC_LDP_CONTAINMENT_ADD, container, serialized)));
                    results.add(producer.send(new ProducerRecord<>(TOPIC_LDP_MEMBERSHIP_ADD, container, serialized)));
                }
            });

            for (final Future<RecordMetadata> result : results) {
                final RecordMetadata res = result.get();
                LOGGER.debug("Send record to topic: {}, {}", res, res.timestamp());
            }

            return true;
        } catch (final InterruptedException | ExecutionException ex) {
            LOGGER.error("Error sending record to kafka topic: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Stream out the added quads
     * @return the added quads
     */
    public Stream<Quad> getAdded() {
        return dataset.stream().filter(q -> !existing.contains(q)).map(q -> (Quad) q);
    }

    /**
     * Stream out the removed quads
     * @return the removed quads
     */
    public Stream<Quad> getRemoved() {
        return existing.stream().filter(q -> !dataset.contains(q))
            .filter(q -> !q.getGraphName().equals(of(PreferServerManaged)) || !modified.equals(q.getPredicate()))
            .map(q -> (Quad) q);
    }

    /**
     * Stream a collection of quads into the event producer
     * @param quads the quads
     */
    public void into(final Stream<? extends Quad> quads) {
        quads.filter(q -> q.getGraphName().equals(of(PreferUserManaged)) ||
                q.getGraphName().equals(of(PreferServerManaged)))
            .forEach(existing::add);
    }
}
