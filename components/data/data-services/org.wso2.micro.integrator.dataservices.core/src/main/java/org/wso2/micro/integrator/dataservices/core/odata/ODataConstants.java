/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.dataservices.core.odata;

import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.server.api.OData;import org.wso2.micro.integrator.dataservices.core.odata.expression.primitive.EdmNull;

import java.math.BigDecimal;
import java.math.BigInteger;

public class ODataConstants {

    public static final OData ODATA = OData.newInstance();

    public static final EdmPrimitiveType primitiveBinary =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Binary);
    public static final EdmPrimitiveType primitiveBoolean =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Boolean);
    public static final EdmPrimitiveType primitiveByte = ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Byte);
    public static final EdmPrimitiveType primitiveSByte = ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.SByte);
    public static final EdmPrimitiveType primitiveDate = ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Date);
    public static final EdmPrimitiveType primitiveDateTimeOffset =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.DateTimeOffset);
    public static final EdmPrimitiveType primitiveTimeOfDay =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.TimeOfDay);
    public static final EdmPrimitiveType primitiveDuration =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Duration);
    public static final EdmPrimitiveType primitiveDecimal =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Decimal);
    public static final EdmPrimitiveType primitiveSingle =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Single);
    public static final EdmPrimitiveType primitiveDouble =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Double);
    public static final EdmPrimitiveType primitiveGuid = ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Guid);
    public static final EdmPrimitiveType primitiveInt16 = ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Int16);
    public static final EdmPrimitiveType primitiveInt32 = ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Int32);
    public static final EdmPrimitiveType primitiveInt64 = ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Int64);
    public static final EdmPrimitiveType primitiveString =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.String);
    public static final EdmPrimitiveType primitiveNull = EdmNull.getInstance();

    public static final int FACTOR_SECOND_INT = 1000;
    public static final BigDecimal FACTOR_SECOND = new BigDecimal(1000);
    public static final BigInteger EDM_SBYTE_MIN = BigInteger.valueOf(Byte.MIN_VALUE);
    public static final BigInteger EDN_SBYTE_MAX = BigInteger.valueOf(Byte.MAX_VALUE);
    public static final BigInteger EDM_BYTE_MIN = BigInteger.ZERO;
    public static final BigInteger EDM_BYTE_MAX = BigInteger.valueOf(((Byte.MAX_VALUE * 2) + 1));
    public static final BigInteger EDM_INT16_MIN = BigInteger.valueOf(Short.MIN_VALUE);
    public static final BigInteger EDM_INT16_MAX = BigInteger.valueOf(Short.MAX_VALUE);
    public static final BigInteger EDM_INT32_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    public static final BigInteger EDM_INT32_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    public static final BigInteger EDM_INT64_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    public static final BigInteger EDM_INT64_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    public static final BigDecimal EDM_SINGLE_MIN = BigDecimal.valueOf(Float.MIN_VALUE);
    public static final BigDecimal EDM_SINGLE_MAX = BigDecimal.valueOf(Float.MAX_VALUE);

    public static final int EQUALS = 0;
    public static final int LESS_THAN = -1;
    public static final int GREATER_THAN = 1;
    public static final String E_TAG = "E_TAG";
    public static final String ODATA_COUNT_WITHOUT_PAGING = "odataCountWithoutPaging";
}
