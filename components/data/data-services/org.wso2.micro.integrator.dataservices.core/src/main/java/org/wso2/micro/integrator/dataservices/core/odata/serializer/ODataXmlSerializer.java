/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.dataservices.core.odata.serializer;

import java.io.OutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.AbstractEntityCollection;
import org.apache.olingo.commons.api.data.ComplexValue;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.EntityIterator;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.data.Linked;
import org.apache.olingo.commons.api.data.Operation;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.Operation.Type;
import org.apache.olingo.commons.api.edm.EdmComplexType;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeException;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.edm.EdmStructuredType;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.constants.EdmTypeKind;
import org.apache.olingo.commons.core.edm.primitivetype.EdmPrimitiveTypeFactory;
import org.apache.olingo.commons.core.edm.primitivetype.EdmString;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerStreamResult;
import org.apache.olingo.server.api.serializer.SerializerException.MessageKeys;
import org.apache.olingo.server.api.uri.UriHelper;
import org.apache.olingo.server.api.uri.queryoption.CountOption;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.LevelsExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.apache.olingo.server.core.serializer.utils.ContextURLBuilder;
import org.apache.olingo.server.core.serializer.utils.ExpandSelectHelper;
import org.apache.olingo.server.core.uri.UriHelperImpl;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.wso2.micro.integrator.dataservices.core.odata.StreamingEntityIterator;

import static org.wso2.micro.integrator.dataservices.core.odata.ODataConstants.ODATA_COUNT_WITHOUT_PAGING;

/**
 * This class is used to create an OData serializer with XML content.
 */
public class ODataXmlSerializer implements ODataSerializer {
    private static final boolean excludePagingForOdataCount;

    /**
     * Initializing the flag to determine if OData count should not be affected with
     * $top, $skip, or $expand query options.
     */
    static {
        String propertyValue = System.getProperty(ODATA_COUNT_WITHOUT_PAGING, "false");
        excludePagingForOdataCount = Boolean.parseBoolean(propertyValue);
    }

    public final static String NAMESPACE_URI = "http://docs.oasis-open.org/odata/ns/";

    public ODataXmlSerializer() {
    }

    /**
     * This method replaces invalid unicode characters.
     *
     * @param expectedType
     * @param value
     * @param isUniCode
     * @param invalidCharacterReplacement
     */
    static String replaceInvalidCharacters(EdmPrimitiveType expectedType, String value, Boolean isUniCode,
                                           String invalidCharacterReplacement) {
        if (expectedType instanceof EdmString && invalidCharacterReplacement != null && isUniCode != null
                && isUniCode) {
            String s = value;
            StringBuilder result = null;
            for (int i = 0; i < s.length(); ++i) {
                char c = s.charAt(i);
                if (c <= ' ' && c != ' ' && c != '\n' && c != '\t' && c != '\r') {
                    if (result == null) {
                        result = new StringBuilder();
                        result.append(s.substring(0, i));
                    }
                    result.append(invalidCharacterReplacement);
                } else if (result != null) {
                    result.append(c);
                }
            }
            if (result == null) {
                return value;
            } else {
                return result.toString();
            }
        } else {
            return value;
        }
    }

    /**
     * This method generates the OData XML response.
     *
     * @param metadata
     * @param entityType
     * @param entitySet
     * @param options
     * @param outputStream
     * @throws SerializerException
     */
    public void entityCollectionIntoStream(ServiceMetadata metadata, EdmEntityType entityType, EntityIterator entitySet,
                                           EntityCollectionSerializerOptions options, OutputStream outputStream)
            throws SerializerException {
        ContextURL contextURL = this.checkContextURL(options == null ? null : options.getContextURL());
        String name = contextURL == null ? null : contextURL.getEntitySetOrSingletonOrType();
        try {
            XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream, "UTF-8");
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("a", "feed", "http://www.w3.org/2005/Atom");
            writer.writeNamespace("a", "http://www.w3.org/2005/Atom");
            writer.writeNamespace("m", NAMESPACE_URI + "metadata");
            writer.writeNamespace("d", NAMESPACE_URI + "data");
            writer.writeAttribute("m", NAMESPACE_URI + "metadata", "context",
                                  ContextURLBuilder.create(contextURL).toASCIIString());
            this.writeMetadataETag(metadata, writer);
            if (options != null && options.getId() != null) {
                writer.writeStartElement("a", "id", "http://www.w3.org/2005/Atom");
                writer.writeCharacters(options.getId());
                writer.writeEndElement();
            }
            boolean writeOnlyRef = options != null && options.getWriteOnlyReferences();
            if (options == null) {
                this.writeEntitySet(metadata, entityType, entitySet, (ExpandOption) null, (Integer) null,
                                    (SelectOption) null, (String) null, writer, writeOnlyRef, name, (Set) null);
            } else {
                this.writeEntitySet(metadata, entityType, entitySet, options.getExpand(), (Integer) null,
                                    options.getSelect(), options.xml10InvalidCharReplacement(), writer, writeOnlyRef,
                                    name, (Set) null);
            }
            if (options != null && options.getCount() != null && options.getCount().getValue()
                    && entitySet.getCount() != null) {
                this.writeCount(entitySet, writer, excludePagingForOdataCount);
            }
            if (entitySet != null && entitySet.getNext() != null) {
                this.writeNextLink(entitySet, writer);
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
        } catch (XMLStreamException e) {
            SerializerException cachedException = new SerializerException("An I/O exception occurred.", e,
                                                                          MessageKeys.IO_EXCEPTION, new String[0]);
            throw cachedException;
        }
    }

