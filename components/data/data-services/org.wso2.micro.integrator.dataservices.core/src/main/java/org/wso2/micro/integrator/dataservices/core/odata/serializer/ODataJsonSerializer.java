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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.olingo.commons.api.data.AbstractEntityCollection;
import org.apache.olingo.commons.api.data.ComplexValue;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityIterator;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.data.Linked;
import org.apache.olingo.commons.api.data.Operation;
import org.apache.olingo.commons.api.data.Property;
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
import org.apache.olingo.commons.api.edm.geo.ComposedGeospatial;
import org.apache.olingo.commons.api.edm.geo.Geospatial;
import org.apache.olingo.commons.api.edm.geo.GeospatialCollection;
import org.apache.olingo.commons.api.edm.geo.LineString;
import org.apache.olingo.commons.api.edm.geo.MultiLineString;
import org.apache.olingo.commons.api.edm.geo.MultiPoint;
import org.apache.olingo.commons.api.edm.geo.MultiPolygon;
import org.apache.olingo.commons.api.edm.geo.Point;
import org.apache.olingo.commons.api.edm.geo.Polygon;
import org.apache.olingo.commons.api.edm.geo.Geospatial.Type;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.core.edm.primitivetype.EdmPrimitiveTypeFactory;
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
import org.apache.olingo.server.core.serializer.utils.ContentTypeHelper;
import org.apache.olingo.server.core.serializer.utils.ContextURLBuilder;
import org.apache.olingo.server.core.serializer.utils.ExpandSelectHelper;
import org.apache.olingo.server.core.uri.UriHelperImpl;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.wso2.micro.integrator.dataservices.core.odata.StreamingEntityIterator;

import static org.wso2.micro.integrator.dataservices.core.odata.ODataConstants.ODATA_COUNT_WITHOUT_PAGING;

/**
 * This class is used to create an OData serializer with JSON content.
 */
public class ODataJsonSerializer implements ODataSerializer {
    private static final Map<Geospatial.Type, String> geoValueTypeToJsonName;
    private static final boolean excludePagingForOdataCount;

    static {
        Map<Geospatial.Type, String> temp = new EnumMap(Geospatial.Type.class);
        temp.put(Type.POINT, "Point");
        temp.put(Type.MULTIPOINT, "MultiPoint");
        temp.put(Type.LINESTRING, "LineString");
        temp.put(Type.MULTILINESTRING, "MultiLineString");
        temp.put(Type.POLYGON, "Polygon");
        temp.put(Type.MULTIPOLYGON, "MultiPolygon");
        temp.put(Type.GEOSPATIALCOLLECTION, "GeometryCollection");
        geoValueTypeToJsonName = Collections.unmodifiableMap(temp);
    }

    /**
     * Initializing the flag to determine if OData count should not be affected with
     * $top, $skip, or $expand query options.
     */
    static {
        String propertyValue = System.getProperty(ODATA_COUNT_WITHOUT_PAGING, "false");
        excludePagingForOdataCount = Boolean.parseBoolean(propertyValue);
    }

    private final boolean isIEEE754Compatible;
    private final boolean isODataMetadataNone;
    private final boolean isODataMetadataFull;

    public ODataJsonSerializer(ContentType contentType) {
        this.isIEEE754Compatible = ContentTypeHelper.isODataIEEE754Compatible(contentType);
        this.isODataMetadataNone = ContentTypeHelper.isODataMetadataNone(contentType);
        this.isODataMetadataFull = ContentTypeHelper.isODataMetadataFull(contentType);
    }

    public SerializerStreamResult entityCollectionStreamed(ServiceMetadata metadata, EdmEntityType entityType,
                                                           EntityIterator entities,
                                                           EntityCollectionSerializerOptions options)
            throws SerializerException {
        return ODataWritableContent.with(entities, entityType, this, metadata, options).build();
    }

