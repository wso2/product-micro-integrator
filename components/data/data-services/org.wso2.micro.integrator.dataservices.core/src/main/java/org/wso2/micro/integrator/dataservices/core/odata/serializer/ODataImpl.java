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

import java.util.List;

import org.apache.olingo.commons.api.edm.provider.CsdlEdmProvider;
import org.apache.olingo.commons.api.edmx.EdmxReference;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.etag.ServiceMetadataETagSupport;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerException.MessageKeys;
import org.apache.olingo.server.api.uri.UriHelper;
import org.apache.olingo.server.core.ServiceMetadataImpl;
import org.apache.olingo.server.core.uri.UriHelperImpl;

public class ODataImpl extends OData {
    public ODataImpl() {
    }

    public ODataSerializer createSerializer(ContentType contentType) throws SerializerException {
        ODataSerializer serializer = null;
        if (contentType.isCompatible(ContentType.APPLICATION_JSON)) {
            String metadata = contentType.getParameter("odata.metadata");
            if (metadata == null || "minimal".equalsIgnoreCase(metadata) || "none".equalsIgnoreCase(metadata)
                    || "full".equalsIgnoreCase(metadata)) {
                serializer = new ODataJsonSerializer(contentType);
            }
        } else if (contentType.isCompatible(ContentType.APPLICATION_XML) || contentType.isCompatible(
                ContentType.APPLICATION_ATOM_XML)) {
            serializer = new ODataXmlSerializer();
        }
        if (serializer == null) {
            throw new SerializerException("Unsupported format: " + contentType.toContentTypeString(),
                                          MessageKeys.UNSUPPORTED_FORMAT,
                                          new String[] { contentType.toContentTypeString() });
        } else {
            return (ODataSerializer) serializer;
        }
    }

    public ServiceMetadata createServiceMetadata(CsdlEdmProvider edmProvider, List<EdmxReference> references) {
        return this.createServiceMetadata(edmProvider, references, (ServiceMetadataETagSupport) null);
    }

    public ServiceMetadata createServiceMetadata(CsdlEdmProvider edmProvider, List<EdmxReference> references,
                                                 ServiceMetadataETagSupport serviceMetadataETagSupport) {
        return new ServiceMetadataImpl(edmProvider, references, serviceMetadataETagSupport);
    }

    public UriHelper createUriHelper() {
        return new UriHelperImpl();
    }

}