    public SerializerStreamResult entityCollectionStreamed(ServiceMetadata metadata, EdmEntityType entityType,
                                                           EntityIterator entities,
                                                           EntityCollectionSerializerOptions options)
            throws SerializerException {
        return ODataWritableContent.with(entities, entityType, this, metadata, options).build();
    }

    /**
     * This method returns the context URL.
     *
     * @param contextURL
     * @throws SerializerException
     */
    private ContextURL checkContextURL(ContextURL contextURL) throws SerializerException {
        if (contextURL == null) {
            throw new SerializerException("ContextURL null!", MessageKeys.NO_CONTEXT_URL, new String[0]);
        } else {
            return contextURL;
        }
    }

    /**
     * This method writes the ETag.
     *
     * @param metadata
     * @param writer
     * @throws XMLStreamException
     */
    private void writeMetadataETag(ServiceMetadata metadata, XMLStreamWriter writer) throws XMLStreamException {
        if (metadata != null && metadata.getServiceMetadataETagSupport() != null
                && metadata.getServiceMetadataETagSupport().getMetadataETag() != null) {
            writer.writeAttribute("m", NAMESPACE_URI + "metadata", "metadata-etag",
                                  metadata.getServiceMetadataETagSupport().getMetadataETag());
        }
    }

    /**
     * This method writes the result entity set.
     *
     * @param metadata
     * @param entityType
     * @param entitySet
     * @param expand
     * @param toDepth
     * @param select
     * @param xml10InvalidCharReplacement
     * @param writer
     * @param writeOnlyRef
     * @param name
     * @param ancestors
     * @throws XMLStreamException
     * @throws SerializerException
     */
    protected void writeEntitySet(ServiceMetadata metadata, EdmEntityType entityType,
                                  AbstractEntityCollection entitySet, ExpandOption expand, Integer toDepth,
                                  SelectOption select, String xml10InvalidCharReplacement, XMLStreamWriter writer,
                                  boolean writeOnlyRef, String name, Set<String> ancestors)
            throws XMLStreamException, SerializerException {
        Iterator iterator = entitySet.iterator();
        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();
            this.writeEntity(metadata, entityType, entity, (ContextURL) null, expand, toDepth, select,
                             xml10InvalidCharReplacement, writer, false, writeOnlyRef, name, ancestors);
        }
    }

    /**
     * This method returns the ID of an entity.
     *
     * @param entity
     * @param entityType
     * @param name
     * @throws SerializerException
     */
    private String getEntityId(Entity entity, EdmEntityType entityType, String name) throws SerializerException {
        if (entity.getId() == null) {
            if (entity == null || entityType == null || entityType.getKeyPredicateNames() == null || name == null) {
                throw new SerializerException("Entity id is null.", MessageKeys.MISSING_ID, new String[0]);
            }
            UriHelper uriHelper = new UriHelperImpl();
            entity.setId(URI.create(name + '(' + uriHelper.buildKeyPredicate(entityType, entity) + ')'));
        }
        return entity.getId().toASCIIString();
    }

    /**
     * This method writes a single entity.
     *
     * @param metadata
     * @param entityType
     * @param entity
     * @param contextURL
     * @param expand
     * @param toDepth
     * @param select
     * @param xml10InvalidCharReplacement
     * @param writer
     * @param top
     * @param writeOnlyRef
     * @param name
     * @param ancestors
     * @throws XMLStreamException
     * @throws SerializerException
     */
    protected void writeEntity(ServiceMetadata metadata, EdmEntityType entityType, Entity entity, ContextURL contextURL,
                               ExpandOption expand, Integer toDepth, SelectOption select,
                               String xml10InvalidCharReplacement, XMLStreamWriter writer, boolean top,
                               boolean writeOnlyRef, String name, Set<String> ancestors)
            throws XMLStreamException, SerializerException {
        boolean cycle = false;
        if (expand != null) {
            if (ancestors == null) {
                ancestors = new HashSet();
            }
            cycle = !((Set) ancestors).add(this.getEntityId(entity, entityType, name));
        }
        if (!cycle && !writeOnlyRef) {
            try {
                writer.writeStartElement("a", "entry", "http://www.w3.org/2005/Atom");
                if (top) {
                    writer.writeNamespace("a", "http://www.w3.org/2005/Atom");
                    writer.writeNamespace("m", NAMESPACE_URI + "metadata");
                    writer.writeNamespace("d", NAMESPACE_URI + "data");
                    if (contextURL != null) {
                        writer.writeAttribute("m", NAMESPACE_URI + "metadata", "context",
                                              ContextURLBuilder.create(contextURL).toASCIIString());
                        this.writeMetadataETag(metadata, writer);
                    }
                }
                if (entity.getETag() != null) {
                    writer.writeAttribute("m", NAMESPACE_URI + "metadata", "etag", entity.getETag());
                }

                if (entity.getId() != null) {
                    writer.writeStartElement("http://www.w3.org/2005/Atom", "id");
                    writer.writeCharacters(entity.getId().toASCIIString());
                    writer.writeEndElement();
                }
                this.writerAuthorInfo(entity.getTitle(), writer);
                if (entity.getId() != null) {
                    writer.writeStartElement("http://www.w3.org/2005/Atom", "link");
                    writer.writeAttribute("rel", "edit");
                    writer.writeAttribute("href", entity.getId().toASCIIString());
                    writer.writeEndElement();
                }
                if (entityType.hasStream()) {
                    writer.writeStartElement("http://www.w3.org/2005/Atom", "content");
                    writer.writeAttribute("type", entity.getMediaContentType());
                    if (entity.getMediaContentSource() != null) {
                        writer.writeAttribute("src", entity.getMediaContentSource().toString());
                    } else {
                        String id = entity.getId().toASCIIString();
                        writer.writeAttribute("src", id + (id.endsWith("/") ? "" : "/") + "$value");
                    }
                    writer.writeEndElement();
                }
                Iterator iterator = entity.getMediaEditLinks().iterator();
                while (iterator.hasNext()) {
                    Link link = (Link) iterator.next();
                    this.writeLink(writer, link);
                }
                EdmEntityType resolvedType = this.resolveEntityType(metadata, entityType, entity.getType());
                this.writeNavigationProperties(metadata, resolvedType, entity, expand, toDepth,
                                               xml10InvalidCharReplacement, (Set) ancestors, name, writer);
                writer.writeStartElement("a", "category", "http://www.w3.org/2005/Atom");
                writer.writeAttribute("scheme", NAMESPACE_URI + "scheme");
                writer.writeAttribute("term", "#" + resolvedType.getFullQualifiedName().getFullQualifiedNameAsString());
                writer.writeEndElement();
                if (!entityType.hasStream()) {
                    writer.writeStartElement("http://www.w3.org/2005/Atom", "content");
                    writer.writeAttribute("type", "application/xml");
                }
                writer.writeStartElement("m", "properties", NAMESPACE_URI + "metadata");
                this.writeProperties(metadata, resolvedType, entity.getProperties(), select,
                                     xml10InvalidCharReplacement, writer);
                writer.writeEndElement();
                if (!entityType.hasStream()) {
                    writer.writeEndElement();
                }
                this.writeOperations(entity.getOperations(), writer);
                writer.writeEndElement();
            } finally {
                if (!cycle && ancestors != null) {
                    ((Set) ancestors).remove(this.getEntityId(entity, entityType, name));
                }
            }
        } else {
            this.writeReference(entity, contextURL, writer, top);
        }
    }

    /**
     * This method writes entity operations.
     *
     * @param operations
     * @param writer
     * @throws XMLStreamException
     */
    private void writeOperations(List<Operation> operations, XMLStreamWriter writer) throws XMLStreamException {
        Iterator iterator = operations.iterator();
        while (iterator.hasNext()) {
            Operation operation = (Operation) iterator.next();
            boolean action = operation.getType() != null && operation.getType() == Type.ACTION;
            writer.writeStartElement("m", action ? "action" : "function", NAMESPACE_URI + "metadata");
            writer.writeAttribute("metadata", operation.getMetadataAnchor());
            writer.writeAttribute("title", operation.getTitle());
            writer.writeAttribute("target", operation.getTarget().toASCIIString());
            writer.writeEndElement();
        }
    }

    /**
     * This method writes information about the author.
     *
     * @param title
     * @param writer
     * @throws XMLStreamException
     */
    private void writerAuthorInfo(String title, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("http://www.w3.org/2005/Atom", "title");
        if (title != null) {
            writer.writeCharacters(title);
        }
        writer.writeEndElement();
        writer.writeStartElement("http://www.w3.org/2005/Atom", "summary");
        writer.writeEndElement();
        writer.writeStartElement("http://www.w3.org/2005/Atom", "updated");
        writer.writeCharacters(
                (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")).format(new Date(System.currentTimeMillis())));
        writer.writeEndElement();
        writer.writeStartElement("http://www.w3.org/2005/Atom", "author");
        writer.writeStartElement("http://www.w3.org/2005/Atom", "name");
        writer.writeEndElement();
        writer.writeEndElement();
    }

    /**
     * This method returns the entity type.
     * Entity type can be a derived type or the base type.
     *
     * @param metadata
     * @param baseType
     * @param derivedTypeName
     * @throws SerializerException
     */
    protected EdmEntityType resolveEntityType(ServiceMetadata metadata, EdmEntityType baseType, String derivedTypeName)
            throws SerializerException {
        if (derivedTypeName != null && !baseType.getFullQualifiedName().getFullQualifiedNameAsString().equals(
                derivedTypeName)) {
            EdmEntityType derivedType = metadata.getEdm().getEntityType(new FullQualifiedName(derivedTypeName));
            if (derivedType == null) {
                throw new SerializerException("EntityType not found", MessageKeys.UNKNOWN_TYPE,
                                              new String[] { derivedTypeName });
            } else {
                for (EdmEntityType type = derivedType.getBaseType(); type != null; type = type.getBaseType()) {
                    if (type.getFullQualifiedName().getFullQualifiedNameAsString().equals(
                            baseType.getFullQualifiedName().getFullQualifiedNameAsString())) {
                        return derivedType;
                    }
                }
                throw new SerializerException("Wrong base type", MessageKeys.WRONG_BASE_TYPE,
                                              new String[] { derivedTypeName,
                                                      baseType.getFullQualifiedName().getFullQualifiedNameAsString() });
            }
        } else {
            return baseType;
        }
    }

    /**
     * This method returns the complex type.
     * Complex type can be a derived type or the base type.
     *
     * @param metadata
     * @param baseType
     * @param derivedTypeName
     * @throws SerializerException
     */
    protected EdmComplexType resolveComplexType(ServiceMetadata metadata, EdmComplexType baseType,
                                                String derivedTypeName) throws SerializerException {
        if (derivedTypeName != null && !baseType.getFullQualifiedName().getFullQualifiedNameAsString().equals(
                derivedTypeName)) {
            EdmComplexType derivedType = metadata.getEdm().getComplexType(new FullQualifiedName(derivedTypeName));
            if (derivedType == null) {
                throw new SerializerException("Complex Type not found", MessageKeys.UNKNOWN_TYPE,
                                              new String[] { derivedTypeName });
            } else {
                for (EdmComplexType type = derivedType.getBaseType(); type != null; type = type.getBaseType()) {
                    if (type.getFullQualifiedName().getFullQualifiedNameAsString().equals(
                            baseType.getFullQualifiedName().getFullQualifiedNameAsString())) {
                        return derivedType;
                    }
                }
                throw new SerializerException("Wrong base type", MessageKeys.WRONG_BASE_TYPE,
                                              new String[] { derivedTypeName,
                                                      baseType.getFullQualifiedName().getFullQualifiedNameAsString() });
            }
        } else {
            return baseType;
        }
    }

    /**
     * This method writes entity properties.
     *
     * @param metadata
     * @param type
     * @param properties
     * @param select
     * @param xml10InvalidCharReplacement
     * @param writer
     * @throws XMLStreamException
     * @throws SerializerException
     */
    protected void writeProperties(ServiceMetadata metadata, EdmStructuredType type, List<Property> properties,
                                   SelectOption select, String xml10InvalidCharReplacement, XMLStreamWriter writer)
            throws XMLStreamException, SerializerException {
        boolean all = ExpandSelectHelper.isAll(select);
        Set<String> selected = all ? new HashSet() : ExpandSelectHelper.getSelectedPropertyNames(
                select.getSelectItems());
        Iterator iterator = type.getPropertyNames().iterator();
        while (true) {
            String propertyName;
            do {
                if (!iterator.hasNext()) {
                    return;
                }
                propertyName = (String) iterator.next();
            } while (!all && !((Set) selected).contains(propertyName));

            EdmProperty edmProperty = type.getStructuralProperty(propertyName);
            Property property = this.findProperty(propertyName, properties);
            Set<List<String>> selectedPaths = !all && !edmProperty.isPrimitive() ? ExpandSelectHelper.getSelectedPaths(
                    select.getSelectItems(), propertyName) : null;
            this.writeProperty(metadata, edmProperty, property, selectedPaths, xml10InvalidCharReplacement, writer);
        }
    }

    /**
     * This method writes entity navigation properties.
     *
     * @param metadata
     * @param type
     * @param linked
     * @param expand
     * @param toDepth
     * @param xml10InvalidCharReplacement
     * @param ancestors
     * @param name
     * @param writer
     * @throws SerializerException
     * @throws XMLStreamException
     */
    protected void writeNavigationProperties(ServiceMetadata metadata, EdmStructuredType type, Linked linked,
                                             ExpandOption expand, Integer toDepth, String xml10InvalidCharReplacement,
                                             Set<String> ancestors, String name, XMLStreamWriter writer)
            throws SerializerException, XMLStreamException {
        Iterator iterator;
        if ((toDepth == null || toDepth <= 1) && (toDepth != null || !ExpandSelectHelper.hasExpand(expand))) {
            iterator = type.getNavigationPropertyNames().iterator();
            while (iterator.hasNext()) {
                String propertyName = (String) iterator.next();
                this.writeLink(writer, this.getOrCreateLink(linked, propertyName));
            }
        } else {
            ExpandItem expandAll = ExpandSelectHelper.getExpandAll(expand);
            Iterator stringIterator = type.getNavigationPropertyNames().iterator();
            label90:
            while (true) {
                while (true) {
                    if (!stringIterator.hasNext()) {
                        break label90;
                    }
                    String propertyName = (String) stringIterator.next();
                    ExpandItem innerOptions = ExpandSelectHelper.getExpandItem(expand.getExpandItems(), propertyName);
                    if (expandAll == null && innerOptions == null && toDepth == null) {
                        this.writeLink(writer, this.getOrCreateLink(linked, propertyName));
                    } else {
                        Integer levels = null;
                        EdmNavigationProperty property = type.getNavigationProperty(propertyName);
                        Link navigationLink = this.getOrCreateLink(linked, propertyName);
                        ExpandOption childExpand = null;
                        LevelsExpandOption levelsOption = null;
                        if (innerOptions != null) {
                            levelsOption = innerOptions.getLevelsOption();
                            childExpand = levelsOption == null ?
                                    innerOptions.getExpandOption() :
                                    (new ExpandOptionImpl()).addExpandItem(innerOptions);
                        } else if (expandAll != null) {
                            levels = 1;
                            levelsOption = expandAll.getLevelsOption();
                            childExpand = (new ExpandOptionImpl()).addExpandItem(expandAll);
                        }
                        if (levelsOption != null) {
                            levels = levelsOption.isMax() ? Integer.MAX_VALUE : levelsOption.getValue();
                        }
                        if (toDepth != null) {
                            levels = toDepth - 1;
                            childExpand = expand;
                        }
                        this.writeLink(writer, navigationLink, false);
                        writer.writeStartElement("m", "inline", NAMESPACE_URI + "metadata");
                        this.writeExpandedNavigationProperty(metadata, property, navigationLink,
                                                             (ExpandOption) childExpand, levels, innerOptions == null ?
                                                                     null :
                                                                     innerOptions.getSelectOption(),
                                                             innerOptions == null ?
                                                                     null :
                                                                     innerOptions.getCountOption(),
                                                             innerOptions == null ? false : innerOptions.hasCountPath(),
                                                             innerOptions == null ? false : innerOptions.isRef(),
                                                             xml10InvalidCharReplacement, ancestors, name, writer);
                        writer.writeEndElement();
                        writer.writeEndElement();
                    }
                }
            }
        }
        iterator = linked.getAssociationLinks().iterator();
        while (iterator.hasNext()) {
            Link link = (Link) iterator.next();
            this.writeLink(writer, link);
        }
    }

    /**
     * This method generates links of an entity.
     *
     * @param linked
     * @param navigationPropertyName
     * @throws XMLStreamException
     */
    protected Link getOrCreateLink(Linked linked, String navigationPropertyName) throws XMLStreamException {
        Link link = linked.getNavigationLink(navigationPropertyName);
        if (link == null) {
            link = new Link();
            link.setRel(NAMESPACE_URI + "related/" + navigationPropertyName);
            link.setType(Constants.ENTITY_SET_NAVIGATION_LINK_TYPE);
            link.setTitle(navigationPropertyName);
            EntityCollection target = new EntityCollection();
            link.setInlineEntitySet(target);
            if (linked.getId() != null) {
                link.setHref(linked.getId().toASCIIString() + "/" + navigationPropertyName);
            }
        }
        return link;
    }

    /**
     * This method writes links.
     *
     * @param writer
     * @param link
     * @throws XMLStreamException
     */
    private void writeLink(XMLStreamWriter writer, Link link) throws XMLStreamException {
        this.writeLink(writer, link, true);
    }

    private void writeLink(XMLStreamWriter writer, Link link, boolean close) throws XMLStreamException {
        writer.writeStartElement("a", "link", "http://www.w3.org/2005/Atom");
        writer.writeAttribute("rel", link.getRel());
        if (link.getType() != null) {
            writer.writeAttribute("type", link.getType());
        }
        if (link.getTitle() != null) {
            writer.writeAttribute("title", link.getTitle());
        }
        if (link.getHref() != null) {
            writer.writeAttribute("href", link.getHref());
        }
        if (close) {
            writer.writeEndElement();
        }
    }

    /**
     * This method writes expanded navigation properties.
     *
     * @param metadata
     * @param property
     * @param navigationLink
     * @param innerExpand
     * @param toDepth
     * @param innerSelect
     * @param coutOption
     * @param writeNavigationCount
     * @param writeOnlyRef
     * @param xml10InvalidCharReplacement
     * @param ancestors
     * @param name
     * @param writer
     * @throws XMLStreamException
     * @throws SerializerException
     */
    protected void writeExpandedNavigationProperty(ServiceMetadata metadata, EdmNavigationProperty property,
                                                   Link navigationLink, ExpandOption innerExpand, Integer toDepth,
                                                   SelectOption innerSelect, CountOption coutOption,
                                                   boolean writeNavigationCount, boolean writeOnlyRef,
                                                   String xml10InvalidCharReplacement, Set<String> ancestors,
                                                   String name, XMLStreamWriter writer)
            throws XMLStreamException, SerializerException {
        if (property.isCollection()) {
            if (navigationLink != null && navigationLink.getInlineEntitySet() != null) {
                writer.writeStartElement("a", "feed", "http://www.w3.org/2005/Atom");
                if (writeNavigationCount) {
                    this.writeCount(navigationLink.getInlineEntitySet(), writer);
                } else {
                    if (coutOption != null && coutOption.getValue()) {
                        this.writeCount(navigationLink.getInlineEntitySet(), writer);
                    }
                    this.writeEntitySet(metadata, property.getType(), navigationLink.getInlineEntitySet(), innerExpand,
                                        toDepth, innerSelect, xml10InvalidCharReplacement, writer, writeOnlyRef, name,
                                        ancestors);
                }
                writer.writeEndElement();
            }
        } else if (navigationLink != null && navigationLink.getInlineEntity() != null) {
            this.writeEntity(metadata, property.getType(), navigationLink.getInlineEntity(), (ContextURL) null,
                             innerExpand, toDepth, innerSelect, xml10InvalidCharReplacement, writer, false,
                             writeOnlyRef, name, ancestors);
        }
    }

    /**
     * This method writes a single property.
     *
     * @param metadata
     * @param edmProperty
     * @param property
     * @param selectedPaths
     * @param xml10InvalidCharReplacement
     * @param writer
     * @throws XMLStreamException
     * @throws SerializerException
     */
    protected void writeProperty(ServiceMetadata metadata, EdmProperty edmProperty, Property property,
                                 Set<List<String>> selectedPaths, String xml10InvalidCharReplacement,
                                 XMLStreamWriter writer) throws XMLStreamException, SerializerException {
        writer.writeStartElement("d", edmProperty.getName(), NAMESPACE_URI + "data");
        if (property != null && !property.isNull()) {
            this.writePropertyValue(metadata, edmProperty, property, selectedPaths, xml10InvalidCharReplacement,
                                    writer);
        } else {
            if (!edmProperty.isNullable()) {
                throw new SerializerException("Non-nullable property not present!", MessageKeys.MISSING_PROPERTY,
                                              new String[] { edmProperty.getName() });
            }
            writer.writeAttribute("m", NAMESPACE_URI + "metadata", "null", "true");
        }
        writer.writeEndElement();
    }

    /**
     * This method writes the collection type.
     *
     * @param type
     */
    private String collectionType(EdmType type) {
        return "#Collection(" + type.getFullQualifiedName().getFullQualifiedNameAsString() + ")";
    }

    /**
     * This method writes the complex property type.
     *
     * @param metadata
     * @param baseType
     * @param definedType
     * @throws SerializerException
     */
    private String complexType(ServiceMetadata metadata, EdmComplexType baseType, String definedType)
            throws SerializerException {
        EdmComplexType type = this.resolveComplexType(metadata, baseType, definedType);
        return type.getFullQualifiedName().getFullQualifiedNameAsString();
    }

    /**
     * This method writes a derived complex property type.
     *
     * @param baseType
     * @param definedType
     * @throws SerializerException
     */
    private String derivedComplexType(EdmComplexType baseType, String definedType) throws SerializerException {
        String base = baseType.getFullQualifiedName().getFullQualifiedNameAsString();
        return base.equals(definedType) ? null : definedType;
    }

    /**
     * This method writes a property value.
     *
     * @param metadata
     * @param edmProperty
     * @param property
     * @param selectedPaths
     * @param xml10InvalidCharReplacement
     * @param writer
     * @throws XMLStreamException
     * @throws SerializerException
     */
    private void writePropertyValue(ServiceMetadata metadata, EdmProperty edmProperty, Property property,
                                    Set<List<String>> selectedPaths, String xml10InvalidCharReplacement,
                                    XMLStreamWriter writer) throws XMLStreamException, SerializerException {
        try {
            if (!edmProperty.isPrimitive() && edmProperty.getType().getKind() != EdmTypeKind.ENUM
                    && edmProperty.getType().getKind() != EdmTypeKind.DEFINITION) {
                if (!property.isComplex()) {
                    throw new SerializerException("Property type not yet supported!",
                                                  MessageKeys.UNSUPPORTED_PROPERTY_TYPE,
                                                  new String[] { edmProperty.getName() });
                }
                if (edmProperty.isCollection()) {
                    writer.writeAttribute("m", NAMESPACE_URI + "metadata", "type",
                                          this.collectionType(edmProperty.getType()));
                    this.writeComplexCollection(metadata, (EdmComplexType) edmProperty.getType(), property,
                                                selectedPaths, xml10InvalidCharReplacement, writer);
                } else {
                    this.writeComplex(metadata, edmProperty, property, selectedPaths, xml10InvalidCharReplacement,
                                      writer);
                }
            } else if (edmProperty.isCollection()) {
                writer.writeAttribute("m", NAMESPACE_URI + "metadata", "type", edmProperty.isPrimitive() ?
                        "#Collection(" + edmProperty.getType().getName() + ")" :
                        this.collectionType(edmProperty.getType()));
                this.writePrimitiveCollection((EdmPrimitiveType) edmProperty.getType(), property,
                                              edmProperty.isNullable(), edmProperty.getMaxLength(),
                                              edmProperty.getPrecision(), edmProperty.getScale(),
                                              edmProperty.isUnicode(), xml10InvalidCharReplacement, writer);
            } else {
                this.writePrimitive((EdmPrimitiveType) edmProperty.getType(), property, edmProperty.isNullable(),
                                    edmProperty.getMaxLength(), edmProperty.getPrecision(), edmProperty.getScale(),
                                    edmProperty.isUnicode(), xml10InvalidCharReplacement, writer);
            }
        } catch (EdmPrimitiveTypeException e) {
            throw new SerializerException("Wrong value for property!", e, MessageKeys.WRONG_PROPERTY_VALUE,
                                          new String[] { edmProperty.getName(), property.getValue().toString() });
        }
    }

    /**
     * This method writes a complex property.
     *
     * @param metadata
     * @param edmProperty
     * @param property
     * @param selectedPaths
     * @param xml10InvalidCharReplacement
     * @param writer
     * @throws XMLStreamException
     * @throws SerializerException
     */
    private void writeComplex(ServiceMetadata metadata, EdmProperty edmProperty, Property property,
                              Set<List<String>> selectedPaths, String xml10InvalidCharReplacement,
                              XMLStreamWriter writer) throws XMLStreamException, SerializerException {
        writer.writeAttribute("m", NAMESPACE_URI + "metadata", "type",
                              "#" + this.complexType(metadata, (EdmComplexType) edmProperty.getType(),
                                                     property.getType()));
        String derivedName = property.getType();
        EdmComplexType resolvedType = this.resolveComplexType(metadata, (EdmComplexType) edmProperty.getType(),
                                                              derivedName);
        this.writeComplexValue(metadata, resolvedType, property.asComplex().getValue(), selectedPaths,
                               xml10InvalidCharReplacement, writer);
    }

    /**
     * This method writes a collection of primitive properties.
     *
     * @param type
     * @param property
     * @param isNullable
     * @param maxLength
     * @param precision
     * @param scale
     * @param isUnicode
     * @param xml10InvalidCharReplacement
     * @param writer
     * @throws XMLStreamException
     * @throws EdmPrimitiveTypeException
     * @throws SerializerException
     */
    private void writePrimitiveCollection(EdmPrimitiveType type, Property property, Boolean isNullable,
                                          Integer maxLength, Integer precision, Integer scale, Boolean isUnicode,
                                          String xml10InvalidCharReplacement, XMLStreamWriter writer)
            throws XMLStreamException, EdmPrimitiveTypeException, SerializerException {
        Iterator iterator = property.asCollection().iterator();
        while (iterator.hasNext()) {
            Object value = iterator.next();
            writer.writeStartElement("m", "element", NAMESPACE_URI + "metadata");
            switch (property.getValueType()) {
                case COLLECTION_PRIMITIVE:
                case COLLECTION_ENUM:
                    this.writePrimitiveValue(type, value, isNullable, maxLength, precision, scale, isUnicode,
                                             xml10InvalidCharReplacement, writer);
                    writer.writeEndElement();
                    break;
                case COLLECTION_GEOSPATIAL:
                    throw new SerializerException("Property type not yet supported!", MessageKeys.UNSUPPORTED_PROPERTY_TYPE,
                                                  new String[] { property.getName() });
                default:
                    throw new SerializerException("Property type not yet supported!", MessageKeys.UNSUPPORTED_PROPERTY_TYPE,
                                                  new String[] { property.getName() });
            }
        }
    }

    /**
     * This method writes a collection of complex properties.
     *
     * @param metadata
     * @param type
     * @param property
     * @param selectedPaths
     * @param xml10InvalidCharReplacement
     * @param writer
     * @throws XMLStreamException
     * @throws SerializerException
     */
    private void writeComplexCollection(ServiceMetadata metadata, EdmComplexType type, Property property,
                                        Set<List<String>> selectedPaths, String xml10InvalidCharReplacement,
                                        XMLStreamWriter writer) throws XMLStreamException, SerializerException {
        Iterator iterator = property.asCollection().iterator();
        while (iterator.hasNext()) {
            Object value = iterator.next();
            writer.writeStartElement("m", "element", NAMESPACE_URI + "metadata");
            String typeName = ((ComplexValue) value).getTypeName();
            String propertyType = typeName != null ? typeName : property.getType();
            if (this.derivedComplexType(type, propertyType) != null) {
                writer.writeAttribute("m", NAMESPACE_URI + "metadata", "type", propertyType);
            }
            EdmComplexType complexType;
            if (typeName != null && !propertyType.equals(type.getFullQualifiedName().getFullQualifiedNameAsString())) {
                complexType = metadata.getEdm().getComplexType(new FullQualifiedName(propertyType));
            } else {
                complexType = type;
            }
            switch (property.getValueType()) {
                case COLLECTION_COMPLEX:
                    this.writeComplexValue(metadata, complexType, ((ComplexValue) value).getValue(), selectedPaths,
                                           xml10InvalidCharReplacement, writer);
                    writer.writeEndElement();
                    break;
                default:
                    throw new SerializerException("Property type not yet supported!", MessageKeys.UNSUPPORTED_PROPERTY_TYPE,
                                                  new String[] { property.getName() });
            }
        }
    }

    /**
     * This method writes a primitive property.
     *
     * @param type
     * @param property
     * @param isNullable
     * @param maxLength
     * @param precision
     * @param scale
     * @param isUnicode
     * @param xml10InvalidCharReplacement
     * @param writer
     * @throws EdmPrimitiveTypeException
     * @throws XMLStreamException
     * @throws SerializerException
     */
    private void writePrimitive(EdmPrimitiveType type, Property property, Boolean isNullable, Integer maxLength,
                                Integer precision, Integer scale, Boolean isUnicode, String xml10InvalidCharReplacement,
                                XMLStreamWriter writer)
            throws EdmPrimitiveTypeException, XMLStreamException, SerializerException {
        if (property.isPrimitive()) {
            if (type != EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.String)) {
                writer.writeAttribute("m", NAMESPACE_URI + "metadata", "type",
                                      type.getKind() == EdmTypeKind.DEFINITION ?
                                              "#" + type.getFullQualifiedName().getFullQualifiedNameAsString() :
                                              type.getName());
            }
            this.writePrimitiveValue(type, property.asPrimitive(), isNullable, maxLength, precision, scale, isUnicode,
                                     xml10InvalidCharReplacement, writer);
        } else {
            if (property.isGeospatial()) {
                throw new SerializerException("Property type not yet supported!", MessageKeys.UNSUPPORTED_PROPERTY_TYPE,
                                              new String[] { property.getName() });
            }
            if (!property.isEnum()) {
                throw new SerializerException("Inconsistent property type!", MessageKeys.INCONSISTENT_PROPERTY_TYPE,
                                              new String[] { property.getName() });
            }
            writer.writeAttribute("m", NAMESPACE_URI + "metadata", "type",
                                  "#" + type.getFullQualifiedName().getFullQualifiedNameAsString());
            this.writePrimitiveValue(type, property.asEnum(), isNullable, maxLength, precision, scale, isUnicode,
                                     xml10InvalidCharReplacement, writer);
        }
    }

    /**
     * This method writes a primitive property value.
     *
     * @param type
     * @param primitiveValue
     * @param isNullable
     * @param maxLength
     * @param precision
     * @param scale
     * @param isUnicode
     * @param xml10InvalidCharReplacement
     * @param writer
     * @throws EdmPrimitiveTypeException
     * @throws XMLStreamException
     */
    protected void writePrimitiveValue(EdmPrimitiveType type, Object primitiveValue, Boolean isNullable,
                                       Integer maxLength, Integer precision, Integer scale, Boolean isUnicode,
                                       String xml10InvalidCharReplacement, XMLStreamWriter writer)
            throws EdmPrimitiveTypeException, XMLStreamException {
        String value = type.valueToString(primitiveValue, isNullable, maxLength, precision, scale, isUnicode);
        if (value == null) {
            writer.writeAttribute("m", NAMESPACE_URI + "metadata", "null", "true");
        } else {
            writer.writeCharacters(replaceInvalidCharacters(type, value, isUnicode, xml10InvalidCharReplacement));
        }
    }

    /**
     * This method writes a complex property value.
     *
     * @param metadata
     * @param type
     * @param properties
     * @param selectedPaths
     * @param xml10InvalidCharReplacement
     * @param writer
     * @throws XMLStreamException
     * @throws SerializerException
     */
    protected void writeComplexValue(ServiceMetadata metadata, EdmComplexType type, List<Property> properties,
                                     Set<List<String>> selectedPaths, String xml10InvalidCharReplacement,
                                     XMLStreamWriter writer) throws XMLStreamException, SerializerException {
        Iterator iterator = type.getPropertyNames().iterator();
        while (true) {
            String propertyName;
            Property property;
            do {
                if (!iterator.hasNext()) {
                    return;
                }
                propertyName = (String) iterator.next();
                property = this.findProperty(propertyName, properties);
            } while (selectedPaths != null && !ExpandSelectHelper.isSelected(selectedPaths, propertyName));
            this.writeProperty(metadata, (EdmProperty) type.getProperty(propertyName), property, selectedPaths == null ?
                                       null :
                                       ExpandSelectHelper.getReducedSelectedPaths(selectedPaths, propertyName),
                               xml10InvalidCharReplacement, writer);
        }
    }

    /**
     * This method returns properties of an entity.
     *
     * @param propertyName
     * @param properties
     */
    private Property findProperty(String propertyName, List<Property> properties) {
        Iterator iterator = properties.iterator();
        Property property;
        do {
            if (!iterator.hasNext()) {
                return null;
            }
            property = (Property) iterator.next();
        } while (!propertyName.equals(property.getName()));
        return property;
    }

    /**
     * This method writes a reference entity.
     *
     * @param entity
     * @param contextURL
     * @param writer
     * @param top
     * @throws XMLStreamException
     */
    private void writeReference(Entity entity, ContextURL contextURL, XMLStreamWriter writer, boolean top)
            throws XMLStreamException {
        writer.writeStartElement("m", "ref", NAMESPACE_URI + "metadata");
        if (top) {
            writer.writeNamespace("m", NAMESPACE_URI + "metadata");
            if (contextURL != null) {
                writer.writeAttribute("m", NAMESPACE_URI + "metadata", "context",
                                      ContextURLBuilder.create(contextURL).toASCIIString());
            }
        }
        writer.writeAttribute("id", entity.getId().toASCIIString());
        writer.writeEndElement();
    }

    /**
     * This method writes the entity count if count option is enabled.
     *
     * @param entitySet
     * @param writer
     * @throws XMLStreamException
     */
    private void writeCount(AbstractEntityCollection entitySet, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("m", "count", NAMESPACE_URI + "metadata");
        writer.writeCharacters(String.valueOf(entitySet.getCount() == null ? 0 : entitySet.getCount()));
        writer.writeEndElement();
    }

    private void writeCount(AbstractEntityCollection entitySet, XMLStreamWriter writer, boolean excludePagingForOdataCount)
            throws XMLStreamException {
        writer.writeStartElement("m", "count", NAMESPACE_URI + "metadata");
        Integer odataCount;
        if (excludePagingForOdataCount){
            StreamingEntityIterator entityIterator = (StreamingEntityIterator) entitySet;
            boolean hasFilters = entityIterator.getQueryOptions().getFilterOption() != null;
            // If the query not has filters, we can take whole rowCount as count.
            odataCount = hasFilters ? entityIterator.getOdataCount() : entityIterator.rowsCount;
        } else {
            odataCount = entitySet.getCount();
        }
        writer.writeCharacters(String.valueOf(odataCount == null ? 0 : odataCount));
        writer.writeEndElement();
    }

    /**
     * This method writes the link to the next page if pagination is enabled.
     *
     * @param entitySet
     * @param writer
     * @throws XMLStreamException
     */
    private void writeNextLink(AbstractEntityCollection entitySet, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("a", "link", "http://www.w3.org/2005/Atom");
        writer.writeAttribute("rel", "next");
        writer.writeAttribute("href", entitySet.getNext().toASCIIString());
        writer.writeEndElement();
    }
}