    /**
     * This method generates the OData JSON response.
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
        boolean pagination = false;
        try {
            JsonGenerator json = (new JsonFactory()).createGenerator(outputStream);
            json.writeStartObject();
            ContextURL contextURL = this.checkContextURL(options == null ? null : options.getContextURL());
            this.writeContextURL(contextURL, json);
            this.writeMetadataETag(metadata, json);
            json.writeFieldName("value");
            String name = contextURL == null ? null : contextURL.getEntitySetOrSingletonOrType();
            if (options == null) {
                this.writeEntitySet(metadata, entityType, entitySet, (ExpandOption) null, (Integer) null,
                                    (SelectOption) null, false, (Set) null, name, json);
            } else {
                this.writeEntitySet(metadata, entityType, entitySet, options.getExpand(), (Integer) null,
                                    options.getSelect(), options.getWriteOnlyReferences(), (Set) null, name, json);
            }
            if (options != null && options.getCount() != null && options.getCount().getValue()) {
                if (excludePagingForOdataCount) {
                    StreamingEntityIterator entityIterator = (StreamingEntityIterator) entitySet;
                    boolean hasFilters = entityIterator.getQueryOptions().getFilterOption() != null;
                    // If the query has no filters, we can take whole rowCount as count.
                    this.writeInlineCount("",
                            hasFilters ? entityIterator.getOdataCount() : entityIterator.rowsCount, json);
                } else {
                    this.writeInlineCount("", entitySet.getCount(), json);
                }
            }
            this.writeNextLink(entitySet, json, pagination);
            json.close();
        } catch (IOException e) {
            SerializerException cachedException = new SerializerException("An I/O exception occurred.", e,
                                                                          MessageKeys.IO_EXCEPTION, new String[0]);
            throw cachedException;
        }
    }

    /**
     * This method returns the context URL.
     *
     * @param contextURL
     * @throws SerializerException
     */
    ContextURL checkContextURL(ContextURL contextURL) throws SerializerException {
        if (this.isODataMetadataNone) {
            return null;
        } else if (contextURL == null) {
            throw new SerializerException("ContextURL null!", MessageKeys.NO_CONTEXT_URL, new String[0]);
        } else {
            return contextURL;
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
     * @param onlyReference
     * @param ancestors
     * @param name
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    protected void writeEntitySet(ServiceMetadata metadata, EdmEntityType entityType,
                                  AbstractEntityCollection entitySet, ExpandOption expand, Integer toDepth,
                                  SelectOption select, boolean onlyReference, Set<String> ancestors, String name,
                                  JsonGenerator json) throws IOException, SerializerException {
        json.writeStartArray();
        Iterator iterator = entitySet.iterator();
        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();
            if (onlyReference) {
                json.writeStartObject();
                json.writeStringField("@odata.id", this.getEntityId(entity, entityType, name));
                json.writeEndObject();
            } else {
                this.writeEntity(metadata, entityType, entity, (ContextURL) null, expand, toDepth, select, false,
                                 ancestors, name, json);
            }
        }
        json.writeEndArray();
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
     * This method checks if the property is selected in the select query option.
     * Select option have to be enabled.
     *
     * @param select
     * @param type
     */
    private boolean areKeyPredicateNamesSelected(SelectOption select, EdmEntityType type) {
        if (select != null && !ExpandSelectHelper.isAll(select)) {
            Set<String> selected = ExpandSelectHelper.getSelectedPropertyNames(select.getSelectItems());
            Iterator iterator = type.getKeyPredicateNames().iterator();
            String key;
            do {
                if (!iterator.hasNext()) {
                    return true;
                }

                key = (String) iterator.next();
            } while (selected.contains(key));
            return false;
        } else {
            return true;
        }
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
     * @param onlyReference
     * @param ancestors
     * @param name
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    protected void writeEntity(ServiceMetadata metadata, EdmEntityType entityType, Entity entity, ContextURL contextURL,
                               ExpandOption expand, Integer toDepth, SelectOption select, boolean onlyReference,
                               Set<String> ancestors, String name, JsonGenerator json)
            throws IOException, SerializerException {
        boolean cycle = false;
        if (expand != null) {
            if (ancestors == null) {
                ancestors = new HashSet();
            }
            cycle = !((Set) ancestors).add(this.getEntityId(entity, entityType, name));
        }
        try {
            json.writeStartObject();
            if (!this.isODataMetadataNone) {
                if (contextURL != null) {
                    this.writeContextURL(contextURL, json);
                    this.writeMetadataETag(metadata, json);
                }
                if (entity.getETag() != null) {
                    json.writeStringField("@odata.etag", entity.getETag());
                }
                if (entityType.hasStream()) {
                    if (entity.getMediaETag() != null) {
                        json.writeStringField("@odata.mediaEtag", entity.getMediaETag());
                    }
                    if (entity.getMediaContentType() != null) {
                        json.writeStringField("@odata.mediaContentType", entity.getMediaContentType());
                    }
                    if (entity.getMediaContentSource() != null) {
                        json.writeStringField("@odata.mediaReadLink", entity.getMediaContentSource().toString());
                    }
                    if (entity.getMediaEditLinks() != null && !entity.getMediaEditLinks().isEmpty()) {
                        json.writeStringField("@odata.mediaEditLink", ((Link) entity.getMediaEditLinks().get(
                                0)).getHref());
                    }
                }
            }

            if (!cycle && !onlyReference) {
                EdmEntityType resolvedType = this.resolveEntityType(metadata, entityType, entity.getType());
                if (!this.isODataMetadataNone && !resolvedType.equals(entityType) || this.isODataMetadataFull) {
                    json.writeStringField("@odata.type", "#" + entity.getType());
                }
                if (!this.isODataMetadataNone && !this.areKeyPredicateNamesSelected(select, resolvedType)
                        || this.isODataMetadataFull) {
                    json.writeStringField("@odata.id", this.getEntityId(entity, resolvedType, name));
                }
                if (this.isODataMetadataFull) {
                    if (entity.getSelfLink() != null) {
                        json.writeStringField("@odata.readLink", entity.getSelfLink().getHref());
                    }
                    if (entity.getEditLink() != null) {
                        json.writeStringField("@odata.editLink", entity.getEditLink().getHref());
                    }
                }
                this.writeProperties(metadata, resolvedType, entity.getProperties(), select, json);
                this.writeNavigationProperties(metadata, resolvedType, entity, expand, toDepth, (Set) ancestors, name,
                                               json);
                this.writeOperations(entity.getOperations(), json);
            } else {
                json.writeStringField("@odata.id", this.getEntityId(entity, entityType, name));
            }
            json.writeEndObject();
        } finally {
            if (expand != null && !cycle && ancestors != null) {
                ((Set) ancestors).remove(this.getEntityId(entity, entityType, name));
            }

        }
    }

    /**
     * This method writes entity operations.
     *
     * @param operations
     * @param json
     * @throws IOException
     */
    private void writeOperations(List<Operation> operations, JsonGenerator json) throws IOException {
        if (this.isODataMetadataFull) {
            Iterator iterator = operations.iterator();
            while (iterator.hasNext()) {
                Operation operation = (Operation) iterator.next();
                json.writeObjectFieldStart(operation.getMetadataAnchor());
                json.writeStringField("title", operation.getTitle());
                json.writeStringField("target", operation.getTarget().toASCIIString());
                json.writeEndObject();
            }
        }
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
                    if (type.getFullQualifiedName().equals(baseType.getFullQualifiedName())) {
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
        String fullQualifiedName = baseType.getFullQualifiedName().getFullQualifiedNameAsString();
        if (derivedTypeName != null && !fullQualifiedName.equals(derivedTypeName)) {
            EdmComplexType derivedType = metadata.getEdm().getComplexType(new FullQualifiedName(derivedTypeName));
            if (derivedType == null) {
                throw new SerializerException("Complex Type not found", MessageKeys.UNKNOWN_TYPE,
                                              new String[] { derivedTypeName });
            } else {
                for (EdmComplexType type = derivedType.getBaseType(); type != null; type = type.getBaseType()) {
                    if (type.getFullQualifiedName().equals(baseType.getFullQualifiedName())) {
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
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    protected void writeProperties(ServiceMetadata metadata, EdmStructuredType type, List<Property> properties,
                                   SelectOption select, JsonGenerator json) throws IOException, SerializerException {
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
            this.writeProperty(metadata, edmProperty, property, selectedPaths, json);
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
     * @param ancestors
     * @param name
     * @param json
     * @throws SerializerException
     * @throws IOException
     */
    protected void writeNavigationProperties(ServiceMetadata metadata, EdmStructuredType type, Linked linked,
                                             ExpandOption expand, Integer toDepth, Set<String> ancestors, String name,
                                             JsonGenerator json) throws SerializerException, IOException {
        if (this.isODataMetadataFull) {
            Iterator iterator = type.getNavigationPropertyNames().iterator();
            while (iterator.hasNext()) {
                String propertyName = (String) iterator.next();
                Link navigationLink = linked.getNavigationLink(propertyName);
                if (navigationLink != null) {
                    json.writeStringField(propertyName + "@odata.navigationLink", navigationLink.getHref());
                }
                Link associationLink = linked.getAssociationLink(propertyName);
                if (associationLink != null) {
                    json.writeStringField(propertyName + "@odata.associationLink", associationLink.getHref());
                }
            }
        }

        if (toDepth != null && toDepth > 1 || toDepth == null && ExpandSelectHelper.hasExpand(expand)) {
            ExpandItem expandAll = ExpandSelectHelper.getExpandAll(expand);
            Iterator iterator = type.getNavigationPropertyNames().iterator();
            while (true) {
                String propertyName;
                ExpandItem innerOptions;
                do {
                    if (!iterator.hasNext()) {
                        return;
                    }
                    propertyName = (String) iterator.next();
                    innerOptions = ExpandSelectHelper.getExpandItem(expand.getExpandItems(), propertyName);
                } while (innerOptions == null && expandAll == null && toDepth == null);
                Integer levels = null;
                EdmNavigationProperty property = type.getNavigationProperty(propertyName);
                Link navigationLink = linked.getNavigationLink(property.getName());
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
                this.writeExpandedNavigationProperty(metadata, property, navigationLink, (ExpandOption) childExpand,
                                                     levels,
                                                     innerOptions == null ? null : innerOptions.getSelectOption(),
                                                     innerOptions == null ? null : innerOptions.getCountOption(),
                                                     innerOptions == null ? false : innerOptions.hasCountPath(),
                                                     innerOptions == null ? false : innerOptions.isRef(), ancestors,
                                                     name, json);
            }
        }
    }

    /**
     * This method writes expanded properties.
     *
     * @param metadata
     * @param property
     * @param navigationLink
     * @param innerExpand
     * @param toDepth
     * @param innerSelect
     * @param innerCount
     * @param writeOnlyCount
     * @param writeOnlyRef
     * @param ancestors
     * @param name
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    protected void writeExpandedNavigationProperty(ServiceMetadata metadata, EdmNavigationProperty property,
                                                   Link navigationLink, ExpandOption innerExpand, Integer toDepth,
                                                   SelectOption innerSelect, CountOption innerCount,
                                                   boolean writeOnlyCount, boolean writeOnlyRef, Set<String> ancestors,
                                                   String name, JsonGenerator json)
            throws IOException, SerializerException {
        if (property.isCollection()) {
            if (writeOnlyCount) {
                if (navigationLink != null && navigationLink.getInlineEntitySet() != null) {
                    this.writeInlineCount(property.getName(), navigationLink.getInlineEntitySet().getCount(), json);
                } else {
                    this.writeInlineCount(property.getName(), 0, json);
                }
            } else if (navigationLink != null && navigationLink.getInlineEntitySet() != null) {
                if (innerCount != null && innerCount.getValue()) {
                    this.writeInlineCount(property.getName(), navigationLink.getInlineEntitySet().getCount(), json);
                }
                json.writeFieldName(property.getName());
                this.writeEntitySet(metadata, property.getType(), navigationLink.getInlineEntitySet(), innerExpand,
                                    toDepth, innerSelect, writeOnlyRef, ancestors, name, json);
            } else {
                if (innerCount != null && innerCount.getValue()) {
                    this.writeInlineCount(property.getName(), 0, json);
                }
                json.writeFieldName(property.getName());
                json.writeStartArray();
                json.writeEndArray();
            }
        } else {
            json.writeFieldName(property.getName());
            if (navigationLink != null && navigationLink.getInlineEntity() != null) {
                this.writeEntity(metadata, property.getType(), navigationLink.getInlineEntity(), (ContextURL) null,
                                 innerExpand, toDepth, innerSelect, writeOnlyRef, ancestors, name, json);
            } else {
                json.writeNull();
            }
        }
    }

    /**
     * This method checks if the given property is a stream property.
     *
     * @param edmProperty
     */
    private boolean isStreamProperty(EdmProperty edmProperty) {
        EdmType type = edmProperty.getType();
        return edmProperty.isPrimitive() && type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Stream);
    }

    /**
     * This method writes a single property.
     *
     * @param metadata
     * @param edmProperty
     * @param property
     * @param selectedPaths
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    protected void writeProperty(ServiceMetadata metadata, EdmProperty edmProperty, Property property,
                                 Set<List<String>> selectedPaths, JsonGenerator json)
            throws IOException, SerializerException {
        boolean isStreamProperty = this.isStreamProperty(edmProperty);
        this.writePropertyType(edmProperty, json);
        if (!isStreamProperty) {
            json.writeFieldName(edmProperty.getName());
        }
        if (property != null && !property.isNull()) {
            this.writePropertyValue(metadata, edmProperty, property, selectedPaths, json);
        } else {
            if (edmProperty.isNullable() == Boolean.FALSE) {
                throw new SerializerException("Non-nullable property not present!", MessageKeys.MISSING_PROPERTY,
                                              new String[] { edmProperty.getName() });
            }
            if (!isStreamProperty) {
                if (edmProperty.isCollection()) {
                    json.writeStartArray();
                    json.writeEndArray();
                } else {
                    json.writeNull();
                }
            }
        }
    }

    /**
     * This method writes the property type.
     *
     * @param edmProperty
     * @param json
     * @throws SerializerException
     * @throws IOException
     */
    private void writePropertyType(EdmProperty edmProperty, JsonGenerator json)
            throws SerializerException, IOException {
        if (this.isODataMetadataFull) {
            String typeName = edmProperty.getName() + "@odata.type";
            EdmType type = edmProperty.getType();
            if (type.getKind() != EdmTypeKind.ENUM && type.getKind() != EdmTypeKind.DEFINITION) {
                if (edmProperty.isPrimitive()) {
                    if (edmProperty.isCollection()) {
                        json.writeStringField(typeName, "#Collection(" + type.getFullQualifiedName().getName() + ")");
                    } else if (type != EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Boolean)
                            && type != EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Double)
                            && type != EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.String)) {
                        json.writeStringField(typeName, "#" + type.getFullQualifiedName().getName());
                    }
                } else {
                    if (type.getKind() != EdmTypeKind.COMPLEX) {
                        throw new SerializerException("Property type not yet supported!",
                                                      MessageKeys.UNSUPPORTED_PROPERTY_TYPE,
                                                      new String[] { edmProperty.getName() });
                    }
                    if (edmProperty.isCollection()) {
                        json.writeStringField(typeName, "#Collection(" + type.getFullQualifiedName()
                                .getFullQualifiedNameAsString() + ")");
                    }
                }
            } else if (edmProperty.isCollection()) {
                json.writeStringField(typeName,
                                      "#Collection(" + type.getFullQualifiedName().getFullQualifiedNameAsString()
                                              + ")");
            } else {
                json.writeStringField(typeName, "#" + type.getFullQualifiedName().getFullQualifiedNameAsString());
            }
        }
    }

    /**
     * This method writes the property value.
     *
     * @param metadata
     * @param edmProperty
     * @param property
     * @param selectedPaths
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    private void writePropertyValue(ServiceMetadata metadata, EdmProperty edmProperty, Property property,
                                    Set<List<String>> selectedPaths, JsonGenerator json)
            throws IOException, SerializerException {
        EdmType type = edmProperty.getType();
        try {
            if (!edmProperty.isPrimitive() && type.getKind() != EdmTypeKind.ENUM
                    && type.getKind() != EdmTypeKind.DEFINITION) {
                if (!property.isComplex()) {
                    throw new SerializerException("Property type not yet supported!",
                                                  MessageKeys.UNSUPPORTED_PROPERTY_TYPE,
                                                  new String[] { edmProperty.getName() });
                }
                if (edmProperty.isCollection()) {
                    this.writeComplexCollection(metadata, (EdmComplexType) type, property, selectedPaths, json);
                } else {
                    this.writeComplex(metadata, (EdmComplexType) type, property, selectedPaths, json);
                }
            } else if (edmProperty.isCollection()) {
                this.writePrimitiveCollection((EdmPrimitiveType) type, property, edmProperty.isNullable(),
                                              edmProperty.getMaxLength(), edmProperty.getPrecision(),
                                              edmProperty.getScale(), edmProperty.isUnicode(), json);
            } else {
                this.writePrimitive((EdmPrimitiveType) type, property, edmProperty.isNullable(),
                                    edmProperty.getMaxLength(), edmProperty.getPrecision(), edmProperty.getScale(),
                                    edmProperty.isUnicode(), json);
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
     * @param type
     * @param property
     * @param selectedPaths
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    private void writeComplex(ServiceMetadata metadata, EdmComplexType type, Property property,
                              Set<List<String>> selectedPaths, JsonGenerator json)
            throws IOException, SerializerException {
        json.writeStartObject();
        String derivedName = property.getType();
        EdmComplexType resolvedType = null;
        if (!type.getFullQualifiedName().getFullQualifiedNameAsString().equals(derivedName)) {
            if (type.getBaseType() != null && type.getBaseType().getFullQualifiedName().getFullQualifiedNameAsString()
                    .equals(derivedName)) {
                resolvedType = this.resolveComplexType(metadata, type.getBaseType(),
                                                       type.getFullQualifiedName().getFullQualifiedNameAsString());
            } else {
                resolvedType = this.resolveComplexType(metadata, type, derivedName);
            }
        } else {
            resolvedType = this.resolveComplexType(metadata, type, derivedName);
        }
        if (!this.isODataMetadataNone && !resolvedType.equals(type) || this.isODataMetadataFull) {
            json.writeStringField("@odata.type",
                                  "#" + resolvedType.getFullQualifiedName().getFullQualifiedNameAsString());
        }
        this.writeComplexValue(metadata, resolvedType, property.asComplex().getValue(), selectedPaths, json);
        json.writeEndObject();
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
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    private void writePrimitiveCollection(EdmPrimitiveType type, Property property, Boolean isNullable,
                                          Integer maxLength, Integer precision, Integer scale, Boolean isUnicode,
                                          JsonGenerator json) throws IOException, SerializerException {
        json.writeStartArray();
        Iterator iterator = property.asCollection().iterator();
        while (iterator.hasNext()) {
            Object value = iterator.next();
            switch (property.getValueType()) {
                case COLLECTION_PRIMITIVE:
                case COLLECTION_ENUM:
                case COLLECTION_GEOSPATIAL:
                    try {
                        this.writePrimitiveValue(property.getName(), type, value, isNullable, maxLength, precision, scale,
                                                 isUnicode, json);
                        break;
                    } catch (EdmPrimitiveTypeException e) {
                        throw new SerializerException("Wrong value for property!", e, MessageKeys.WRONG_PROPERTY_VALUE,
                                                      new String[] { property.getName(), property.getValue().toString() });
                    }
                default:
                    throw new SerializerException("Property type not yet supported!", MessageKeys.UNSUPPORTED_PROPERTY_TYPE,
                                                  new String[] { property.getName() });
            }
        }
        json.writeEndArray();
    }

    /**
     * This method writes a collection of complex properties.
     *
     * @param metadata
     * @param type
     * @param property
     * @param selectedPaths
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    private void writeComplexCollection(ServiceMetadata metadata, EdmComplexType type, Property property,
                                        Set<List<String>> selectedPaths, JsonGenerator json)
            throws IOException, SerializerException {
        json.writeStartArray();
        Iterator iterator = property.asCollection().iterator();
        while (iterator.hasNext()) {
            Object value = iterator.next();
            EdmComplexType derivedType =
                    ((ComplexValue) value).getTypeName() != null ? metadata.getEdm().getComplexType(
                            new FullQualifiedName(((ComplexValue) value).getTypeName())) : type;
            switch (property.getValueType()) {
                case COLLECTION_COMPLEX:
                    json.writeStartObject();
                    if (this.isODataMetadataFull || !this.isODataMetadataNone && !derivedType.equals(type)) {
                        json.writeStringField("@odata.type",
                                              "#" + derivedType.getFullQualifiedName().getFullQualifiedNameAsString());
                    }
                    this.writeComplexValue(metadata, derivedType, ((ComplexValue) value).getValue(), selectedPaths, json);
                    json.writeEndObject();
                    break;
                default:
                    throw new SerializerException("Property type not yet supported!", MessageKeys.UNSUPPORTED_PROPERTY_TYPE,
                                                  new String[] { property.getName() });
            }
        }
        json.writeEndArray();
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
     * @param json
     * @throws EdmPrimitiveTypeException
     * @throws IOException
     * @throws SerializerException
     */
    private void writePrimitive(EdmPrimitiveType type, Property property, Boolean isNullable, Integer maxLength,
                                Integer precision, Integer scale, Boolean isUnicode, JsonGenerator json)
            throws EdmPrimitiveTypeException, IOException, SerializerException {
        if (property.isPrimitive()) {
            this.writePrimitiveValue(property.getName(), type, property.asPrimitive(), isNullable, maxLength, precision,
                                     scale, isUnicode, json);
        } else if (property.isGeospatial()) {
            this.writeGeoValue(property.getName(), type, property.asGeospatial(), isNullable, json);
        } else {
            if (!property.isEnum()) {
                throw new SerializerException("Inconsistent property type!", MessageKeys.INCONSISTENT_PROPERTY_TYPE,
                                              new String[] { property.getName() });
            }
            this.writePrimitiveValue(property.getName(), type, property.asEnum(), isNullable, maxLength, precision,
                                     scale, isUnicode, json);
        }
    }

    /**
     * This method writes a primitive property value.
     *
     * @param name
     * @param type
     * @param primitiveValue
     * @param isNullable
     * @param maxLength
     * @param precision
     * @param scale
     * @param isUnicode
     * @param json
     * @throws EdmPrimitiveTypeException
     * @throws IOException
     */
    protected void writePrimitiveValue(String name, EdmPrimitiveType type, Object primitiveValue, Boolean isNullable,
                                       Integer maxLength, Integer precision, Integer scale, Boolean isUnicode,
                                       JsonGenerator json) throws EdmPrimitiveTypeException, IOException {
        String value = type.valueToString(primitiveValue, isNullable, maxLength, precision, scale, isUnicode);
        if (value == null) {
            json.writeNull();
        } else if (type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Boolean)) {
            json.writeBoolean(Boolean.parseBoolean(value));
        } else if (type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Byte)
                || type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Double)
                || type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Int16)
                || type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Int32)
                || type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.SByte)
                || type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Single)
                || (type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Decimal)
                || type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Int64))
                && !this.isIEEE754Compatible) {
            json.writeNumber(value);
        } else if (type == EdmPrimitiveTypeFactory.getInstance(EdmPrimitiveTypeKind.Stream)) {
            if (primitiveValue instanceof Link) {
                Link stream = (Link) primitiveValue;
                if (!this.isODataMetadataNone) {
                    if (stream.getMediaETag() != null) {
                        json.writeStringField(name + "@odata.mediaEtag", stream.getMediaETag());
                    }
                    if (stream.getType() != null) {
                        json.writeStringField(name + "@odata.mediaContentType", stream.getType());
                    }
                }
                if (this.isODataMetadataFull) {
                    if (stream.getRel() != null && stream.getRel().equals(
                            "http://docs.oasis-open.org/odata/ns/mediaresource/")) {
                        json.writeStringField(name + "@odata.mediaReadLink", stream.getHref());
                    }
                    if (stream.getRel() == null || stream.getRel().equals(
                            "http://docs.oasis-open.org/odata/ns/edit-media/")) {
                        json.writeStringField(name + "@odata.mediaEditLink", stream.getHref());
                    }
                }
            }
        } else {
            json.writeString(value);
        }
    }

    /**
     * This method writes a geospatial value.
     *
     * @param name
     * @param type
     * @param geoValue
     * @param isNullable
     * @param json
     * @throws EdmPrimitiveTypeException
     * @throws IOException
     * @throws SerializerException
     */
    protected void writeGeoValue(String name, EdmPrimitiveType type, Geospatial geoValue, Boolean isNullable,
                                 JsonGenerator json)
            throws EdmPrimitiveTypeException, IOException, SerializerException {
        if (geoValue == null) {
            if (isNullable != null && !isNullable) {
                throw new EdmPrimitiveTypeException("The literal 'null' is not allowed.");
            }
            json.writeNull();
        } else {
            if (!type.getDefaultType().isAssignableFrom(geoValue.getClass())) {
                throw new EdmPrimitiveTypeException("The value type " + geoValue.getClass() + " is not supported.");
            }
            if (geoValue.getSrid() != null && geoValue.getSrid().isNotDefault()) {
                throw new SerializerException("Non-standard SRID not supported!", MessageKeys.WRONG_PROPERTY_VALUE,
                                              new String[] { name, geoValue.toString() });
            }
            json.writeStartObject();
            json.writeStringField("type", (String) geoValueTypeToJsonName.get(geoValue.getGeoType()));
            json.writeFieldName(geoValue.getGeoType() == Type.GEOSPATIALCOLLECTION ? "geometries" : "coordinates");
            json.writeStartArray();
            Iterator iterator;
            label54:
            switch (geoValue.getGeoType()) {
                case POINT:
                    this.writeGeoPoint(json, (Point) geoValue);
                    break;
                case MULTIPOINT:
                    this.writeGeoPoints(json, (MultiPoint) geoValue);
                    break;
                case LINESTRING:
                    this.writeGeoPoints(json, (LineString) geoValue);
                    break;
                case MULTILINESTRING:
                    iterator = ((MultiLineString) geoValue).iterator();
                    while (true) {
                        if (!iterator.hasNext()) {
                            break label54;
                        }
                        LineString lineString = (LineString) iterator.next();
                        json.writeStartArray();
                        this.writeGeoPoints(json, lineString);
                        json.writeEndArray();
                    }
                case POLYGON:
                    this.writeGeoPolygon(json, (Polygon) geoValue);
                    break;
                case MULTIPOLYGON:
                    iterator = ((MultiPolygon) geoValue).iterator();
                    while (true) {
                        if (!iterator.hasNext()) {
                            break label54;
                        }
                        Polygon polygon = (Polygon) iterator.next();
                        json.writeStartArray();
                        this.writeGeoPolygon(json, polygon);
                        json.writeEndArray();
                    }
                case GEOSPATIALCOLLECTION:
                    iterator = ((GeospatialCollection) geoValue).iterator();

                    while (iterator.hasNext()) {
                        Geospatial element = (Geospatial) iterator.next();
                        this.writeGeoValue(name, EdmPrimitiveTypeFactory.getInstance(element.getEdmPrimitiveTypeKind()),
                                           element, isNullable, json);
                    }
            }
            json.writeEndArray();
            json.writeEndObject();
        }
    }

    /**
     * This method writes a single geo-point.
     *
     * @param json
     * @param point
     * @throws IOException
     */
    private void writeGeoPoint(JsonGenerator json, Point point) throws IOException {
        json.writeNumber(point.getX());
        json.writeNumber(point.getY());
        if (point.getZ() != 0.0) {
            json.writeNumber(point.getZ());
        }
    }

    /**
     * This method writes a collection of geo-points.
     *
     * @param json
     * @param points
     * @throws IOException
     */
    private void writeGeoPoints(JsonGenerator json, ComposedGeospatial<Point> points) throws IOException {
        Iterator iterator = points.iterator();
        while (iterator.hasNext()) {
            Point point = (Point) iterator.next();
            json.writeStartArray();
            this.writeGeoPoint(json, point);
            json.writeEndArray();
        }
    }

    /**
     * This method writes the geo polygon.
     *
     * @param json
     * @param polygon
     * @throws IOException
     */
    private void writeGeoPolygon(JsonGenerator json, Polygon polygon) throws IOException {
        json.writeStartArray();
        this.writeGeoPoints(json, polygon.getExterior());
        json.writeEndArray();
        if (!polygon.getInterior().isEmpty()) {
            json.writeStartArray();
            this.writeGeoPoints(json, polygon.getInterior());
            json.writeEndArray();
        }
    }

    /**
     * This method writes a complex property value.
     *
     * @param metadata
     * @param type
     * @param properties
     * @param selectedPaths
     * @param json
     * @throws IOException
     * @throws SerializerException
     */
    protected void writeComplexValue(ServiceMetadata metadata, EdmComplexType type, List<Property> properties,
                                     Set<List<String>> selectedPaths, JsonGenerator json)
            throws IOException, SerializerException {
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
                    ExpandSelectHelper.getReducedSelectedPaths(selectedPaths, propertyName), json);
        }
    }

    /**
     * This method finds properties of an entity.
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
     * This method writes the context URL.
     *
     * @param contextURL
     * @param json
     * @throws IOException
     */
    void writeContextURL(ContextURL contextURL, JsonGenerator json) throws IOException {
        if (!this.isODataMetadataNone && contextURL != null) {
            json.writeStringField("@odata.context", ContextURLBuilder.create(contextURL).toASCIIString());
        }
    }

    /**
     * This method writes the ETag.
     *
     * @param metadata
     * @param json
     * @throws IOException
     */
    void writeMetadataETag(ServiceMetadata metadata, JsonGenerator json) throws IOException {
        if (!this.isODataMetadataNone && metadata != null && metadata.getServiceMetadataETagSupport() != null
                && metadata.getServiceMetadataETagSupport().getMetadataETag() != null) {
            json.writeStringField("@odata.metadataEtag", metadata.getServiceMetadataETagSupport().getMetadataETag());
        }
    }

    /**
     * This method writes the entity count if count option is enabled.
     *
     * @param propertyName
     * @param count
     * @param json
     * @throws IOException
     */
    void writeInlineCount(String propertyName, Integer count, JsonGenerator json) throws IOException {
        if (count != null) {
            if (this.isIEEE754Compatible) {
                json.writeStringField(propertyName + "@odata.count", String.valueOf(count));
            } else {
                json.writeNumberField(propertyName + "@odata.count", count);
            }
        }
    }

    /**
     * This method writes the link to the next page if pagination is enabled.
     *
     * @param entitySet
     * @param json
     * @param pagination
     * @throws IOException
     */
    void writeNextLink(AbstractEntityCollection entitySet, JsonGenerator json, boolean pagination) throws IOException {
        if (entitySet.getNext() != null) {
            pagination = true;
            json.writeStringField("@odata.nextLink", entitySet.getNext().toASCIIString());
        } else {
            pagination = false;
        }
    }
}
